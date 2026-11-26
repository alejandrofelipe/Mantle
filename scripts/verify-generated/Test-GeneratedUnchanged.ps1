<#
.SYNOPSIS
  Datagen golden diff: fails when the generated resources of a repo differ from what git has committed.

.DESCRIPTION
  Run it right after a datagen run (runData and/or runServerData). It lists every content change against HEAD under the
  generated folders (modified, added/staged, deleted, renamed as delete + add, and untracked files; gitignored files such
  as Tinkers' local src/generated/resources/.cache are not seen) and fails when any changed path matches none of the
  -Allow globs. Files that differ from the checkout only in line endings (core.autocrlf CRLF checkout vs LF datagen
  output) are not changes; their count is printed as a note.

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

function Split-Nul($output) {
  return @((($output | Out-String) -split "`0") | ForEach-Object { $_.TrimStart("`r", "`n") } | Where-Object { $_.Trim() })
}

# content changes of tracked files (staged or not) against HEAD, after git's eol normalization: a file that only differs
# in its checkout line endings (core.autocrlf without an eol attribute, e.g. Tinkers' .mcmeta) is not a change.
# --no-renames reports a rename as a deletion plus an addition. With -z the output alternates status and path.
$changes = New-Object System.Collections.Generic.List[object]
$null = & git -C $Repo rev-parse --verify --quiet HEAD 2>$null
$hasHead = $LASTEXITCODE -eq 0
if ($hasHead) {
  $diff = Split-Nul (& git -C $Repo -c core.quotepath=false -c core.safecrlf=false diff HEAD --name-status --no-renames -z -- @folders 2>$null)
  if ($LASTEXITCODE -ne 0) {
    Write-Host "git diff failed in $Repo"
    exit 2
  }
  for ($i = 0; $i + 1 -lt $diff.Count; $i += 2) {
    $changes.Add([pscustomobject]@{ Status = $diff[$i].Trim(); Path = $diff[$i + 1] })
  }
}
# untracked files (gitignored ones excluded); without a HEAD every file counts as new
$untrackedArgs = if ($hasHead) { @('--others', '--exclude-standard') } else { @('--others', '--cached', '--exclude-standard') }
foreach ($path in Split-Nul (& git -C $Repo -c core.quotepath=false ls-files @untrackedArgs -z -- @folders)) {
  $changes.Add([pscustomobject]@{ Status = '??'; Path = $path })
}

# files git status flags whose content is unchanged (line endings only): reported for information
$changedPaths = [System.Collections.Generic.HashSet[string]]::new([string[]]@($changes | ForEach-Object { $_.Path }))
$eolOnly = @(Split-Nul (& git -C $Repo -c core.quotepath=false -c core.safecrlf=false status --porcelain=v1 -z --untracked-files=no --no-renames -- @folders 2>$null) |
  Where-Object { $_.Length -gt 3 } | ForEach-Object { $_.Substring(3) } | Where-Object { -not $changedPaths.Contains($_) })

$allowed = @()
$unexpected = @()
foreach ($change in $changes) {
  $match = $allowGlobs | Where-Object { $change.Path -like $_ } | Select-Object -First 1
  if ($match) { $allowed += $change } else { $unexpected += $change }
}

$scope = ($folders -join ', ')
if ($eolOnly.Count -gt 0) {
  Write-Host "note: $($eolOnly.Count) file(s) under $scope differ from the checkout only in line endings (content identical to HEAD), not counted; e.g. $($eolOnly[0])"
}
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
