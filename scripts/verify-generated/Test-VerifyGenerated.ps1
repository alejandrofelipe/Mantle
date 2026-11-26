<#
.SYNOPSIS
  Self-test of Test-GeneratedUnchanged.ps1 against a throwaway git repo in the temp folder.

.DESCRIPTION
  Builds a temp repo with committed files under src/generated and src/main, then runs the checker (in a child pwsh,
  for real exit codes) for each case and compares the exit code. Exits 0 when every case passes, 1 otherwise.
#>
$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'Test-GeneratedUnchanged.ps1'
$shell = (Get-Process -Id $PID).Path
$root = Join-Path ([IO.Path]::GetTempPath()) ("verify-generated-selftest-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $root | Out-Null

function Invoke-Git {
  & git -C $root @args 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "git $args failed" }
}

function Set-File([string]$relative, [string]$content) {
  $path = Join-Path $root $relative
  New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
  Set-Content -LiteralPath $path -Value $content -NoNewline
}

function Reset-Repo {
  Invoke-Git reset --hard --quiet
  Invoke-Git clean -fdq
}

$failures = 0
function Test-Case([string]$name, [int]$expected, [string[]]$extraArgs = @()) {
  $output = & $shell -NoProfile -NonInteractive -File $checker -Repo $root @extraArgs 2>&1
  $code = $LASTEXITCODE
  if ($code -eq $expected) {
    Write-Host "PASS  $name (exit $code)"
  } else {
    Write-Host "FAIL  ${name}: expected exit $expected, got $code"
    $output | ForEach-Object { Write-Host "      $_" }
    $script:failures++
  }
}

try {
  Invoke-Git init --quiet
  Invoke-Git config user.email selftest@example.invalid
  Invoke-Git config user.name selftest
  Invoke-Git config core.autocrlf false
  Set-File 'src/generated/resources/data/test/a.json' '{"a":1}'
  Set-File 'src/generated/resources/assets/test/b.json' '{"b":1}'
  Set-File 'src/main/java/Main.java' 'class Main {}'
  Set-File '.gitignore' 'src/generated/resources/.cache/*'
  Invoke-Git add -A
  Invoke-Git commit --quiet -m init

  Test-Case 'clean tree passes' 0

  Set-File 'src/generated/resources/data/test/a.json' '{"a":2}'
  Test-Case 'modified generated file fails' 1
  Test-Case 'modified generated file allowed by exact path passes' 0 @('-Allow', 'src/generated/resources/data/test/a.json')
  Test-Case 'modified generated file allowed by glob passes' 0 @('-Allow', 'src/generated/resources/data/*')
  Test-Case 'glob for another folder does not allow it' 1 @('-Allow', 'src/generated/resources/assets/*')
  Reset-Repo

  Set-File 'src/generated/resources/data/test/new.json' '{}'
  Test-Case 'untracked generated file fails' 1
  Test-Case 'untracked generated file allowed (comma list) passes' 0 @('-Allow', 'src/generated/resources/assets/*,src/generated/resources/data/test/new.json')
  Reset-Repo

  Remove-Item -LiteralPath (Join-Path $root 'src/generated/resources/assets/test/b.json')
  Test-Case 'deleted generated file fails' 1
  Reset-Repo

  Set-File 'src/generated/resources/data/test/staged.json' '{}'
  Invoke-Git add -A
  Test-Case 'staged new generated file fails' 1
  Reset-Repo

  Set-File 'src/main/java/Main.java' 'class Main { int x; }'
  Set-File 'src/main/java/Other.java' 'class Other {}'
  Test-Case 'changes outside the generated folder are ignored' 0
  Reset-Repo

  Set-File 'src/generated/resources/.cache/provider' '// cache'
  Test-Case 'gitignored generated file is ignored' 0
  Reset-Repo

  $output =& $shell -NoProfile -NonInteractive -File $checker -Repo (Join-Path $root 'does-not-exist') 2>&1
  if ($LASTEXITCODE -eq 2) { Write-Host "PASS  nonexistent repo folder (exit 2)" } else { Write-Host "FAIL  nonexistent repo folder: exit $LASTEXITCODE"; $failures++ }
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) {
  Write-Host "$failures self-test case(s) failed"
  exit 1
}
Write-Host 'All self-test cases passed'
exit 0
