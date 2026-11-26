<#
.SYNOPSIS
  M7 Phase 0.6 (decision D-N2): migrates the jsr305 nullness annotations (javax.annotation.*) of Java sources to
  JSpecify (org.jspecify.annotations.Nullable), the annotations vanilla and NeoForge use since 1.21.11.

.DESCRIPTION
  For every file under -Root matching -Include (default src/**/*.java). Comments, string literals and text blocks are
  never touched. Default is a DRY RUN: nothing is written; -Apply rewrites the changed files, keeping each file's
  encoding (UTF-8, BOM only if it had one) and line endings.

  1. @CheckForNull -> @Nullable; @javax.annotation.Nullable/CheckForNull -> @Nullable (file with the import) or
     @org.jspecify.annotations.Nullable.
  2. Placement. JSpecify @Nullable is a TYPE_USE annotation, jsr305's was a declaration annotation. In every file that
     imported javax.annotation.Nullable (or CheckForNull), each @Nullable is followed past modifiers, other annotations
     and method type parameters to the type it now annotates:
     - qualified type: '@Nullable Outer.Inner' (javac rejects it for a static nested class or a package prefix, and for
       an inner class it annotates Outer) -> 'Outer.@Nullable Inner'; same for 'pkg.Type' -> 'pkg.@Nullable Type';
     - array: '@Nullable String[]' now means nullable elements; jsr305 meant a nullable array -> 'String @Nullable []'
       (also 'Outer.Inner @Nullable []', 'int @Nullable [][]');
     - reported, not rewritten (decide by hand): varargs ('@Nullable Object...'), primitives, 'void', 'var', a generic
       outer type ('Outer<T>.Inner') and any @Nullable the parser can't follow.
     The annotation moves from its old place (with the whitespace after it, so a '@Nullable' line merges into the next
     one) to right before the annotated type part.
  3. @Nonnull is removed (packages are @NullMarked; files whose folder has no @NullMarked package-info are reported).
     Exception: a @Nonnull FIELD in a file that uses a Lombok annotation generating constructors or setters
     (@RequiredArgsConstructor, @AllArgsConstructor, @Data, @Value, @Setter, @Builder, @SuperBuilder, @With) becomes
     Lombok's @NonNull (import lombok.NonNull added), because Lombok generates a null check for such fields from
     javax.annotation.Nonnull and the generated code must not change.
  4. package-info.java with @NullMarked: leftover @ParametersAreNonnullByDefault / @MethodsReturnNonnullByDefault
     lines are removed.
  5. Imports: javax.annotation.Nullable / CheckForNull -> one 'import org.jspecify.annotations.Nullable;' (dropped if
     the file already has it); any other javax.annotation import is dropped when its annotation is no longer used,
     otherwise kept and reported, like every other javax.annotation reference left in code.

  Every placement rewrite is listed (file:line, before -> after) for review. Summary line:
      files=<n> changed=<n> imports=<n> checkForNull=<n> nonnullRemoved=<n> nonnullLombok=<n> qualified=<n> arrays=<n> fqn=<n> notes=<n>
  Exit codes: 0 = success, 1 = bad arguments or an unreadable (non UTF-8) file, 3 = overlapping edits in a file
  (nothing written for that file).

.EXAMPLE
  pwsh -NoProfile -File Convert-NullableToJSpecify.ps1 -Root "C:\...\repo"
  pwsh -NoProfile -File Convert-NullableToJSpecify.ps1 -Root "C:\...\repo" -Apply
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)] [string] $Root,
  [switch] $Apply,
  # Glob(s) relative to -Root, as in Invoke-RenameMap.ps1 ('**' = any number of directories, comma-separated ok).
  [string[]] $Include = @('src/**/*.java')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Write-Host "ERROR: root not found: $Root"; exit 1 }
$Root = (Resolve-Path -LiteralPath $Root).ProviderPath.TrimEnd('\', '/')

# ---------- collect files (same glob semantics as Invoke-RenameMap.ps1) ----------
function ConvertTo-GlobRegex([string] $glob) {
  $g = $glob.Replace('\', '/').TrimStart('/')
  $sb = [System.Text.StringBuilder]::new('^')
  for ($i = 0; $i -lt $g.Length; $i++) {
    $c = $g[$i]
    if ($c -eq '*') {
      if ($i + 1 -lt $g.Length -and $g[$i + 1] -eq '*') {
        if ($i + 2 -lt $g.Length -and $g[$i + 2] -eq '/') { [void]$sb.Append('(?:.*/)?'); $i += 2 }
        else { [void]$sb.Append('.*'); $i += 1 }
      }
      else { [void]$sb.Append('[^/]*') }
    }
    elseif ($c -eq '?') { [void]$sb.Append('[^/]') }
    else { [void]$sb.Append([regex]::Escape([string]$c)) }
  }
  [void]$sb.Append('$')
  [regex]::new($sb.ToString(), 'IgnoreCase')
}
$globs = @($Include | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$files = [System.Collections.Generic.SortedSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($glob in $globs) {
  $norm = $glob.Replace('\', '/').TrimStart('/')
  $segments = $norm -split '/'
  $prefix = @()
  for ($k = 0; $k -lt $segments.Count - 1; $k++) { if ($segments[$k] -cmatch '[*?]') { break }; $prefix += $segments[$k] }
  $start = if ($prefix.Count) { Join-Path $Root ($prefix -join '\') } else { $Root }
  if (-not (Test-Path -LiteralPath $start -PathType Container)) { continue }
  $rx = ConvertTo-GlobRegex $norm
  foreach ($f in Get-ChildItem -LiteralPath $start -Recurse -File -Force) {
    $rel = $f.FullName.Substring($Root.Length + 1).Replace('\', '/')
    if ($rx.IsMatch($rel)) { [void]$files.Add($f.FullName) }
  }
}

# ---------- lexical helpers ----------
# Non-code spans: text blocks, strings, char literals, line and block comments (leftmost match wins, like a lexer).
$nonCodeRx = [regex]::new('(?s)""".*?"""|"(?:\\.|[^"\\\r\n])*"|''(?:\\.|[^''\\\r\n])+''|//[^\r\n]*|/\*.*?\*/')
function Get-NonCode([string] $text) {
  $list = [System.Collections.Generic.List[int[]]]::new()
  foreach ($m in $nonCodeRx.Matches($text)) { $list.Add([int[]]@($m.Index, ($m.Index + $m.Length))) }
  , $list
}
function Test-Code($ranges, [int] $pos) {
  foreach ($r in $ranges) { if ($pos -lt $r[0]) { return $true }; if ($pos -lt $r[1]) { return $false } }
  return $true
}
function Get-Line([string] $text, [int] $pos) { [regex]::Matches($text.Substring(0, $pos), "`n").Count + 1 }
function Compress([string] $s) { ($s -replace '\s+', ' ').Trim() }

$mods = 'public|protected|private|static|final|abstract|synchronized|transient|volatile|default|native|strictfp'
$gen = '<(?:[^<>]|<(?:[^<>]|<(?:[^<>]|<[^<>]*>)*>)*>)*>'
$annot = '@(?!interface(?![\w$]))[\w$]+(?:\.[\w$]+)*(?:\s*\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\))?'
$pre = "(?:\s+(?:(?:$mods)(?![\w$])|$annot|$gen))*"
# what an annotation applies to: modifiers/annotations/type parameters, then the type, its type arguments and what follows
$declRx = [regex]::new("\G(?<pre>$pre)\s+(?<type>[\w$]+(?:\.[\w$]+)*)(?<gen>\s*$gen)?(?<after>\s*(?:\[|\.\.\.|\.(?=[\w$]))?)")
# a declaration: ... type name, then the character that tells field / method / parameter apart
$targetRx = [regex]::new("\G$pre\s+[\w$]+(?:\.[\w$]+)*(?:\s*$gen)?(?:\s*\[\s*\])*(?:\s*\.\.\.)?\s+[\w$]+\s*(?<next>[(),;={\[])")
$lombokGenRx = [regex]::new('@(?:lombok\.)?(?:RequiredArgsConstructor|AllArgsConstructor|Data|Value|Setter|Builder|SuperBuilder|With)(?![\w$])')
$keywordTypes = @('void', 'var', 'class', 'interface', 'enum', 'record') + ($mods -split '\|')
$primitives = @('int', 'long', 'short', 'byte', 'char', 'boolean', 'float', 'double')

# ---------- per-file state ----------
$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$rewrites = [System.Collections.Generic.List[object]]::new()
$notes = [System.Collections.Generic.List[object]]::new()
$unreadable = [System.Collections.Generic.List[string]]::new()
$overlaps = [System.Collections.Generic.List[string]]::new()
$totals = [ordered]@{ changed = 0; imports = 0; checkForNull = 0; nonnullRemoved = 0; nonnullLombok = 0; qualified = 0; arrays = 0; fqn = 0 }
$written = 0
$nullMarkedDirs = @{}

function Add-Note([string] $kind, [string] $file, [int] $line, [string] $text) {
  $notes.Add([pscustomobject]@{ Kind = $kind; File = $file; Line = $line; Text = $text })
}
function Test-NullMarkedDir([string] $dir) {
  if (-not $nullMarkedDirs.ContainsKey($dir)) {
    $pi = Join-Path $dir 'package-info.java'
    $nullMarkedDirs[$dir] = (Test-Path -LiteralPath $pi) -and ([System.IO.File]::ReadAllText($pi) -cmatch '@NullMarked(?![\w$])')
  }
  $nullMarkedDirs[$dir]
}
# replaces [start, end) of $text by $repl for each edit, from the end; returns $null when edits overlap
function Invoke-Edits([string] $text, $edits) {
  $sorted = @($edits | Sort-Object { $_.Start } -Descending)
  $sb = [System.Text.StringBuilder]::new($text)
  $limit = $text.Length
  foreach ($e in $sorted) {
    if ($e.End -gt $limit) { return $null }
    [void]$sb.Remove($e.Start, $e.End - $e.Start).Insert($e.Start, $e.Repl)
    $limit = $e.Start
  }
  $sb.ToString()
}
function New-Edit([int] $start, [int] $end, [string] $repl) { [pscustomobject]@{ Start = $start; End = $end; Repl = $repl } }

foreach ($path in $files) {
  $rel = $path.Substring($Root.Length + 1).Replace('\', '/')
  $bytes = [System.IO.File]::ReadAllBytes($path)
  $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
  $offset = if ($hasBom) { 3 } else { 0 }
  try { $original = $utf8Strict.GetString($bytes, $offset, $bytes.Length - $offset) }
  catch { $unreadable.Add($rel); continue }
  if (-not $original.Contains('javax.annotation') -and -not $original.Contains('NonnullByDefault')) { continue }
  $eol = if ($original.Contains("`r`n")) { "`r`n" } else { "`n" }
  $text = $original
  $fileCounts = @{ fqn = 0; checkForNull = 0; nonnullRemoved = 0; nonnullLombok = 0; qualified = 0; arrays = 0; imports = 0 }
  $migrates = $text -cmatch '(?m)^[ \t]*import[ \t]+javax\.annotation\.(?:Nullable|CheckForNull)[ \t]*;'

  # --- 1. @CheckForNull -> @Nullable (same line, so line numbers stay those of the original file) ---
  $nc = Get-NonCode $text
  $edits = foreach ($m in [regex]::Matches($text, '@CheckForNull(?![\w$])')) {
    if (Test-Code $nc $m.Index) { New-Edit $m.Index ($m.Index + $m.Length) '@Nullable'; $fileCounts.checkForNull++ }
  }
  if ($edits) { $text = Invoke-Edits $text @($edits) }
  # fully qualified uses: short form when the file gets the import, else the JSpecify FQN
  $nc = Get-NonCode $text
  $edits = foreach ($m in [regex]::Matches($text, '@javax\.annotation\.(?:Nullable|CheckForNull)(?![\w$.])')) {
    if (Test-Code $nc $m.Index) { New-Edit $m.Index ($m.Index + $m.Length) ($migrates ? '@Nullable' : '@org.jspecify.annotations.Nullable'); $fileCounts.fqn++ }
  }
  if ($edits) { $text = Invoke-Edits $text @($edits) }

  # --- 2. + 3. + 4. placement, @Nonnull, package-info leftovers: one edit list on the same text ---
  $nc = Get-NonCode $text
  $edits = [System.Collections.Generic.List[object]]::new()
  if ($migrates) {
    foreach ($m in [regex]::Matches($text, '@Nullable(?![\w$])')) {
      if (-not (Test-Code $nc $m.Index)) { continue }
      $line = Get-Line $text $m.Index
      $d = $declRx.Match($text, $m.Index + $m.Length)
      if (-not $d.Success) { Add-Note 'UNPARSED' $rel $line (Compress $text.Substring($m.Index, [Math]::Min(80, $text.Length - $m.Index))); continue }
      $type = $d.Groups['type']; $after = $d.Groups['after'].Value.Trim()
      $wsEnd = $m.Index + $m.Length
      while ($wsEnd -lt $text.Length -and [char]::IsWhiteSpace($text[$wsEnd])) { $wsEnd++ }
      $typeEnd = if ($d.Groups['gen'].Success) { $d.Groups['gen'].Index + $d.Groups['gen'].Length } else { $type.Index + $type.Length }
      if ($after -eq '[') {
        $bracket = $text.IndexOf('[', $typeEnd)
        $close = $text.IndexOf(']', $bracket) + 1
        $repl = $text.Substring($wsEnd, $typeEnd - $wsEnd) + ' @Nullable '
        $edits.Add((New-Edit $m.Index $bracket $repl)); $fileCounts.arrays++
        $rewrites.Add([pscustomobject]@{ Kind = 'ARRAY'; File = $rel; Line = $line
            Before = Compress $text.Substring($m.Index, $close - $m.Index); After = Compress ($repl + $text.Substring($bracket, $close - $bracket)) })
      }
      elseif ($after -eq '...') { Add-Note 'VARARGS' $rel $line (Compress $text.Substring($m.Index, $d.Index + $d.Length - $m.Index)) }
      elseif ($after -eq '.') { Add-Note 'GENERIC-OUTER' $rel $line (Compress $text.Substring($m.Index, $d.Index + $d.Length - $m.Index + 20)) }
      elseif ($keywordTypes -ccontains $type.Value) { Add-Note 'KEYWORD' $rel $line (Compress $text.Substring($m.Index, $typeEnd - $m.Index)) }
      elseif ($type.Value.Contains('.')) {
        $insert = $type.Index + $type.Value.LastIndexOf('.') + 1
        $repl = $text.Substring($wsEnd, $insert - $wsEnd) + '@Nullable '
        $edits.Add((New-Edit $m.Index $insert $repl)); $fileCounts.qualified++
        $rewrites.Add([pscustomobject]@{ Kind = 'QUALIFIED'; File = $rel; Line = $line
            Before = Compress $text.Substring($m.Index, $typeEnd - $m.Index); After = Compress ($repl + $text.Substring($insert, $typeEnd - $insert)) })
      }
      elseif ($primitives -ccontains $type.Value) { Add-Note 'PRIMITIVE' $rel $line (Compress $text.Substring($m.Index, $typeEnd - $m.Index)) }
    }
  }

  $nonnull = @([regex]::Matches($text, '@Nonnull(?![\w$])') | Where-Object { Test-Code $nc $_.Index })
  if ($nonnull.Count -gt 0) {
    $lombokGen = @($lombokGenRx.Matches($text) | Where-Object { Test-Code $nc $_.Index }).Count -gt 0
    $otherNonNull = $text -cmatch '(?m)^[ \t]*import[ \t]+(?!lombok\.)[\w.]+\.NonNull[ \t]*;'
    if (-not (Test-NullMarkedDir (Split-Path $path))) { Add-Note 'NO-NULLMARKED' $rel (Get-Line $text $nonnull[0].Index) 'removes @Nonnull outside a @NullMarked package' }
    foreach ($m in $nonnull) {
      $start = $m.Index; $end = $m.Index + $m.Length
      $t = $targetRx.Match($text, $end)
      if ($lombokGen -and $t.Success -and ';='.Contains($t.Groups['next'].Value)) {
        $edits.Add((New-Edit $start $end ($otherNonNull ? '@lombok.NonNull' : '@NonNull'))); $fileCounts.nonnullLombok++
        Add-Note 'NONNULL-LOMBOK' $rel (Get-Line $text $start) (Compress $text.Substring($start, $t.Index + $t.Length - $start))
        continue
      }
      $ls = if ($start -gt 0) { $text.LastIndexOf("`n", $start - 1) + 1 } else { 0 }
      $le = $text.IndexOf("`n", $end); if ($le -lt 0) { $le = $text.Length }
      if ($text.Substring($ls, $le - $ls).Trim() -eq '@Nonnull') {
        $edits.Add((New-Edit $ls ([Math]::Min($le + 1, $text.Length)) ''))
      }
      else {
        $j = $end; while ($j -lt $text.Length -and ($text[$j] -eq ' ' -or $text[$j] -eq "`t")) { $j++ }
        if ($j -gt $end -and $j -lt $text.Length -and $text[$j] -ne "`r" -and $text[$j] -ne "`n") { $edits.Add((New-Edit $start $j '')) }
        else {
          $k = $start; while ($k -gt $ls -and ($text[$k - 1] -eq ' ' -or $text[$k - 1] -eq "`t")) { $k-- }
          $edits.Add((New-Edit $k $j ''))
        }
      }
      $fileCounts.nonnullRemoved++
    }
  }

  if ($path.EndsWith('package-info.java') -and $text -cmatch '@NullMarked(?![\w$])') {
    foreach ($m in [regex]::Matches($text, '(?m)^[ \t]*@(?:ParametersAreNonnullByDefault|MethodsReturnNonnullByDefault)[ \t]*\r?\n')) {
      if (Test-Code $nc $m.Index) { $edits.Add((New-Edit $m.Index ($m.Index + $m.Length) '')) }
    }
  }

  if ($edits.Count -gt 0) {
    $new = Invoke-Edits $text $edits
    if ($null -eq $new) { $overlaps.Add($rel); continue }
    $text = $new
  }

  # --- 5. imports ---
  $nc = Get-NonCode $text
  $hasJspecify = $text -cmatch '(?m)^[ \t]*import[ \t]+org\.jspecify\.annotations\.Nullable[ \t]*;'
  $imports = @([regex]::Matches($text, '(?m)^[ \t]*import[ \t]+(?<fqn>(?:javax\.annotation|net\.minecraft)\.(?<name>[\w$]+))[ \t]*;[ \t]*(?:\r?\n)?'))
  $edits = [System.Collections.Generic.List[object]]::new()
  foreach ($m in $imports) {
    $name = $m.Groups['name'].Value; $fqn = $m.Groups['fqn'].Value
    if ($fqn -ceq 'javax.annotation.Nullable' -or $fqn -ceq 'javax.annotation.CheckForNull') {
      if ($hasJspecify) { $repl = '' }
      else { $repl = 'import org.jspecify.annotations.Nullable;' + ($m.Value.EndsWith("`n") ? $eol : ''); $hasJspecify = $true }
    }
    elseif ($fqn -ceq 'net.minecraft.MethodsReturnNonnullByDefault' -or $fqn.StartsWith('javax.annotation.')) {
      $used = @([regex]::Matches($text, "@(?:$([regex]::Escape($fqn))|$([regex]::Escape($name)))(?![\w$.])") | Where-Object { Test-Code $nc $_.Index }).Count -gt 0
      if ($used) { if ($fqn.StartsWith('javax.')) { Add-Note 'OTHER-JAVAX' $rel (Get-Line $text $m.Index) "import $fqn; (still used, kept)" }; continue }
      $repl = ''
    }
    else { continue }
    $start = $m.Index; $end = $m.Index + $m.Length
    if ($repl -eq '') {
      # dropping the only line of an import group must not leave two blank lines in a row
      $prevBlank = $start -ge 2 -and ($text.Substring(0, $start) -cmatch '(?:^|\n)[ \t]*\r?\n$')
      $nextBlank = [regex]::new('\G[ \t]*\r?\n').Match($text, $end)
      if ($prevBlank -and $nextBlank.Success) { $end += $nextBlank.Length }
    }
    $edits.Add((New-Edit $start $end $repl)); $fileCounts.imports++
  }
  if ($fileCounts.nonnullLombok -gt 0 -and -not ($text -cmatch '(?m)^[ \t]*import[ \t]+lombok\.NonNull[ \t]*;') -and -not ($text -cmatch '(?m)^[ \t]*import[ \t]+(?!lombok\.)[\w.]+\.NonNull[ \t]*;')) {
    $lombokImports = @([regex]::Matches($text, '(?m)^[ \t]*import[ \t]+lombok\.[\w.$]+[ \t]*;[ \t]*\r?\n'))
    $anchor = if ($lombokImports.Count) { $lombokImports[-1] } else { @([regex]::Matches($text, '(?m)^[ \t]*import[ \t]+[\w.$*]+[ \t]*;[ \t]*\r?\n'))[-1] }
    $at = $anchor.Index + $anchor.Length
    $edits.Add((New-Edit $at $at ('import lombok.NonNull;' + $eol)))
  }
  if ($edits.Count -gt 0) {
    $new = Invoke-Edits $text $edits
    if ($null -eq $new) { $overlaps.Add($rel); continue }
    $text = $new
  }

  # --- leftovers ---
  $nc = Get-NonCode $text
  foreach ($m in [regex]::Matches($text, 'javax\.annotation\.')) {
    if (-not (Test-Code $nc $m.Index)) { continue }
    $lineStart = $text.LastIndexOf("`n", [Math]::Max(0, $m.Index - 1)) + 1
    $lineEnd = $text.IndexOf("`n", $m.Index); if ($lineEnd -lt 0) { $lineEnd = $text.Length }
    $lineText = $text.Substring($lineStart, $lineEnd - $lineStart)
    if ($lineText -cmatch '^\s*import\s') { continue } # already reported above
    Add-Note 'OTHER-JAVAX' $rel (Get-Line $text $m.Index) (Compress $lineText)
  }

  if ($text -cne $original) {
    $totals.changed++
    foreach ($k in @($fileCounts.Keys)) { $totals[$k] += $fileCounts[$k] }
    if ($Apply) {
      $out = $utf8Strict.GetBytes($text)
      if ($hasBom) { $out = [byte[]](0xEF, 0xBB, 0xBF) + $out }
      [System.IO.File]::WriteAllBytes($path, $out)
      $written++
    }
  }
}

# ---------- report ----------
$mode = if ($Apply) { 'APPLY' } else { 'DRY RUN (nothing written; pass -Apply to rewrite)' }
Write-Host "convert-nullable: $mode"
Write-Host "  root:    $Root"
Write-Host "  include: $($globs -join ', ')  ($($files.Count) files scanned)"
Write-Host ''
if ($rewrites.Count -gt 0) {
  Write-Host "Placement rewrites ($($rewrites.Count), review each):"
  foreach ($r in $rewrites) { Write-Host ("  {0,-9} {1}:{2}  {3}  ->  {4}" -f $r.Kind, $r.File, $r.Line, $r.Before, $r.After) }
  Write-Host ''
}
if ($notes.Count -gt 0) {
  Write-Host "Notes ($($notes.Count); not rewritten unless NONNULL-LOMBOK):"
  foreach ($n in $notes) { Write-Host ("  {0,-14} {1}:{2}  {3}" -f $n.Kind, $n.File, $n.Line, $n.Text) }
  Write-Host ''
}
if ($unreadable.Count -gt 0) { Write-Host "WARNING: $($unreadable.Count) file(s) skipped (not valid UTF-8):"; $unreadable | ForEach-Object { Write-Host "  $_" } }
if ($overlaps.Count -gt 0) { Write-Host "ERROR: $($overlaps.Count) file(s) with overlapping edits, left unchanged:"; $overlaps | ForEach-Object { Write-Host "  $_" } }
if ($Apply) { Write-Host "written=$written" }
Write-Host ("files={0} changed={1} imports={2} checkForNull={3} nonnullRemoved={4} nonnullLombok={5} qualified={6} arrays={7} fqn={8} notes={9}" -f `
    $files.Count, $totals.changed, $totals.imports, $totals.checkForNull, $totals.nonnullRemoved, $totals.nonnullLombok, $totals.qualified, $totals.arrays, $totals.fqn, $notes.Count)
if ($overlaps.Count -gt 0) { exit 3 }
if ($unreadable.Count -gt 0) { exit 1 }
exit 0
