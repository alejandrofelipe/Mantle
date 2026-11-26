<#
.SYNOPSIS
  Seeds rename-map rules for vanilla classes that moved package between two Minecraft versions, from the jar itself.

.DESCRIPTION
  Collects every `import net.minecraft.*` / `import com.mojang.*` FQN used under -Root (src/**/*.java), checks each one
  against the class list of the target version's jar (-Jar: e.g. the NeoForm joined `raw.jar`), and for every import
  whose class no longer exists looks for classes with the same simple name in the jar:
  - exactly one candidate -> printed as a `literal <old-fqn>; <new-fqn>;` rule (import statements only) — review it;
  - several candidates     -> printed as a comment listing them (pick by hand);
  - none                   -> printed as a comment (removed or renamed class: the compiler/primer decides).
  Nested classes (`import a.b.Outer.Inner`) are checked as `Outer$Inner`. Static imports are ignored.
  Output goes to stdout (pipe it into a map file after review). The jar is the authority; primers are non-exhaustive.

.EXAMPLE
  .\Find-MovedClasses.ps1 -Root ..\..\..\tinkers -Jar ..\..\build\neoForm\neoFormJoined1.21.11-20251209.172050\raw.jar
#>
param(
  [Parameter(Mandatory)] [string] $Root,
  [Parameter(Mandatory)] [string] $Jar
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $Jar))
$classes = [System.Collections.Generic.HashSet[string]]::new()
$bySimple = @{}
try {
  foreach ($entry in $zip.Entries) {
    if (-not $entry.FullName.EndsWith('.class')) { continue }
    $fqn = $entry.FullName.Substring(0, $entry.FullName.Length - 6).Replace('/', '.')
    [void]$classes.Add($fqn)
    $simple = $fqn.Substring($fqn.LastIndexOf('.') + 1)
    if (-not $bySimple.ContainsKey($simple)) { $bySimple[$simple] = [System.Collections.Generic.List[string]]::new() }
    $bySimple[$simple].Add($fqn)
  }
} finally { $zip.Dispose() }

function Test-ClassExists([string] $fqn) {
  # try a.b.C, then a.b$C ... for nested classes
  $parts = $fqn.Split('.')
  for ($i = $parts.Length - 1; $i -ge 1; $i--) {
    $candidate = ($parts[0..($i - 1)] -join '.') + '.' + ($parts[$i..($parts.Length - 1)] -join '$')
    if ($classes.Contains($candidate)) { return $true }
  }
  return $false
}

$imports = Get-ChildItem -LiteralPath (Join-Path $Root 'src') -Recurse -Filter *.java -File |
  Select-String -Pattern '^\s*import\s+((?:net\.minecraft|com\.mojang)\.[\w.]+)\s*;' |
  ForEach-Object { $_.Matches[0].Groups[1].Value } | Sort-Object -Unique

# packages (first two segments) present in the jar: imports from libraries outside it (brigadier, DFU, serialization,
# authlib, ...) are not Minecraft classes and are skipped
$roots = [System.Collections.Generic.HashSet[string]]::new()
foreach ($c in $classes) { $p = $c.Split('.'); if ($p.Length -ge 3) { [void]$roots.Add($p[0] + '.' + $p[1] + '.' + $p[2]) } }

$moved = 0; $ambiguous = 0; $missing = 0
$seen = [System.Collections.Generic.HashSet[string]]::new()
foreach ($fqn in $imports) {
  $p3 = $fqn.Split('.'); if ($p3.Length -lt 3 -or -not $roots.Contains($p3[0] + '.' + $p3[1] + '.' + $p3[2])) { continue }
  if (Test-ClassExists $fqn) { continue }
  # find the top-level class segment (first capitalized) to search by simple name; keep the nested tail
  $parts = $fqn.Split('.')
  $top = 0; while ($top -lt $parts.Length -and $parts[$top] -cnotmatch '^[A-Z]') { $top++ }
  if ($top -ge $parts.Length) { "# ? $fqn (no class segment)"; $missing++; continue }
  $simple = $parts[$top]
  $oldTop = ($parts[0..$top] -join '.')
  if (-not $seen.Add($oldTop)) { continue }
  $cands = @(if ($bySimple.ContainsKey($simple)) { $bySimple[$simple] | Where-Object { $_ -notmatch '\$' } })
  # two rules per moved class: the import itself and the `Outer.` prefix (nested imports, inline FQNs)
  if ($cands.Count -eq 1) { "literal $oldTop;`t$($cands[0]);"; "literal $oldTop.`t$($cands[0])."; $moved++ }
  elseif ($cands.Count -gt 1) { "# ambiguous $fqn -> $($cands -join ', ')"; $ambiguous++ }
  else { "# missing $fqn"; $missing++ }
}
"# summary: imports=$($imports.Count) moved=$moved ambiguous=$ambiguous missing=$missing"
