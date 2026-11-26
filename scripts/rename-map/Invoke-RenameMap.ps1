<#
.SYNOPSIS
  Applies a rename map (mechanical old -> new renames between Minecraft versions) to Java sources.

.DESCRIPTION
  Reads a map file and applies its rules, in file order, to every file under -Root matching -Include.
  Default is a DRY RUN: nothing is written; the script prints hit counts per rule and per file, a WARNING
  list of hits that look like they fall inside a Java string literal, and a summary line
      rules=<n> files=<n> hits=<n> stringHits=<n>
  With -Apply the changed files are rewritten. Each file keeps its encoding (UTF-8, with a BOM only if it
  already had one) and its line endings (CRLF/LF are never normalized). Unchanged files are not touched.

  MAP FORMAT (one rule per line):
      <kind> <old> <new>
  - Blank lines and lines whose first non-space character is '#' are ignored. Trailing comments are NOT
    supported (they make the line have more than three fields, which is a syntax error).
  - Fields are separated by whitespace, so <old>/<new> cannot contain spaces.
  - kind 'token'   : whole-word replacement. <old> is matched literally with the regex
                     (?<![\w$])OLD(?![\w$]) - so 'token ResourceLocation Identifier' renames
                     ResourceLocation but leaves ResourceLocationLoadable, MyResourceLocation and
                     RESOURCE_LOCATION alone.
  - kind 'literal' : exact (case-sensitive) substring replacement, e.g. a full import FQN or a call
                     prefix like 'readResourceLocation('.
  - Rules apply in file order, each on the output of the previous ones: put specific rules (import FQNs,
    longer names) before the general token rule.

  The string-literal check is a heuristic: a hit is flagged when an odd number of unescaped '"' precede it
  on its line (char literals like '"' are skipped). Flagged hits are still counted and still replaced with
  -Apply; review them in the diff.

  Exit codes: 0 = success, 1 = bad arguments (missing root/map) or unreadable file, 2 = bad map syntax.

.EXAMPLE
  pwsh -NoProfile -File Invoke-RenameMap.ps1 -Root "C:\...\repo" -Map maps\1.21.11.txt
  pwsh -NoProfile -File Invoke-RenameMap.ps1 -Root "C:\...\repo" -Map maps\1.21.11.txt -Apply
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)] [string] $Root,
  [Parameter(Mandatory)] [string] $Map,
  [switch] $Apply,
  # Glob(s) relative to -Root; '**' matches any number of directories. Comma-separated values are split
  # too, so '-Include "src/main/**/*.java,src/test/**/*.java"' works through 'pwsh -File'.
  [string[]] $Include = @('src/**/*.java'),
  # How many files to list in the per-file table.
  [int] $Top = 20
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ---------- arguments ----------
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Write-Host "ERROR: root not found: $Root"; exit 1 }
if (-not (Test-Path -LiteralPath $Map -PathType Leaf)) { Write-Host "ERROR: map not found: $Map"; exit 1 }
$Root = (Resolve-Path -LiteralPath $Root).ProviderPath.TrimEnd('\', '/')
$Map = (Resolve-Path -LiteralPath $Map).ProviderPath

# ---------- parse map ----------
$rules = [System.Collections.Generic.List[object]]::new()
$errors = [System.Collections.Generic.List[string]]::new()
$lineNo = 0
foreach ($raw in [System.IO.File]::ReadAllLines($Map)) {
  $lineNo++
  $text = $raw.Trim()
  if ($text.Length -eq 0 -or $text.StartsWith('#')) { continue }
  $fields = $text -split '\s+'
  if ($fields.Count -ne 3) { $errors.Add("line ${lineNo}: expected '<kind> <old> <new>', got: $raw"); continue }
  $kind, $old, $new = $fields
  if ($kind -ceq 'token') { $pattern = '(?<![\w$])' + [regex]::Escape($old) + '(?![\w$])' }
  elseif ($kind -ceq 'literal') { $pattern = [regex]::Escape($old) }
  else { $errors.Add("line ${lineNo}: unknown kind '$kind' (expected 'token' or 'literal'): $raw"); continue }
  $rules.Add([pscustomobject]@{
      Index = $rules.Count + 1; Line = $lineNo; Kind = $kind; Old = $old; New = $new
      Regex = [regex]::new($pattern, [System.Text.RegularExpressions.RegexOptions]::CultureInvariant)
      Hits = 0; Files = 0
    })
}
if ($errors.Count -gt 0) {
  Write-Host "ERROR: bad map syntax in ${Map}:"
  $errors | ForEach-Object { Write-Host "  $_" }
  exit 2
}
if ($rules.Count -eq 0) { Write-Host "ERROR: map has no rules: $Map"; exit 2 }

# ---------- collect files ----------
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
  # Walk only from the literal directory prefix (e.g. 'src'), not the whole repo (build/, run/ are big).
  $segments = $norm -split '/'
  $prefix = @()
  for ($k = 0; $k -lt $segments.Count - 1; $k++) { if ($segments[$k] -match '[*?]') { break }; $prefix += $segments[$k] }
  $start = if ($prefix.Count) { Join-Path $Root ($prefix -join '\') } else { $Root }
  if (-not (Test-Path -LiteralPath $start -PathType Container)) { continue }
  $rx = ConvertTo-GlobRegex $norm
  foreach ($f in Get-ChildItem -LiteralPath $start -Recurse -File -Force) {
    $rel = $f.FullName.Substring($Root.Length + 1).Replace('\', '/')
    if ($rx.IsMatch($rel)) { [void]$files.Add($f.FullName) }
  }
}

# ---------- string-literal heuristic ----------
# True when position $pos (on its line) is inside a "..." string: scans the line prefix, honouring
# backslash escapes inside strings and skipping char literals such as '"'.
function Test-InString([string] $content, [int] $pos) {
  $lineStart = if ($pos -gt 0) { $content.LastIndexOf("`n", $pos - 1) + 1 } else { 0 }
  $inString = $false
  $i = $lineStart
  while ($i -lt $pos) {
    $c = $content[$i]
    if ($inString) {
      if ($c -eq '\') { $i += 2; continue }
      if ($c -eq '"') { $inString = $false }
    }
    elseif ($c -eq '"') { $inString = $true }
    elseif ($c -eq "'") {
      # skip a char literal: 'x' or '\x' (only if it closes within a few chars)
      $j = $i + 1
      if ($j -lt $content.Length -and $content[$j] -eq '\') { $j += 2 } else { $j += 1 }
      while ($j -lt $content.Length -and $j -lt $i + 8 -and $content[$j] -ne "'" -and $content[$j] -ne "`n") { $j++ }
      if ($j -lt $content.Length -and $content[$j] -eq "'" -and $j -lt $pos) { $i = $j + 1; continue }
    }
    $i++
  }
  return $inString
}

function Get-LineInfo([string] $content, [int] $pos) {
  $lineStart = if ($pos -gt 0) { $content.LastIndexOf("`n", $pos - 1) + 1 } else { 0 }
  $lineEnd = $content.IndexOf("`n", $pos)
  if ($lineEnd -lt 0) { $lineEnd = $content.Length }
  $number = 1
  for ($k = 0; $k -lt $lineStart; $k++) { if ($content[$k] -eq "`n") { $number++ } }
  [pscustomobject]@{ Number = $number; Text = $content.Substring($lineStart, $lineEnd - $lineStart).Trim() }
}

# ---------- apply rules ----------
$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$fileHits = [System.Collections.Generic.List[object]]::new()
$stringHits = [System.Collections.Generic.List[object]]::new()
$unreadable = [System.Collections.Generic.List[string]]::new()
$written = 0

foreach ($path in $files) {
  $rel = $path.Substring($Root.Length + 1).Replace('\', '/')
  $bytes = [System.IO.File]::ReadAllBytes($path)
  $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
  $offset = if ($hasBom) { 3 } else { 0 }
  try { $content = $utf8Strict.GetString($bytes, $offset, $bytes.Length - $offset) }
  catch { $unreadable.Add($rel); continue }

  $original = $content
  $hitsInFile = 0
  foreach ($rule in $rules) {
    $matches_ = $rule.Regex.Matches($content)
    if ($matches_.Count -eq 0) { continue }
    foreach ($m in $matches_) {
      if (Test-InString $content $m.Index) {
        $info = Get-LineInfo $content $m.Index
        $stringHits.Add([pscustomobject]@{ File = $rel; Line = $info.Number; Rule = $rule.Index; Old = $rule.Old; Text = $info.Text })
      }
    }
    $rule.Hits += $matches_.Count
    $rule.Files++
    $hitsInFile += $matches_.Count
    $content = $rule.Regex.Replace($content, $rule.New.Replace('$', '$$'))
  }

  if ($hitsInFile -gt 0) { $fileHits.Add([pscustomobject]@{ File = $rel; Hits = $hitsInFile }) }
  if ($Apply -and $content -ne $original) {
    $out = $utf8Strict.GetBytes($content)
    if ($hasBom) { $out = [byte[]](0xEF, 0xBB, 0xBF) + $out }
    [System.IO.File]::WriteAllBytes($path, $out)
    $written++
  }
}

# ---------- report ----------
$mode = if ($Apply) { 'APPLY' } else { 'DRY RUN (nothing written; pass -Apply to rewrite)' }
Write-Host "rename-map: $mode"
Write-Host "  root:    $Root"
Write-Host "  map:     $Map"
Write-Host "  include: $($globs -join ', ')  ($($files.Count) files scanned)"
Write-Host ''
Write-Host 'Hits per rule:'
$rules | Format-Table @{N = '#'; E = { $_.Index } }, Kind, Old, New, Hits, Files -AutoSize | Out-String -Width 220 | Write-Host

$totalHits = ($rules | Measure-Object Hits -Sum).Sum
Write-Host "Hits per file (top $Top of $($fileHits.Count) files):"
$fileHits | Sort-Object @{E = 'Hits'; Descending = $true }, File | Select-Object -First $Top |
  Format-Table Hits, File -AutoSize | Out-String -Width 220 | Write-Host

if ($stringHits.Count -gt 0) {
  Write-Host "WARNING: $($stringHits.Count) hit(s) inside a string literal (heuristic; counted and replaced anyway - review):"
  foreach ($h in $stringHits) { Write-Host ("  {0}:{1}  [rule {2} {3}]  {4}" -f $h.File, $h.Line, $h.Rule, $h.Old, $h.Text) }
  Write-Host ''
}
if ($unreadable.Count -gt 0) {
  Write-Host "WARNING: $($unreadable.Count) file(s) skipped (not valid UTF-8):"
  $unreadable | ForEach-Object { Write-Host "  $_" }
  Write-Host ''
}
if ($Apply) { Write-Host "written=$written" }
Write-Host "rules=$($rules.Count) files=$($fileHits.Count) hits=$totalHits stringHits=$($stringHits.Count)"
if ($unreadable.Count -gt 0) { exit 1 }
exit 0
