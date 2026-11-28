<#
.SYNOPSIS
  Self-contained tests for Invoke-RenameMap.ps1 (no Pester). Builds fixtures in a temp dir, runs the tool
  in a child pwsh, prints PASS/FAIL per case and exits nonzero on any failure.

.EXAMPLE
  pwsh -NoProfile -File Test-RenameMap.ps1
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$tool = Join-Path $PSScriptRoot 'Invoke-RenameMap.ps1'
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("rename-map-test-" + [guid]::NewGuid().ToString('N'))
$script:failures = 0

function Assert-Case([string] $name, [bool] $ok, [string] $detail = '') {
  if ($ok) { Write-Host "PASS  $name" }
  else { $script:failures++; Write-Host "FAIL  $name $(if ($detail) { "-- $detail" })" }
}

function Invoke-Tool([string] $root, [string] $map, [switch] $Apply) {
  $toolArgs = @('-NoProfile', '-File', $tool, '-Root', $root, '-Map', $map)
  if ($Apply) { $toolArgs += '-Apply' }
  $out = & pwsh @toolArgs 2>&1 | Out-String
  [pscustomobject]@{ Exit = $LASTEXITCODE; Out = $out }
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
function New-Fixture([string] $root, [string] $rel, [string[]] $lines, [string] $eol, [bool] $bom) {
  $path = Join-Path $root $rel
  New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
  $bytes = $utf8.GetBytes(($lines -join $eol) + $eol)
  if ($bom) { $bytes = [byte[]](0xEF, 0xBB, 0xBF) + $bytes }
  [System.IO.File]::WriteAllBytes($path, $bytes)
  $path
}
function Get-Text([string] $path) { $utf8.GetString([System.IO.File]::ReadAllBytes($path)).TrimStart([char]0xFEFF) }
function Get-Sha([string] $path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }

try {
  $root = Join-Path $tmp 'repo'
  $java = @(
    'package demo;'
    'import net.minecraft.resources.ResourceLocation;'
    'import slimeknights.mantle.data.loadable.primitive.ResourceLocationLoadable;'
    '/** Uses {@link ResourceLocation}. */'
    'class Demo {'
    '  ResourceLocation id = ResourceLocation.fromNamespaceAndPath("a", "b");'
    '  ResourceLocationLoadable loadable; MyResourceLocation mine; Object c = Loadables.RESOURCE_LOCATION;'
    '  void io(FriendlyByteBuf buf) { buf.writeResourceLocation(buf.readResourceLocation()); }'
    '  String s = "ResourceLocation in a string";'
    '}'
  )
  $crlf = New-Fixture $root 'src/main/java/demo/Crlf.java' $java "`r`n" $false
  $lf = New-Fixture $root 'src/main/java/demo/Lf.java' $java "`n" $false
  $bom = New-Fixture $root 'src/test/java/demo/Bom.java' $java "`r`n" $true
  $untouched = New-Fixture $root 'src/main/java/demo/Other.java' @('class Other { int x; }') "`n" $false
  $notJava = New-Fixture $root 'src/main/resources/data.json' @('{"id": "ResourceLocation"}') "`n" $false

  $map = Join-Path $tmp 'map.txt'
  [System.IO.File]::WriteAllText($map, @(
      '# test map'
      ''
      'literal net.minecraft.resources.ResourceLocation net.minecraft.resources.Identifier'
      'literal readResourceLocation( readIdentifier('
      '  literal   writeResourceLocation(   writeIdentifier(  '
      'token ResourceLocation Identifier'
    ) -join "`n")

  $before = @{}
  foreach ($p in $crlf, $lf, $bom, $untouched, $notJava) { $before[$p] = Get-Sha $p }

  # --- dry run ---
  $dry = Invoke-Tool $root $map
  Assert-Case 'dry run exits 0' ($dry.Exit -eq 0) "exit=$($dry.Exit)"
  $unchanged = @($crlf, $lf, $bom, $untouched, $notJava | Where-Object { (Get-Sha $_) -ne $before[$_] }).Count -eq 0
  Assert-Case 'dry run changes nothing on disk' $unchanged
  # per file: import literal 1 + read 1 + write 1 + token (javadoc 1, field 2) = 6 (string hit included)
  #           + 1 string hit = 7 hits per file; 3 files
  Assert-Case 'dry run summary line' ($dry.Out -match 'rules=4 files=3 hits=21 stringHits=3') ($dry.Out -split "`n" | Select-String 'rules=' | Out-String)
  Assert-Case 'dry run flags string-literal hits' ($dry.Out -match 'WARNING: 3 hit\(s\) inside a string literal' -and $dry.Out -match 'Lf\.java:9') ''
  Assert-Case 'dry run lists per-rule hits' ($dry.Out -match 'Hits per rule' -and $dry.Out -match 'Hits per file \(top 20 of 3 files\)')
  Assert-Case 'includes default to src/**/*.java (json ignored)' ($dry.Out -match '\(4 files scanned\)' -and $dry.Out -notmatch 'data\.json') ''

  # --- apply ---
  $app = Invoke-Tool $root $map -Apply
  Assert-Case 'apply exits 0' ($app.Exit -eq 0) "exit=$($app.Exit) $($app.Out)"
  Assert-Case 'apply writes only changed files' ($app.Out -match 'written=3' -and (Get-Sha $untouched) -eq $before[$untouched] -and (Get-Sha $notJava) -eq $before[$notJava])

  $text = Get-Text $lf
  Assert-Case 'token renames ResourceLocation' ($text -match 'Identifier id = Identifier\.fromNamespaceAndPath' -and $text -match '\{@link Identifier\}')
  Assert-Case 'token keeps ResourceLocationLoadable' ($text -match 'ResourceLocationLoadable loadable' -and $text -match 'primitive\.ResourceLocationLoadable;')
  Assert-Case 'token keeps MyResourceLocation' ($text -match 'MyResourceLocation mine')
  Assert-Case 'token keeps RESOURCE_LOCATION' ($text -match 'Loadables\.RESOURCE_LOCATION')
  Assert-Case 'literal rules (read/write calls)' ($text -match 'buf\.writeIdentifier\(buf\.readIdentifier\(\)\)')
  Assert-Case 'import literal + token rename the import, no ResourceLocation token left' ($text -match 'import net\.minecraft\.resources\.Identifier;' -and $text -notmatch '(?<![\w$])ResourceLocation(?![\w$])')
  Assert-Case 'string-literal hit still replaced' ($text -match '"Identifier in a string"')

  $crlfBytes = [System.IO.File]::ReadAllBytes($crlf); $crlfText = $utf8.GetString($crlfBytes)
  Assert-Case 'apply keeps CRLF as CRLF' (($crlfText -split "`r`n").Count -eq 11 -and ($crlfText -replace "`r`n", '') -notmatch "`n")
  $lfText = $utf8.GetString([System.IO.File]::ReadAllBytes($lf))
  Assert-Case 'apply keeps LF as LF' ($lfText -notmatch "`r" -and ($lfText -split "`n").Count -eq 11)
  $bomBytes = [System.IO.File]::ReadAllBytes($bom)
  Assert-Case 'apply keeps BOM when present' ($bomBytes[0] -eq 0xEF -and $bomBytes[1] -eq 0xBB -and $bomBytes[2] -eq 0xBF -and $bomBytes[3] -ne 0xEF)
  Assert-Case 'apply adds no BOM when absent' ($crlfBytes[0] -eq [byte][char]'p')

  $again = Invoke-Tool $root $map -Apply
  Assert-Case 'second apply is a no-op' ($again.Exit -eq 0 -and $again.Out -match 'written=0' -and $again.Out -match 'hits=0')

  # --- token rule escapes its input ($ and . are literal) ---
  $root2 = Join-Path $tmp 'repo2'
  $f2 = New-Fixture $root2 'src/A.java' @('a.b x; aXb y; a.bc z; Foo$Bar q;') "`n" $false
  $map2 = Join-Path $tmp 'map2.txt'
  [System.IO.File]::WriteAllText($map2, "token a.b NEW`ntoken Foo`$Bar Baz`$Qux`n")
  $r2 = Invoke-Tool $root2 $map2 -Apply
  Assert-Case 'token old is literal (escaped) and $ in new is literal' ($r2.Exit -eq 0 -and (Get-Text $f2) -eq "NEW x; aXb y; a.bc z; Baz`$Qux q;`n") (Get-Text $f2)

  # --- rule order: rules run top to bottom, each on the previous output ---
  $root3 = Join-Path $tmp 'repo3'
  $f3 = New-Fixture $root3 'src/B.java' @('Foo.bar(1);') "`n" $false
  $map3 = Join-Path $tmp 'map3.txt'
  [System.IO.File]::WriteAllText($map3, "literal Foo.bar( Foo.baz(`ntoken Foo Qux`n")
  $null = Invoke-Tool $root3 $map3 -Apply
  $forward = Get-Text $f3
  $f3 = New-Fixture $root3 'src/B.java' @('Foo.bar(1);') "`n" $false
  [System.IO.File]::WriteAllText($map3, "token Foo Qux`nliteral Foo.bar( Foo.baz(`n")
  $null = Invoke-Tool $root3 $map3 -Apply
  $reversed = Get-Text $f3
  Assert-Case 'rule order: literal first, then token' ($forward -eq "Qux.baz(1);`n" -and $reversed -eq "Qux.bar(1);`n") "forward=$forward reversed=$reversed"

  # --- string-literal heuristic edge cases ---
  $root4 = Join-Path $tmp 'repo4'
  $null = New-Fixture $root4 'src/C.java' @(
    'x("a", ResourceLocation.ID);'              # after a closed string: code
    'x("say \"ResourceLocation\" here");'        # inside a string with escaped quotes: flagged
    'char q = ''"''; ResourceLocation r;'        # quote char literal: code
    'x("\\", ResourceLocation.ID);'             # string ending in an escaped backslash: code
  ) "`r`n" $false
  $map4 = Join-Path $tmp 'map4.txt'
  [System.IO.File]::WriteAllText($map4, "token ResourceLocation Identifier`n")
  $r4 = Invoke-Tool $root4 $map4
  Assert-Case 'string heuristic: only the real in-string hit is flagged' ($r4.Out -match 'hits=4 stringHits=1' -and $r4.Out -match 'C\.java:2 ') ($r4.Out -split "`n" | Select-String 'C.java|rules=' | Out-String)

  # --- class rules: FQN everywhere, simple name only where the old class is imported ---
  $root5 = Join-Path $tmp 'repo5'
  $importer = New-Fixture $root5 'src/a/Importer.java' @(
    'package a;'
    'import net.mc.model.Material;'
    'import net.mc.model.Face;'
    'import static net.mc.gui.Graphics.helper;'
    '/** {@link Material} and {@link net.mc.gui.Graphics}. */'
    'class Importer { Material m = new Material(); Face.UVs uv; Face.Other o; x.Material notThis; MaterialSet keep; String s = "Material"; }'
  ) "`n" $false
  $other = New-Fixture $root5 'src/a/Other.java' @(
    'package a;'
    'import slimeknights.tconstruct.Material;'
    'class Other { Material mine; net.mc.model.Material fqn; }'
  ) "`n" $false
  $nested = New-Fixture $root5 'src/a/Nested.java' @(
    'package a;'
    'import net.mc.model.Face.UVs;'
    'class Nested { UVs uv; }'
  ) "`n" $false
  $map5 = Join-Path $tmp 'map5.txt'
  [System.IO.File]::WriteAllText($map5, @(
      'class net.mc.model.Face$UVs net.mc.cuboid.CuboidFace$Uv'
      'class net.mc.model.Material net.mc.sprite.SpriteId'
      'class net.mc.model.Face net.mc.cuboid.CuboidFace'
      'class net.mc.gui.Graphics net.mc.gui.GraphicsExtractor'
    ) -join "`n")
  $r5 = Invoke-Tool $root5 $map5 -Apply
  $t5 = Get-Text $importer
  Assert-Case 'class rule exits 0' ($r5.Exit -eq 0) $r5.Out
  Assert-Case 'class rule renames import and simple name in the importing file' ($t5 -match 'import net\.mc\.sprite\.SpriteId;' -and $t5 -match 'SpriteId m = new SpriteId\(\);' -and $t5 -match '\{@link SpriteId\}') $t5
  Assert-Case 'class rule keeps qualified other.Material, MaterialSet' ($t5 -match 'x\.Material notThis' -and $t5 -match 'MaterialSet keep') $t5
  Assert-Case 'class rule renames static import and javadoc FQN' ($t5 -match 'import static net\.mc\.gui\.GraphicsExtractor\.helper;' -and $t5 -match '\{@link net\.mc\.gui\.GraphicsExtractor\}') $t5
  Assert-Case 'class rule: nested renamed independently via outer import (Face.UVs -> CuboidFace.Uv)' ($t5 -match 'import net\.mc\.cuboid\.CuboidFace;' -and $t5 -match 'CuboidFace\.Uv uv;' -and $t5 -match 'CuboidFace\.Other o;') $t5
  Assert-Case 'class rule flags the string hit' ($r5.Out -match 'Importer\.java:6 ') ($r5.Out -split "`n" | Select-String 'WARNING|Importer' | Out-String)
  $o5 = Get-Text $other
  Assert-Case 'class rule leaves a same-named class of another package alone, FQN still renamed' ($o5 -match 'import slimeknights\.tconstruct\.Material;' -and $o5 -match 'Material mine;' -and $o5 -match 'net\.mc\.sprite\.SpriteId fqn;') $o5
  $n5 = Get-Text $nested
  Assert-Case 'class rule: directly imported nested class' ($n5 -match 'import net\.mc\.cuboid\.CuboidFace\.Uv;' -and $n5 -match 'Uv uv;') $n5
  $root6 = Join-Path $tmp 'repo6'
  $keep = New-Fixture $root6 'src/K.java' @('import net.mc.Keep;', 'class K { Keep.Inner x; Keep y; }') "`r`n" $false
  $map6 = Join-Path $tmp 'map6.txt'
  [System.IO.File]::WriteAllText($map6, "class net.mc.Keep`$Inner net.mc.other.Moved`$Inner`n")
  $null = Invoke-Tool $root6 $map6 -Apply
  Assert-Case 'class rule: nested class under another outer adds the new outer import (CRLF kept)' ((Get-Text $keep) -eq "import net.mc.Keep;`r`nimport net.mc.other.Moved;`r`nclass K { Moved.Inner x; Keep y; }`r`n") (Get-Text $keep)
  $badClass = Join-Path $tmp 'badclass.txt'
  [System.IO.File]::WriteAllText($badClass, "class NoPackage a.b.C`n")
  $r = Invoke-Tool $root5 $badClass
  Assert-Case 'class rule without a package is a syntax error' ($r.Exit -eq 2) "exit=$($r.Exit)"

  # --- field rules: moved static fields / constants folded into collections ---
  $root7 = Join-Path $tmp 'repo7'
  $fUser = New-Fixture $root7 'src/a/User.java' @(
    'package a;'
    'import net.mc.world.EntityType;'
    'import net.mc.item.Items;'
    'class User {'
    '  Object p = EntityType.PIG, q = EntityType'
    '      .COW, r = net.mc.world.EntityType.PIG, keep = EntityType.PIGLIN, s = "EntityType.PIG";'
    '  Object d = Items.RED_DYE, c = Items.COPPER_BLOCK, w = Items.WAXED_COPPER_BLOCK, stick = Items.STICK;'
    '  EntityType<?> type;'
    '}'
  ) "`r`n" $false
  # imports only for code hits: here the only uses are in a string and in comments
  $fOnlyText = New-Fixture $root7 'src/e/OnlyText.java' @(
    'package e;'
    'import net.mc.item.Items;'
    '/* Items.RED_DYE in a block comment */'
    'class OnlyText { String s = "Items.RED_DYE"; } // Items.RED_DYE'
  ) "`n" $false
  # a DyeColor of the file's own package / of a wildcard-imported package (sources under -Root) is a clash too
  $null = New-Fixture $root7 'src/my/pkg/DyeColor.java' @('package my.pkg;', 'public class DyeColor {}') "`n" $false
  $fSibling = New-Fixture $root7 'src/my/pkg/Sibling.java' @(
    'package my.pkg;'
    'import net.mc.item.Items;'
    'class Sibling { Object d = Items.RED_DYE; DyeColor mine; }'
  ) "`n" $false
  $fWild = New-Fixture $root7 'src/w/Wild.java' @(
    'package w;'
    'import my.pkg.*;'
    'import net.mc.world.*;'
    'import net.mc.world.EntityType;'
    'import net.mc.item.Items;'
    'class Wild { Object d = Items.RED_DYE, p = EntityType.PIG; DyeColor mine; }'
  ) "`n" $false
  $fOther = New-Fixture $root7 'src/b/Other.java' @(
    'package b;'
    'import my.own.EntityType;'
    'class Other { Object p = EntityType.PIG, q = net.mc.world.EntityType.COW; }'
  ) "`n" $false
  $fStatic = New-Fixture $root7 'src/c/Static.java' @(
    'package c;'
    'import static net.mc.world.EntityType.PIG;'
    'import static net.mc.item.Items.RED_DYE;'
    'class Static { Object p = PIG, d = RED_DYE; }'
  ) "`n" $false
  $fClash = New-Fixture $root7 'src/d/Clash.java' @(
    'package d;'
    'import net.mc.item.Items;'
    'import my.own.DyeColor;'
    'class Clash { Object d = Items.RED_DYE; }'
  ) "`n" $false
  $fSamePkg = New-Fixture $root7 'src/net/mc/item/Local.java' @(
    'package net.mc.item;'
    'import net.mc.world.EntityType;'
    'import net.mc.item.Items;'
    'class Local { Object d = Items.RED_DYE; }'
  ) "`n" $false
  $map7 = Join-Path $tmp 'map7.txt'
  [System.IO.File]::WriteAllText($map7, @(
      'field net.mc.world.EntityType.PIG net.mc.world.EntityTypes.PIG'
      'field net.mc.world.EntityType.COW net.mc.world.EntityTypes.COW'
      'field net.mc.item.Items.RED_DYE net.mc.item.Items.DYE.pick(net.mc.item.DyeColor.RED)'
      'field net.mc.item.Items.COPPER_BLOCK net.mc.item.Items.COPPER_BLOCK.weathering().unaffected()'
      'field net.mc.item.Items.WAXED_COPPER_BLOCK net.mc.item.Items.COPPER_BLOCK.waxed().unaffected()'
    ) -join "`n")
  $r7 = Invoke-Tool $root7 $map7 -Apply
  Assert-Case 'field rule exits 0' ($r7.Exit -eq 0) $r7.Out
  $u7 = Get-Text $fUser
  Assert-Case 'field rule: moved field in a file importing the old owner (+ import, CRLF kept, line break inside the access)' ($u7 -eq (@(
        'package a;', 'import net.mc.world.EntityType;', 'import net.mc.item.Items;', 'import net.mc.world.EntityTypes;', 'import net.mc.item.DyeColor;', 'class User {'
        '  Object p = EntityTypes.PIG, q = EntityTypes.COW, r = net.mc.world.EntityTypes.PIG, keep = EntityType.PIGLIN, s = "EntityTypes.PIG";'
        '  Object d = Items.DYE.pick(DyeColor.RED), c = Items.COPPER_BLOCK.weathering().unaffected(), w = Items.COPPER_BLOCK.waxed().unaffected(), stick = Items.STICK;'
        '  EntityType<?> type;', '}', '') -join "`r`n")) $u7
  Assert-Case 'field rule: string hit flagged' ($r7.Out -match 'User\.java:6 ') ($r7.Out -split "`n" | Select-String 'WARNING|User' | Out-String)
  Assert-Case 'field rule: another EntityType class keeps its simple name, the FQN form is still rewritten' ((Get-Text $fOther) -eq "package b;`nimport my.own.EntityType;`nclass Other { Object p = EntityType.PIG, q = net.mc.world.EntityTypes.COW; }`n") (Get-Text $fOther)
  Assert-Case 'field rule: static import of a moved field rewritten; of an expression left alone and reported' ((Get-Text $fStatic) -match 'import static net\.mc\.world\.EntityTypes\.PIG;' -and (Get-Text $fStatic) -match 'import static net\.mc\.item\.Items\.RED_DYE;' -and $r7.Out -match 'Static\.java:3  \[rule 3\]  static import of net\.mc\.item\.Items\.RED_DYE not rewritten') "$(Get-Text $fStatic) $($r7.Out)"
  Assert-Case 'field rule: a clashing simple name keeps the FQN in the expression (no import)' ((Get-Text $fClash) -match 'Items\.DYE\.pick\(net\.mc\.item\.DyeColor\.RED\)' -and (Get-Text $fClash) -notmatch 'import net\.mc\.item\.DyeColor;') (Get-Text $fClash)
  Assert-Case 'field rule: no import for a class of the same package' ((Get-Text $fSamePkg) -match 'Items\.DYE\.pick\(DyeColor\.RED\)' -and (Get-Text $fSamePkg) -notmatch 'import net\.mc\.item\.DyeColor;') (Get-Text $fSamePkg)
  $o7 = Get-Text $fOnlyText
  Assert-Case 'field rule: matches only in a string / comments add no import' ($o7 -notmatch 'import net\.mc\.item\.DyeColor;' -and $o7 -match '"Items\.DYE\.pick\(DyeColor\.RED\)"') $o7
  Assert-Case 'field rule: a same-package class of that simple name keeps the FQN (no import)' ((Get-Text $fSibling) -match 'Items\.DYE\.pick\(net\.mc\.item\.DyeColor\.RED\)' -and (Get-Text $fSibling) -notmatch 'import net\.mc\.item\.DyeColor;') (Get-Text $fSibling)
  $w7 = Get-Text $fWild
  Assert-Case 'field rule: a wildcard-imported class of that simple name keeps the FQN; a wildcard of the target package needs no import' ($w7 -match 'Items\.DYE\.pick\(net\.mc\.item\.DyeColor\.RED\)' -and $w7 -notmatch 'import net\.mc\.item\.DyeColor;' -and $w7 -match 'p = EntityTypes\.PIG;' -and $w7 -notmatch 'import net\.mc\.world\.EntityTypes;') $w7
  $again7 = Invoke-Tool $root7 $map7 -Apply
  Assert-Case 'field rule: second apply is a no-op (same-name rule guarded against its siblings'' accessors)' ($again7.Exit -eq 0 -and $again7.Out -match 'written=0' -and $again7.Out -match 'hits=0') $again7.Out
  $badField = Join-Path $tmp 'badfield.txt'
  [System.IO.File]::WriteAllText($badField, "field NoOwner.X net.mc.A.X`nfield net.mc.A.X lowercase.expr`n")
  $r = Invoke-Tool $root7 $badField
  Assert-Case 'field rule without an owner FQN / with an expression not starting with a class is a syntax error' ($r.Exit -eq 2 -and $r.Out -match 'line 1:' -and $r.Out -match 'line 2:') $r.Out

  # --- bad map syntax ---
  foreach ($bad in @('token OnlyTwo', 'rename A B', 'token A B # trailing comment')) {
    $badMap = Join-Path $tmp 'bad.txt'
    [System.IO.File]::WriteAllText($badMap, "token Good Fine`n$bad`n")
    $r = Invoke-Tool $root $badMap
    Assert-Case "bad map line '$bad' exits nonzero" ($r.Exit -ne 0 -and $r.Out -match 'bad map syntax' -and $r.Out -match 'line 2') "exit=$($r.Exit)"
  }
  $r = Invoke-Tool $root (Join-Path $tmp 'missing.txt')
  Assert-Case 'missing map exits nonzero' ($r.Exit -ne 0)
}
finally {
  Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
if ($script:failures -gt 0) { Write-Host "$script:failures test(s) FAILED"; exit 1 }
Write-Host 'All tests PASSED'
exit 0
