<#
.SYNOPSIS
  Diffs the API of two Mojang-named Minecraft jars (e.g. the NeoForm raw.jar of two versions) and writes the rename map
  (class rules) and the member-rename table for a milestone.

.DESCRIPTION
  Thin wrapper around FindApiRenames.java (single-file Java program, JDK ClassFile API, needs Java 24+; run with the
  JDK in JAVA_HOME, or -Java). The program writes into -Out:
    classes.tsv   old -> new class pairs and how each was matched (override / moved / inner-classes / nested-classes /
                  outer-renamed / similarity[-xpkg] / shape-over-name / moved-split / moved-low-shape)
    members.tsv   method/field renames inside class pairs: kind, old/new owner, old/new name, old/new descriptor, static,
                  how (desc-unique / name-tiebreak(score) / override) + 'hint' rows from the overrides file
    map.txt       rename-map rules for Invoke-RenameMap.ps1 ('field' rules for moved static fields and constants folded
                  into collections, 'class' rules, the overrides file's 'rule' lines); 'moved' lines go to members.tsv
    changed.tsv   members whose name survived with another descriptor/type (same-name caller review)
    warnings.txt  "same simple name, different shape" / "same FQN, different shape" (semantic swaps such as Material)
    removed.tsv   classes and members that vanished without a candidate (input for the manual part of a port)
  -MapOut / -MembersOut copy map.txt / members.tsv to their committed place (maps/<version>.txt,
  maps/<version>-members.tsv). Exit code: the program's (0 ok, 1 bad arguments, 2 bad overrides file).

.EXAMPLE
  pwsh -NoProfile -File Find-ApiRenames.ps1 -OldJar <1.21.11 raw.jar> -NewJar <26.1.2 raw.jar> `
    -Overrides maps\26.1.2.overrides.txt -Out $env:TEMP\apidiff -MapOut maps\26.1.2.txt -MembersOut maps\26.1.2-members.tsv `
    -Title "MC 1.21.11 -> MC 26.1.2"
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)] [string] $OldJar,
  [Parameter(Mandatory)] [string] $NewJar,
  [Parameter(Mandatory)] [string] $Out,
  [string] $Overrides,
  [string] $MapOut,
  [string] $MembersOut,
  [string] $Title,
  # Class-name prefixes (internal form) to compare.
  [string] $Prefix = 'net/minecraft/,com/mojang/',
  # 'old,new' class pairs (dotted) whose similarity features to print; ';' separates pairs.
  [string] $Explain,
  [string] $Java
)
$ErrorActionPreference = 'Stop'
foreach ($p in @($OldJar, $NewJar) + @(if ($Overrides) { $Overrides })) {
  if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { Write-Host "ERROR: not found: $p"; exit 1 }
}
if (-not $Java) {
  if (-not $env:JAVA_HOME) { Write-Host 'ERROR: set JAVA_HOME to a JDK 24+ (or pass -Java)'; exit 1 }
  $Java = Join-Path $env:JAVA_HOME 'bin/java'
}
$javaArgs = @((Join-Path $PSScriptRoot 'FindApiRenames.java'), '--old', $OldJar, '--new', $NewJar, '--out', $Out, '--prefix', $Prefix)
if ($Overrides) { $javaArgs += @('--overrides', $Overrides) }
if ($Title) { $javaArgs += @('--title', $Title) }
if ($Explain) { $javaArgs += @('--explain', $Explain) }
& $Java @javaArgs
$code = $LASTEXITCODE
if ($code -ne 0) { exit $code }
if ($MapOut) { Copy-Item -LiteralPath (Join-Path $Out 'map.txt') -Destination $MapOut -Force; Write-Host "map     -> $MapOut" }
if ($MembersOut) { Copy-Item -LiteralPath (Join-Path $Out 'members.tsv') -Destination $MembersOut -Force; Write-Host "members -> $MembersOut" }
exit 0
