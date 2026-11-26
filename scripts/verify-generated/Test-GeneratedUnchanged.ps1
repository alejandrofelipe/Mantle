<#
.SYNOPSIS
  Datagen golden diff: fails when the generated resources of a repo differ from what git has committed.

.DESCRIPTION
  Run it right after a datagen run (runData and/or runServerData). It lists every change git sees under the generated
  folders (modified, added, deleted, renamed, type-changed and untracked files; gitignored files such as Tinkers'
  local src/generated/resources/.cache are not seen) and fails when any changed path matches none of the -Allow globs.

  Exit codes: 0 = no unexpected change, 1 = unexpected changes (listed), 2 = usage error (not a git repo, git missing).

.PARAMETER Repo
  Path to the git work tree root (the folder holding src/). Paths are matched relative to it, with forward slashes.

.PARAMETER Allow
  Globs (PowerShell -like syntax: * matches any characters, slashes included; ? one character) of paths that are
  expected to change in this step, e.g. 'src/generated/resources/data/tconstruct/recipe/*'. Accepts an array or one
  comma-separated string (what `pwsh -File ... -Allow a,b` passes).

.PARAMETER Generated
  Generated folders to check, relative to -Repo. Default: src/generated (both Mantle and Tinkers write their datagen
  output to src/generated/resources).

.EXAMPLE
  ./Test-GeneratedUnchanged.ps1 -Repo C:\dev\tinkers
.EXAMPLE
  ./Test-GeneratedUnchanged.ps1 -Repo C:\dev\repo -Allow 'src/generated/resources/assets/mantle/models/*'
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$Repo,
  [string[]]$Allow = @(),
  [string[]]$Generated = @('src/generated')
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
  Write-Error 'git not found on PATH'
  exit 2
}
if (-not (Test-Path -LiteralPath $Repo -PathType Container)) {
  Write-Host "Repo folder not found: $Repo"
  exit 2
}
$Repo = (Resolve-Path -LiteralPath $Repo).Path
$null = & git -C $Repo rev-parse --show-toplevel 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host "Not a git work tree: $Repo"
  exit 2
}

# one comma-separated string or an array, empty entries dropped
$allowGlobs = @($Allow | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
$folders = @($Generated | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim().Replace('\', '/').TrimEnd('/') } | Where-Object { $_ })

foreach ($folder in $folders) {
  if (-not (Test-Path -LiteralPath (Join-Path $Repo $folder))) {
    Write-Host "warning: generated folder '$folder' does not exist in $Repo"
  }
}

# porcelain v1 with NUL separators: "XY path" entries; renames and copies are followed by the original path
$raw = & git -C $Repo -c core.quotepath=false status --porcelain=v1 -z --untracked-files=all -- @folders
if ($LASTEXITCODE -ne 0) {
  Write-Host "git status failed in $Repo"
  exit 2
}
$tokens = @((($raw | Out-String) -split "`0") | Where-Object { $_.Trim() })

$changes = New-Object System.Collections.Generic.List[object]
for ($i = 0; $i -lt $tokens.Count; $i++) {
  $token = $tokens[$i].TrimStart("`r", "`n")
  if ($token.Length -lt 4) { continue }
  $status = $token.Substring(0, 2)
  $path = $token.Substring(3)
  $changes.Add([pscustomobject]@{ Status = $status; Path = $path })
  if ($status[0] -eq 'R' -or $status[0] -eq 'C') {
    $i++
    if ($i -lt $tokens.Count) {
      $changes.Add([pscustomobject]@{ Status = 'R-'; Path = $tokens[$i] })
    }
  }
}

$allowed = @()
$unexpected = @()
foreach ($change in $changes) {
  $match = $allowGlobs | Where-Object { $change.Path -like $_ } | Select-Object -First 1
  if ($match) { $allowed += $change } else { $unexpected += $change }
}

$scope = ($folders -join ', ')
if ($allowed.Count -gt 0) {
  Write-Host "$($allowed.Count) expected change(s) under $scope (matched -Allow):"
  $allowed | ForEach-Object { Write-Host "  $($_.Status) $($_.Path)" }
}
if ($unexpected.Count -gt 0) {
  Write-Host "FAIL: $($unexpected.Count) unexpected change(s) under $scope in $Repo"
  $unexpected | ForEach-Object { Write-Host "  $($_.Status) $($_.Path)" }
  Write-Host "Review them (git -C `"$Repo`" diff -- $scope); pass -Allow <glob,...> for changes this step intends."
  exit 1
}
Write-Host "OK: no unexpected change under $scope in $Repo"
exit 0
