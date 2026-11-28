<#
.SYNOPSIS
  Compiler-driven member renames: edits only the lines javac reports, using the member table of Find-ApiRenames.ps1.

.DESCRIPTION
  Thin wrapper around CompilerRenames.java (single-file Java program, needs Java 24+; JDK from JAVA_HOME or -Java).
  Inputs: a compile log (Gradle output of compileJava with the flow gate init script, so javac reports every
  attribution error - see COMMANDS.md; each error prints twice in a Gradle log, duplicates are ignored), the member table
  (maps/<version>-members.tsv) and the TARGET version's jar (supertype hierarchy: NeoForm raw.jar).
  It edits only:
    (a) 'cannot find symbol: method|variable X' (also in 'invalid method reference') at the reported line/column, when
        the location type or one of its supertypes (source + jar) has a rename X -> Y with a compatible arity;
        several fitting renames -> disambiguated by parameter types, else listed as Ambiguous;
    (b) 'method does not override or implement a method from a supertype': renames the declaration when a supertype
        method with that name and arity was renamed (refuses when the class already declares the new name);
    (m) 'moved' rows (overrides file 'moved <owner> <member> <replacement>'): at a 'cannot find symbol' caret whose
        location is <owner> (or a subtype) the identifier becomes <replacement> (mc.screen -> mc.gui.screen(),
        mc.setScreen(s) -> mc.gui.setScreen(s)); method references and assignments are reported instead;
  and reports (never edits):
    (c) "possible dead overrides": methods WITHOUT @Override whose name/arity matches a renamed or hinted supertype
        method that no longer exists (they compile and are never called - the renderBg trap);
    unresolved overrides (with the overrides file's 'hint' text when there is one), ambiguous spots.
  Default is a DRY RUN; -Apply rewrites (only the reported identifiers; encoding, BOM and line endings untouched).
  Loop: compile -> Invoke-CompilerRenames -Apply -> compile, until it reports edits=0.
  Last line: errors=<n> edits=<n> files=<n> ambiguous=<n> unresolvedOverrides=<n> deadOverrides=<n> moved=<n>.
  Exit code: 0 ok, 1 bad arguments.

.EXAMPLE
  pwsh -NoProfile -File Invoke-CompilerRenames.ps1 -Log $env:TEMP\compile.log -Members maps\26.1.2-members.tsv `
    -Jar <repo>\build\neoForm\neoFormJoined26.1.2-1\raw.jar -Root <repo>
.EXAMPLE
  # Tinkers: index Mantle's sources too (Tinkers classes extend Mantle classes). Any reported file under any -Root
  # can be edited, and a Tinkers compile log only reports Tinkers files.
  pwsh -NoProfile -File Invoke-CompilerRenames.ps1 -Log ... -Members ... -Jar ... -Root <tinkers>,<repo> -Apply
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)] [string] $Log,
  [Parameter(Mandatory)] [string] $Members,
  [Parameter(Mandatory)] [string] $Jar,
  [Parameter(Mandatory)] [string[]] $Root,
  [switch] $Apply,
  [string] $Java
)
$ErrorActionPreference = 'Stop'
if (-not $Java) {
  if (-not $env:JAVA_HOME) { Write-Host 'ERROR: set JAVA_HOME to a JDK 24+ (or pass -Java)'; exit 1 }
  $Java = Join-Path $env:JAVA_HOME 'bin/java'
}
$roots = @($Root | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$javaArgs = @((Join-Path $PSScriptRoot 'CompilerRenames.java'), '--log', $Log, '--members', $Members, '--jar', $Jar, '--root', ($roots -join ','))
if ($Apply) { $javaArgs += '--apply' }
& $Java @javaArgs
exit $LASTEXITCODE
