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
  Assert-Case 'summary line' ($r -match 'classPairs=\d+ .*methodRenames=\d+ fieldRenames=\d+ hints=1') $r

  # the generated map is valid input for Invoke-RenameMap.ps1
  $root = Join-Path $tmp 'repo'
  New-Item -ItemType Directory -Force (Join-Path $root 'src') | Out-Null
  Set-Content -LiteralPath (Join-Path $root 'src/U.java') -Value @('import net.minecraft.fix.model.Material;', 'class U { Material m; }')
  $rm = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-RenameMap.ps1') -Root $root -Map $mapOut -Apply 2>&1 | Out-String
  $u = Get-Content (Join-Path $root 'src/U.java') -Raw
  Assert-Case 'generated map applies (Material user becomes SpriteId)' ($LASTEXITCODE -eq 0 -and $u -match 'import net\.minecraft\.fix\.model\.sprite\.SpriteId;' -and $u -match 'SpriteId m;') "$rm $u"

  # bad overrides
  Set-Content -LiteralPath $ov -Value 'class net.minecraft.fix.Nope net.minecraft.fix.Graphics'
  $r = & pwsh -NoProfile -File $tool -OldJar $oldJar -NewJar $newJar -Out $out -Overrides $ov 2>&1 | Out-String
  Assert-Case 'override naming a missing class exits 2' ($LASTEXITCODE -eq 2 -and $r -match 'old class not in old jar') $r
  $r = & pwsh -NoProfile -File $tool -OldJar (Join-Path $tmp 'nope.jar') -NewJar $newJar -Out $out 2>&1 | Out-String
  Assert-Case 'missing jar exits 1' ($LASTEXITCODE -eq 1) $r
} finally {
  Remove-Item -Recurse -Force -LiteralPath $tmp -ErrorAction SilentlyContinue
}
if ($script:failures -gt 0) { Write-Host "$($script:failures) failure(s)"; exit 1 }
Write-Host 'all passed'
