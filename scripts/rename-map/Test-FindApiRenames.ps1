<#
.SYNOPSIS
  Self-contained tests for FindApiRenames.java / Find-ApiRenames.ps1 (no Pester). Compiles two tiny fixture APIs ("old"
  and "new") with the JDK in JAVA_HOME (24+), jars them, runs the tool and checks classes.tsv, members.tsv, warnings.txt
  and map.txt. Prints PASS/FAIL, exits nonzero on any failure.

.EXAMPLE
  pwsh -NoProfile -File Test-FindApiRenames.ps1
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$tool = Join-Path $PSScriptRoot 'Find-ApiRenames.ps1'
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("api-renames-test-" + [guid]::NewGuid().ToString('N'))
$script:failures = 0
function Assert-Case([string] $name, [bool] $ok, [string] $detail = '') {
  if ($ok) { Write-Host "PASS  $name" }
  else { $script:failures++; Write-Host "FAIL  $name $(if ($detail) { "-- $detail" })" }
}

# Writes {relative path = source} into $dir, compiles it with javac and zips the classes into $jar.
function New-FixtureJar([string] $dir, [hashtable] $sources, [string] $jar) {
  $files = foreach ($k in $sources.Keys) {
    $p = Join-Path $dir "src/$k"
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    Set-Content -LiteralPath $p -Value $sources[$k]
    $p
  }
  $classes = Join-Path $dir 'classes'
  New-Item -ItemType Directory -Force $classes | Out-Null
  & (Join-Path $env:JAVA_HOME 'bin/javac') -d $classes @files
  if ($LASTEXITCODE -ne 0) { throw "javac failed for $dir" }
  [System.IO.Compression.ZipFile]::CreateFromDirectory($classes, $jar)
}

# Shared fixture APIs (also used by Test-CompilerRenames.ps1 via dot-sourcing the hashtables).
. (Join-Path $PSScriptRoot 'Test-ApiFixtures.ps1')

try {
  if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to a JDK 24+ (javac + ClassFile API)' }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  New-Item -ItemType Directory -Force $tmp | Out-Null
  $oldJar = Join-Path $tmp 'old.jar'; $newJar = Join-Path $tmp 'new.jar'
  New-FixtureJar (Join-Path $tmp 'old') $OldApi $oldJar
  New-FixtureJar (Join-Path $tmp 'new') $NewApi $newJar

  $ov = Join-Path $tmp 'overrides.txt'
  Set-Content -LiteralPath $ov -Value @(
    '# test overrides'
    'class net.minecraft.fix.a.Gone net.minecraft.fix.b.Replacement'
    'hint net.minecraft.fix.Screen renderBg merge into extractBackground'
    'rule literal Foo.INSTANCE Bar.EMPTY'
    'method net.minecraft.fix.Types cutoutNoCull (Ljava/lang/String;)Ljava/lang/String; cutout'
    'method net.minecraft.fix.Types cutout (Ljava/lang/String;)Ljava/lang/String; cutoutCull'
    'method net.minecraft.fix.Types decal (Ljava/lang/String;)Ljava/lang/String; -'
    'moved net.minecraft.fix.Minecraft screen gui.screen()'
    'moved net.minecraft.fix.Minecraft setScreen gui.setScreen'
    'moved net.minecraft.fix.Minecraft tick gui.tick'
  )
  $out = Join-Path $tmp 'out'
  $mapOut = Join-Path $tmp 'map.txt'; $memOut = Join-Path $tmp 'members.tsv'
  $r = & pwsh -NoProfile -File $tool -OldJar $oldJar -NewJar $newJar -Out $out -Overrides $ov -MapOut $mapOut -MembersOut $memOut -Title 'old -> new' 2>&1 | Out-String
  Assert-Case 'tool exits 0' ($LASTEXITCODE -eq 0) $r
  $classes = Get-Content (Join-Path $out 'classes.tsv') -Raw
  $members = Get-Content $memOut -Raw
  $warnings = Get-Content (Join-Path $out 'warnings.txt') -Raw
  $map = (Get-Content $mapOut -Raw) -replace "`r", ""

  Assert-Case 'moved class (same simple name)' ($classes -match "net/minecraft/fix/a/Thing`tnet/minecraft/fix/b/Thing`tmoved") $classes
  Assert-Case 'renamed outer class by inner-class votes' ($classes -match "net/minecraft/fix/Graphics`tnet/minecraft/fix/GraphicsExtractor`tinner-classes") $classes
  Assert-Case 'nested class follows its renamed outer' ($classes -match "net/minecraft/fix/Graphics\`$Pose`tnet/minecraft/fix/GraphicsExtractor\`$Pose`touter-renamed") $classes
  Assert-Case 'cross-package rename by member similarity' ($classes -match "net/minecraft/fix/model/BlockElement`tnet/minecraft/fix/cuboid/CuboidModelElement`tsimilarity-xpkg") $classes
  Assert-Case 'enum rename (field names + self references)' ($classes -match "net/minecraft/fix/inv/ClickType`tnet/minecraft/fix/inv/ContainerInput`tsimilarity") $classes
  Assert-Case 'same simple name, different shape: old Material -> SpriteId, not the new Material' ($classes -match "net/minecraft/fix/model/Material`tnet/minecraft/fix/model/sprite/SpriteId`t(similarity-xpkg|shape-over-name)") $classes
  Assert-Case 'shape warning names both classes' ($warnings -match 'same simple name, different shape: net\.minecraft\.fix\.model\.Material -> net\.minecraft\.fix\.model\.sprite\.SpriteId .*NOT the same-name net\.minecraft\.fix\.model\.sprite\.Material') $warnings
  Assert-Case 'same FQN reused by another class: old shape follows its successor' ($classes -match "net/minecraft/fix/BlockModel`tnet/minecraft/fix/cuboid/CuboidModel`tswap") $classes
  Assert-Case 'swap warning' ($warnings -match 'same FQN, different class: net\.minecraft\.fix\.BlockModel -> net\.minecraft\.fix\.cuboid\.CuboidModel') $warnings
  Assert-Case 'override class pair' ($classes -match "net/minecraft/fix/a/Gone`tnet/minecraft/fix/b/Replacement`toverride") $classes

  Assert-Case 'unique same-descriptor method rename' ($members -match "method`tnet/minecraft/fix/Graphics`tnet/minecraft/fix/GraphicsExtractor`tdrawString`ttext`t\(Ljava/lang/String;I\)V`t\(Ljava/lang/String;I\)V`t0`tdesc-unique") $members
  Assert-Case 'descriptor remapped through the class map' ($members -match "method`tnet/minecraft/fix/Renderable`tnet/minecraft/fix/Renderable`trender`textractRenderState`t\(Lnet/minecraft/fix/Graphics;IIF\)V`t\(Lnet/minecraft/fix/GraphicsExtractor;IIF\)V") $members
  Assert-Case 'same-descriptor collision resolved by name (render)' ($members -match "method`tnet/minecraft/fix/Screen`tnet/minecraft/fix/Screen`trender`textractRenderState`t.*name-tiebreak") $members
  Assert-Case 'same-descriptor collision resolved by name (renderBackground)' ($members -match "method`tnet/minecraft/fix/Screen`tnet/minecraft/fix/Screen`trenderBackground`textractBackground`t.*name-tiebreak") $members
  Assert-Case 'static field rename' ($members -match "field`tnet/minecraft/fix/Graphics`tnet/minecraft/fix/GraphicsExtractor`tOLD_CONST`tNEW_CONST`tI`tI`t1") $members
  Assert-Case 'hint row from the overrides file' ($members -match "hint`tnet/minecraft/fix/Screen`tnet/minecraft/fix/Screen`trenderBg`t-`t-`t-`t-`tmerge into extractBackground") $members
  Assert-Case 'removed method without candidate is not a rename' ($members -notmatch "`trenderBg`t[a-z]") $members
  Assert-Case 'forced rename of a gone member' ($members -match "method`tnet/minecraft/fix/Types`tnet/minecraft/fix/Types`tcutoutNoCull`tcutout`t.*`toverride`r?`n") $members
  Assert-Case 'forced same-name swap of a member that still exists' ($members -match "method`tnet/minecraft/fix/Types`tnet/minecraft/fix/Types`tcutout`tcutoutCull`t.*`toverride-swap") $members
  Assert-Case 'forced removal (-): no row, never paired with the free name' ($members -notmatch "`tdecal`t") $members
  $removed = Get-Content (Join-Path $out 'removed.tsv') -Raw
  Assert-Case 'forced removal listed in removed.tsv' ($removed -match "method`tnet/minecraft/fix/Types`tdecal\(Ljava/lang/String;\)Ljava/lang/String;`tforced") $removed
  # cutoutAlt (gone, not forced) has the descriptor of the forced swap target cutoutCull: the reservation keeps the two apart
  Assert-Case 'forced target reserved: a gone member with its descriptor is not paired with it' ($members -notmatch "`tcutoutAlt`t") $members
  Assert-Case 'gone member left without candidates after the reservation is removed' ($removed -match "(?m)^method`tnet/minecraft/fix/Types`tcutoutAlt\(Ljava/lang/String;\)Ljava/lang/String;\r?$") $removed

  Assert-Case 'map: class rules use $ for nested and dotted packages' ($map -match '(?m)^class net\.minecraft\.fix\.Graphics net\.minecraft\.fix\.GraphicsExtractor$') $map
  Assert-Case 'map: implied nested rule omitted' ($map -notmatch 'Graphics\$Pose') $map
  Assert-Case 'map: Material -> SpriteId rule' ($map -match '(?m)^class net\.minecraft\.fix\.model\.Material net\.minecraft\.fix\.model\.sprite\.SpriteId$') $map
  Assert-Case 'map: override rule section + extra rule copied' ($map -match '# --- manual overrides' -and $map -match '(?m)^literal Foo\.INSTANCE Bar\.EMPTY$') $map
  Assert-Case 'summary line' ($r -match 'classPairs=\d+ .*methodRenames=\d+ fieldRenames=\d+ fieldMoves=2 \(weak=1\) collectionRules=40 hints=1 moved=3 ') $r

  # --- M8: moved static fields, collections, moved members, same-name changes ---
  Assert-Case 'moved static field rule (same name + descriptor, new in one other class)' ($map -match '(?m)^field net\.minecraft\.fix\.EntityType\.PIG net\.minecraft\.fix\.EntityTypes\.PIG$' -and $map -match '(?m)^field net\.minecraft\.fix\.EntityType\.COW net\.minecraft\.fix\.EntityTypes\.COW$') $map
  Assert-Case 'field pulled up into a supertype: no rule (inherited access still compiles)' ($map -notmatch 'MAX_SIZE') $map
  Assert-Case 'gone static field without a candidate: no rule, listed as removed' ($map -notmatch 'UNRELATED_COUNT' -and $removed -match "(?m)^field`tnet/minecraft/fix/EntityType`tUNRELATED_COUNT") $removed
  Assert-Case 'moved field is not also a member rename' ($members -notmatch "`tPIG`t") $members
  Assert-Case 'color collection rule (LIGHT_GRAY_ before GRAY_)' ($map -match '(?m)^field net\.minecraft\.fix\.Items\.RED_DYE net\.minecraft\.fix\.Items\.DYE\.pick\(net\.minecraft\.fix\.DyeColor\.RED\)$' -and $map -match '(?m)^field net\.minecraft\.fix\.Items\.LIGHT_GRAY_DYE net\.minecraft\.fix\.Items\.DYE\.pick\(net\.minecraft\.fix\.DyeColor\.LIGHT_GRAY\)$') $map
  Assert-Case 'copper collection rules (accessors; COPPER_BLOCK keeps its name)' ($map -match '(?m)^field net\.minecraft\.fix\.Items\.COPPER_BLOCK net\.minecraft\.fix\.Items\.COPPER_BLOCK\.weathering\(\)\.unaffected\(\)$' -and $map -match '(?m)^field net\.minecraft\.fix\.Items\.EXPOSED_COPPER net\.minecraft\.fix\.Items\.COPPER_BLOCK\.weathering\(\)\.exposed\(\)$' -and $map -match '(?m)^field net\.minecraft\.fix\.Items\.WAXED_OXIDIZED_COPPER net\.minecraft\.fix\.Items\.COPPER_BLOCK\.waxed\(\)\.oxidized\(\)$') $map
  Assert-Case 'DYED_ color collection: <COLOR>_TERRACOTTA -> DYED_TERRACOTTA.pick' ($map -match '(?m)^field net\.minecraft\.fix\.Items\.RED_TERRACOTTA net\.minecraft\.fix\.Items\.DYED_TERRACOTTA\.pick\(net\.minecraft\.fix\.DyeColor\.RED\)$' -and $map -match '(?m)^field net\.minecraft\.fix\.Items\.LIGHT_GRAY_TERRACOTTA net\.minecraft\.fix\.Items\.DYED_TERRACOTTA\.pick\(net\.minecraft\.fix\.DyeColor\.LIGHT_GRAY\)$') $map
  Assert-Case 'incomplete copper family (old field was a record): no rule, warning names the old type' ($map -notmatch 'COPPER_BARS' -and $warnings -match 'collection without a complete old copper family \(1/8\): net\.minecraft\.fix\.Items\.COPPER_BARS \(the old field of that name was a CopperItems: its accessor calls break, fix by hand\)') "$warnings $map"
  Assert-Case 'weak moved field (single field, unrelated class): written commented out' ($map -match '(?m)^# weak \(single field, unrelated class; uncomment after review\): field net\.minecraft\.fix\.Pond\.CODEC net\.minecraft\.fix\.Serializers\.CODEC$' -and $map -notmatch '(?m)^field net\.minecraft\.fix\.Pond\.CODEC') $map
  Assert-Case 'field rules run before the class rules' ($map.IndexOf('field net.minecraft.fix.EntityType.PIG') -lt $map.IndexOf('class net.minecraft.fix.Graphics ') -and $map.IndexOf('field net.minecraft.fix.Items.RED_DYE') -lt $map.IndexOf('class net.minecraft.fix.Graphics ')) $map
  Assert-Case 'moved rows copied into the member table' ($members -match "(?m)^moved`tnet/minecraft/fix/Minecraft`tnet/minecraft/fix/Minecraft`tscreen`tgui\.screen\(\)`t" -and $members -match "(?m)^moved`tnet/minecraft/fix/Minecraft`tnet/minecraft/fix/Minecraft`tsetScreen`tgui\.setScreen`t") $members
  Assert-Case 'moved member that still exists: warning' ($warnings -match 'moved member net\.minecraft\.fix\.Minecraft#tick still exists') $warnings
  $changed = Get-Content (Join-Path $out 'changed.tsv') -Raw
  Assert-Case 'changed.tsv: same-name field with a new type' ($changed -match "field`tnet/minecraft/fix/Items`tnet/minecraft/fix/Items`tCOPPER_BLOCK`tLnet/minecraft/fix/Item;`tLnet/minecraft/fix/WeatheringCopperCollection;") $changed

  # the generated map is valid input for Invoke-RenameMap.ps1
  $root = Join-Path $tmp 'repo'
  New-Item -ItemType Directory -Force (Join-Path $root 'src') | Out-Null
  Set-Content -LiteralPath (Join-Path $root 'src/U.java') -Value @('import net.minecraft.fix.model.Material;', 'class U { Material m; }')
  Set-Content -LiteralPath (Join-Path $root 'src/F.java') -Value @(
    'package f;', 'import net.minecraft.fix.EntityType;', 'import net.minecraft.fix.Items;', 'import net.minecraft.fix.Pond;',
    'class F { Object a = EntityType.PIG, b = Items.RED_DYE, c = Items.COPPER_BLOCK, d = Items.WAXED_EXPOSED_COPPER, e = Items.STICK, f = Items.RED_TERRACOTTA, g = Pond.CODEC; }')
  $rm = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-RenameMap.ps1') -Root $root -Map $mapOut -Apply 2>&1 | Out-String
  $u = Get-Content (Join-Path $root 'src/U.java') -Raw
  Assert-Case 'generated map applies (Material user becomes SpriteId)' ($LASTEXITCODE -eq 0 -and $u -match 'import net\.minecraft\.fix\.model\.sprite\.SpriteId;' -and $u -match 'SpriteId m;') "$rm $u"
  $fj = Get-Content (Join-Path $root 'src/F.java') -Raw
  Assert-Case 'generated field rules apply (imports added)' ($fj -match 'a = EntityTypes\.PIG, b = Items\.DYE\.pick\(DyeColor\.RED\), c = Items\.COPPER_BLOCK\.weathering\(\)\.unaffected\(\), d = Items\.COPPER_BLOCK\.waxed\(\)\.exposed\(\), e = Items\.STICK, f = Items\.DYED_TERRACOTTA\.pick\(DyeColor\.RED\), g = Pond\.CODEC;' -and $fj -match 'import net\.minecraft\.fix\.EntityTypes;' -and $fj -match 'import net\.minecraft\.fix\.DyeColor;') "$rm $fj"

  $rm2 = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-RenameMap.ps1') -Root $root -Map $mapOut -Apply 2>&1 | Out-String
  Assert-Case 'generated map: second apply is a no-op (COPPER_BLOCK.waxed().exposed() not rewritten again)' ($LASTEXITCODE -eq 0 -and $rm2 -match 'written=0' -and $rm2 -match 'hits=0' -and (Get-Content (Join-Path $root 'src/F.java') -Raw) -eq $fj) $rm2

  # bad overrides
  Set-Content -LiteralPath $ov -Value 'class net.minecraft.fix.Nope net.minecraft.fix.Graphics'
  $r = & pwsh -NoProfile -File $tool -OldJar $oldJar -NewJar $newJar -Out $out -Overrides $ov 2>&1 | Out-String
  Assert-Case 'override naming a missing class exits 2' ($LASTEXITCODE -eq 2 -and $r -match 'old class not in old jar') $r
  Set-Content -LiteralPath $ov -Value @('moved net.minecraft.fix.Nope screen gui.screen()', 'moved net.minecraft.fix.Minecraft screen')
  $r = & pwsh -NoProfile -File $tool -OldJar $oldJar -NewJar $newJar -Out $out -Overrides $ov 2>&1 | Out-String
  Assert-Case 'bad moved lines exit 2 (owner not in the new jar, missing replacement)' ($LASTEXITCODE -eq 2 -and $r -match 'line 1: moved owner not in new jar' -and $r -match 'line 2: moved <owner> <member> <replacement>') $r
  $r = & pwsh -NoProfile -File $tool -OldJar (Join-Path $tmp 'nope.jar') -NewJar $newJar -Out $out 2>&1 | Out-String
  Assert-Case 'missing jar exits 1' ($LASTEXITCODE -eq 1) $r
} finally {
  Remove-Item -Recurse -Force -LiteralPath $tmp -ErrorAction SilentlyContinue
}
if ($script:failures -gt 0) { Write-Host "$($script:failures) failure(s)"; exit 1 }
Write-Host 'all passed'
