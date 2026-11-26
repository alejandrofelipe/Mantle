<#
.SYNOPSIS
  Self-contained tests for Find-StaleAccessTransformer.ps1 (no Pester). Compiles a fixture class with the JDK in
  JAVA_HOME, jars it, runs the tool against good and stale AT files, prints PASS/FAIL and exits nonzero on any failure.

.EXAMPLE
  pwsh -NoProfile -File Test-FindStaleAccessTransformer.ps1
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$tool = Join-Path $PSScriptRoot 'Find-StaleAccessTransformer.ps1'
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("at-check-test-" + [guid]::NewGuid().ToString('N'))
$script:failures = 0
function Assert-Case([string] $name, [bool] $ok, [string] $detail = '') {
  if ($ok) { Write-Host "PASS  $name" }
  else { $script:failures++; Write-Host "FAIL  $name $(if ($detail) { "-- $detail" })" }
}
function Invoke-Tool([string] $at, [string] $jar) {
  $out = & pwsh -NoProfile -File $tool -At $at -Jar $jar 2>&1 | Out-String
  [pscustomobject]@{ Exit = $LASTEXITCODE; Out = $out }
}

try {
  if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to a JDK (javac + javap)' }
  $javac = Get-ChildItem -LiteralPath (Join-Path $env:JAVA_HOME 'bin') -Filter 'javac*' -File | Select-Object -First 1
  $src = Join-Path $tmp 'src/a/b'; $classes = Join-Path $tmp 'classes'
  New-Item -ItemType Directory -Force $src, $classes | Out-Null
  Set-Content -LiteralPath (Join-Path $src 'Owner.java') -Value @'
package a.b;
public class Owner {
  private int count;
  private Owner(String name) {}
  private void run(int times, String label) {}
  private static class Inner { private boolean flag; }
}
'@
  & $javac.FullName -d $classes (Join-Path $src 'Owner.java')
  if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
  $jar = Join-Path $tmp 'fixture.jar'
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::CreateFromDirectory($classes, $jar)

  $good = Join-Path $tmp 'good.cfg'
  Set-Content -LiteralPath $good -Value @(
    '# comment line', '',
    'public a.b.Owner',
    'public a.b.Owner count # field',
    'public a.b.Owner <init>(Ljava/lang/String;)V',
    'public a.b.Owner run(ILjava/lang/String;)V',
    'public a.b.Owner$Inner',
    'public a.b.Owner$Inner flag',
    'public a.b.Owner *()')
  $r = Invoke-Tool $good $jar
  Assert-Case 'valid entries exit 0' ($r.Exit -eq 0) "exit=$($r.Exit) $($r.Out)"
  Assert-Case 'valid entries counted, comments skipped' ($r.Out -match 'entries=7 problems=0') $r.Out

  $stale = Join-Path $tmp 'stale.cfg'
  Set-Content -LiteralPath $stale -Value @(
    'public a.c.Owner',
    'public a.b.Owner missing',
    'public a.b.Owner run(I)V',
    'public a.b.Owner gone(I)V',
    'public a.b.Owner <init>()V',
    'public a.b.Owner count')
  $r = Invoke-Tool $stale $jar
  Assert-Case 'stale entries exit 2' ($r.Exit -eq 2) "exit=$($r.Exit)"
  Assert-Case 'moved class lists the candidate' ($r.Out -match 'L1 MISSING CLASS a\.c\.Owner -> candidates: a\.b\.Owner') $r.Out
  Assert-Case 'missing field' ($r.Out -match 'L2 BAD FIELD a\.b\.Owner missing') $r.Out
  Assert-Case 'wrong descriptor lists the real method' ($r.Out -match 'L3 BAD METHOD a\.b\.Owner run\(I\)V -> have: .*run\(int, java\.lang\.String\)') $r.Out
  Assert-Case 'missing method' ($r.Out -match 'L4 BAD METHOD a\.b\.Owner gone\(I\)V -> have: \(none\)') $r.Out
  Assert-Case 'wrong constructor descriptor' ($r.Out -match 'L5 BAD METHOD a\.b\.Owner <init>\(\)V') $r.Out
  Assert-Case 'valid line in a stale file is not reported' ($r.Out -notmatch 'L6 ') $r.Out
  Assert-Case 'problem count' ($r.Out -match 'entries=6 problems=5') $r.Out

  $r = Invoke-Tool (Join-Path $tmp 'nope.cfg') $jar
  Assert-Case 'missing AT file exits 1' ($r.Exit -eq 1) "exit=$($r.Exit)"
} finally {
  Remove-Item -Recurse -Force -LiteralPath $tmp -ErrorAction SilentlyContinue
}
if ($script:failures -gt 0) { Write-Host "$($script:failures) failure(s)"; exit 1 }
Write-Host 'all passed'
