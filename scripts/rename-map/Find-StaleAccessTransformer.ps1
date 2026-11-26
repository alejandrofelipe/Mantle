<#
.SYNOPSIS
  Checks every entry of an access transformer against the classes of a target Minecraft jar.

.DESCRIPTION
  The rename map does not touch accesstransformer.cfg, and NeoForge skips an AT entry whose class or member no longer
  exists without failing the build. Run this after a version bump: it reads each `<access> <class> [<member>]` line of
  -At and checks it against -Jar (e.g. the NeoForm joined `raw.jar` of the target version):
  - MISSING CLASS  -> the class is not in the jar; same-simple-name classes are listed as candidates (moved package);
  - BAD METHOD     -> no method with that name AND descriptor; the methods with that name are listed;
  - BAD FIELD      -> no field with that name.
  Class-only and `*` entries only need the class. Exit code: 0 all entries resolve, 1 bad arguments, 2 problems found.

.EXAMPLE
  .\Find-StaleAccessTransformer.ps1 -At ..\..\src\main\resources\META-INF\accesstransformer.cfg `
    -Jar ..\..\build\neoForm\neoFormJoined1.21.11-20251209.172050\raw.jar
#>
param(
  [Parameter(Mandatory)] [string] $At,
  [Parameter(Mandatory)] [string] $Jar,
  # javap of a JDK that can read the jar's class files; defaults to $env:JAVA_HOME\bin\javap(.exe), then PATH
  [string] $Javap
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $At -PathType Leaf)) { Write-Error "AT file not found: $At" -ErrorAction Continue; exit 1 }
if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) { Write-Error "jar not found: $Jar" -ErrorAction Continue; exit 1 }
if (-not $Javap) {
  $fromHome = if ($env:JAVA_HOME) { Get-ChildItem -LiteralPath (Join-Path $env:JAVA_HOME 'bin') -Filter 'javap*' -File -ErrorAction SilentlyContinue | Select-Object -First 1 }
  $Javap = if ($fromHome) { $fromHome.FullName } else { (Get-Command javap -ErrorAction SilentlyContinue).Source }
}
if (-not $Javap) { Write-Error 'javap not found (set JAVA_HOME or pass -Javap)' -ErrorAction Continue; exit 1 }
$jarPath = (Resolve-Path -LiteralPath $Jar).Path

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try { $entries = @($zip.Entries | ForEach-Object { $_.FullName } | Where-Object { $_.EndsWith('.class') }) }
finally { $zip.Dispose() }
$set = [System.Collections.Generic.HashSet[string]]::new([string[]]$entries)

$cache = @{}
$n = 0; $checked = 0; $problems = 0
foreach ($line in Get-Content -LiteralPath $At) {
  $n++
  $t = ($line -replace '#.*$', '').Trim()
  if (-not $t) { continue }
  $checked++
  $parts = $t -split '\s+'
  $cls = $parts[1]; $mem = if ($parts.Count -gt 2) { $parts[2] } else { $null }
  if (-not $set.Contains(($cls -replace '\.', '/') + '.class')) {
    $simple = ($cls -split '[.$]')[-1]
    $cands = $entries | Where-Object { $_ -match "[/$]$([regex]::Escape($simple))\.class$" } | ForEach-Object { ($_ -replace '\.class$', '') -replace '/', '.' }
    "L$n MISSING CLASS $cls -> candidates: $(if ($cands) { $cands -join ', ' } else { '(none)' })"
    $problems++; continue
  }
  if (-not $mem -or $mem -eq '*' -or $mem -eq '*()') { continue }
  if (-not $cache.ContainsKey($cls)) { $cache[$cls] = @(& $Javap -cp $jarPath -p -s $cls 2>&1 | ForEach-Object { "$_" }) }
  $lines = $cache[$cls]
  if ($mem -match '^([^(]+)(\(.*)$') {
    $name = $Matches[1]; $desc = $Matches[2]
    $owner = ($cls -split '[.$]')[-1]
    $ok = $false
    for ($i = 0; $i -lt $lines.Count - 1; $i++) {
      $l = $lines[$i]
      $nameMatch = if ($name -eq '<init>') { $l -match ('[ .$]' + [regex]::Escape($owner) + '\(') } else { $l -match ('[ .]' + [regex]::Escape($name) + '\(') }
      if ($nameMatch -and $lines[$i + 1] -match 'descriptor:\s*(\S+)' -and $Matches[1] -eq $desc) { $ok = $true; break }
    }
    if (-not $ok) {
      $alts = ($lines | Where-Object { $_.Contains("$name(") } | ForEach-Object { $_.Trim() }) -join ' | '
      "L$n BAD METHOD $cls $mem -> have: $(if ($alts) { $alts } else { '(none)' })"
      $problems++
    }
  } elseif (-not ($lines | Where-Object { $_ -match ('[ .]' + [regex]::Escape($mem) + ';$') })) {
    "L$n BAD FIELD $cls $mem"
    $problems++
  }
}
"entries=$checked problems=$problems"
if ($problems -gt 0) { exit 2 }
exit 0
