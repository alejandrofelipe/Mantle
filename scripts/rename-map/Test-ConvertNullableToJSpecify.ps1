<#
.SYNOPSIS
  Self-contained tests for Convert-NullableToJSpecify.ps1 (no Pester). Builds fixtures in a temp dir, runs the tool
  in a child pwsh, prints PASS/FAIL per case and exits nonzero on any failure.

.EXAMPLE
  pwsh -NoProfile -File Test-ConvertNullableToJSpecify.ps1
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$tool = Join-Path $PSScriptRoot 'Convert-NullableToJSpecify.ps1'
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("convert-nullable-test-" + [guid]::NewGuid().ToString('N'))
$script:failures = 0

function Assert-Case([string] $name, [bool] $ok, [string] $detail = '') {
  if ($ok) { Write-Host "PASS  $name" }
  else { $script:failures++; Write-Host "FAIL  $name $(if ($detail) { "-- $detail" })" }
}
function Invoke-Tool([string] $root, [switch] $Apply) {
  $toolArgs = @('-NoProfile', '-File', $tool, '-Root', $root)
  if ($Apply) { $toolArgs += '-Apply' }
  $out = & pwsh @toolArgs 2>&1 | Out-String -Width 400
  [pscustomobject]@{ Exit = $LASTEXITCODE; Out = $out }
}
$utf8 = [System.Text.UTF8Encoding]::new($false)
function New-Fixture([string] $root, [string] $rel, [string[]] $lines, [string] $eol = "`n", [bool] $bom = $false) {
  $path = Join-Path $root $rel
  New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
  $bytes = $utf8.GetBytes(($lines -join $eol) + $eol)
  if ($bom) { $bytes = [byte[]](0xEF, 0xBB, 0xBF) + $bytes }
  [System.IO.File]::WriteAllBytes($path, $bytes)
  $path
}
function Get-Text([string] $path) { $utf8.GetString([System.IO.File]::ReadAllBytes($path)).TrimStart([char]0xFEFF) }
function Get-Sha([string] $path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
function Test-Has([string] $text, [string] $literal) { $text.Contains($literal) }

try {
  $root = Join-Path $tmp 'repo'
  $pkg = 'src/main/java/demo'
  $null = New-Fixture $root "$pkg/package-info.java" @('@NullMarked', 'package demo;', '', 'import org.jspecify.annotations.NullMarked;')
  $placement = @(
    'package demo;'
    ''
    'import java.util.Map;'
    'import javax.annotation.Nullable;'
    ''
    '/** Mentions @Nullable Map.Entry in javadoc and {@link Nullable}. */'
    'public class Placement {'
    '  @Nullable'
    '  private static Map.Entry<String, Integer> entry;'
    '  @Nullable private java.util.List<String> fqn;'
    '  @Nullable'
    '  @Deprecated(since = "1")'
    '  public static <T extends Map.Entry<T, T>> Map.Entry<T, T> get() { return null; }'
    '  @Nullable String[] names;'
    '  @Nullable Map.Entry<String, String>[] entries;'
    '  @Nullable int[][] grid;'
    '  @Nullable String plain;'
    '  void m(@Nullable final Map.Entry<String, String> param, @Nullable Object... rest) {'
    '    String s = "@Nullable Map.Entry in a string";'
    '    // @Nullable Map.Entry in a comment'
    '  }'
    '  @Nullable int primitive() { return 0; }'
    '  @Nullable Boolean boxed;'
    '  @Nullable Class<?> type;'
    '}'
  )
  $crlf = New-Fixture $root "$pkg/Placement.java" $placement "`r`n"
  $lf = New-Fixture $root "$pkg/PlacementLf.java" ($placement -replace 'class Placement', 'class PlacementLf') "`n" $true
  $nonnull = New-Fixture $root "$pkg/Lomboked.java" @(
    'package demo;'
    ''
    'import lombok.Getter;'
    'import lombok.RequiredArgsConstructor;'
    ''
    'import javax.annotation.Nonnull;'
    ''
    '@RequiredArgsConstructor'
    'public class Lomboked {'
    '  @Getter @Nonnull'
    '  private final Object field;'
    '  @Nonnull'
    '  @Override'
    '  public String toString() { return ""; }'
    '  void m(@Nonnull Object a, int b) {}'
    '}'
  )
  $plainNonnull = New-Fixture $root "$pkg/Plain.java" @(
    'package demo;'
    ''
    'import java.util.List;'
    ''
    'import javax.annotation.Nonnull;'
    ''
    'public class Plain {'
    '  @Nonnull @Deprecated'
    '  private Object field = new Object();'
    '  @Nonnull'
    '  public Object get() { return field; }'
    '}'
  )
  $check = New-Fixture $root "$pkg/Check.java" @(
    'package demo;'
    ''
    'import javax.annotation.CheckForNull;'
    'import javax.annotation.Nullable;'
    'import javax.annotation.OverridingMethodsMustInvokeSuper;'
    ''
    'public class Check {'
    '  @CheckForNull'
    '  public Map.Entry<String, String> a() { return null; }'
    '  @Nullable String b;'
    '  @OverridingMethodsMustInvokeSuper void c() {}'
    '  void d(@javax.annotation.Nullable Object o) {}'
    '}'
  )
  $onlyCheck = New-Fixture $root "$pkg/OnlyCheck.java" @(
    'package demo;'
    ''
    'import javax.annotation.CheckForNull;'
    ''
    'public class OnlyCheck {'
    '  @CheckForNull Object a() { return null; }'
    '}'
  )
  $already = New-Fixture $root "$pkg/Already.java" @(
    'package demo;'
    ''
    'import org.jspecify.annotations.Nullable;'
    'import javax.annotation.Nullable;'
    ''
    'public class Already { @Nullable String a; }'
  )
  $fqnOnly = New-Fixture $root "$pkg/FqnOnly.java" @('package demo;', 'class FqnOnly { void d(@javax.annotation.Nullable Object o) {} }')
  $untouched = New-Fixture $root "$pkg/Untouched.java" @('package demo;', 'import org.jetbrains.annotations.Nullable;', 'class Untouched { @Nullable String[] a; }')
  $otherPkg = Join-Path $root 'src/main/java/other'
  $noMarked = New-Fixture $root 'src/main/java/other/NoMarked.java' @('package other;', '', 'import javax.annotation.Nonnull;', '', 'class NoMarked { void m(@Nonnull Object o) {} }')
  $leftover = New-Fixture $root 'src/main/java/legacy/package-info.java' @(
    '@NullMarked'
    '@ParametersAreNonnullByDefault'
    '@MethodsReturnNonnullByDefault'
    'package legacy;'
    ''
    'import javax.annotation.ParametersAreNonnullByDefault;'
    'import net.minecraft.MethodsReturnNonnullByDefault;'
    'import org.jspecify.annotations.NullMarked;'
  )

  $all = @($crlf, $lf, $nonnull, $plainNonnull, $check, $onlyCheck, $already, $fqnOnly, $untouched, $noMarked, $leftover)
  $before = @{}; foreach ($p in $all) { $before[$p] = Get-Sha $p }

  # --- dry run ---
  $dry = Invoke-Tool $root
  Assert-Case 'dry run exits 0' ($dry.Exit -eq 0) "exit=$($dry.Exit) $($dry.Out)"
  Assert-Case 'dry run changes nothing on disk' (@($all | Where-Object { (Get-Sha $_) -ne $before[$_] }).Count -eq 0)
  # 10 changed files (all but Untouched); per Placement file: 4 qualified + 3 arrays
  $summary = ($dry.Out -split "`n" | Select-String 'files=' | Out-String).Trim()
  Assert-Case 'dry run summary line' ($dry.Out -match 'files=12 changed=10 imports=11 checkForNull=2 nonnullRemoved=5 nonnullLombok=1 qualified=9 arrays=6 fqn=2 ') $summary
  Assert-Case 'dry run lists the rewrites with file:line' ($dry.Out -match 'QUALIFIED\s+src/main/java/demo/Placement\.java:8\s+@Nullable private static Map\.Entry<String, Integer>\s+->\s+private static Map\.@Nullable Entry<String, Integer>') ''
  Assert-Case 'dry run notes varargs, primitive, generic-free keyword cases' ($dry.Out -match 'VARARGS\s+src/main/java/demo/Placement\.java:18' -and $dry.Out -match 'PRIMITIVE\s+src/main/java/demo/Placement\.java:22' -and $dry.Out -notmatch 'Placement\.java:2[34]') ''
  Assert-Case 'dry run reports a kept javax annotation' ($dry.Out -match 'OTHER-JAVAX\s+src/main/java/demo/Check\.java:\d+\s+import javax\.annotation\.OverridingMethodsMustInvokeSuper;') ''
  Assert-Case 'dry run reports @Nonnull outside @NullMarked' ($dry.Out -match 'NO-NULLMARKED\s+src/main/java/other/NoMarked\.java') ''
  Assert-Case 'dry run reports the Lombok field' ($dry.Out -match 'NONNULL-LOMBOK\s+src/main/java/demo/Lomboked\.java:10') ''

  # --- apply ---
  $app = Invoke-Tool $root -Apply
  Assert-Case 'apply exits 0 and writes 10 files' ($app.Exit -eq 0 -and $app.Out -match 'written=10') "exit=$($app.Exit)"
  Assert-Case 'jetbrains @Nullable file untouched' ((Get-Sha $untouched) -eq $before[$untouched])

  $t = Get-Text $crlf
  Assert-Case 'import swapped' ((Test-Has $t "import java.util.Map;`r`nimport org.jspecify.annotations.Nullable;`r`n`r`n/**") -and -not (Test-Has $t 'javax'))
  Assert-Case 'qualified on its own line merges into the declaration' (Test-Has $t "  private static Map.@Nullable Entry<String, Integer> entry;")
  Assert-Case 'package-qualified type' (Test-Has $t '  private java.util.@Nullable List<String> fqn;')
  Assert-Case 'qualified return type after annotation and method type parameters' (Test-Has $t "  @Deprecated(since = `"1`")`r`n  public static <T extends Map.Entry<T, T>> Map.@Nullable Entry<T, T> get()")
  Assert-Case 'array: nullable array, not elements' (Test-Has $t '  String @Nullable [] names;')
  Assert-Case 'array of a qualified generic type' (Test-Has $t '  Map.Entry<String, String> @Nullable [] entries;')
  Assert-Case 'multi-dimensional primitive array' (Test-Has $t '  int @Nullable [][] grid;')
  Assert-Case 'simple type unchanged' (Test-Has $t '  @Nullable String plain;')
  Assert-Case 'parameter with final modifier' (Test-Has $t 'void m(final Map.@Nullable Entry<String, String> param, @Nullable Object... rest)')
  Assert-Case 'javadoc, comment and string untouched' ((Test-Has $t '/** Mentions @Nullable Map.Entry in javadoc and {@link Nullable}. */') -and (Test-Has $t '"@Nullable Map.Entry in a string"') -and (Test-Has $t '// @Nullable Map.Entry in a comment'))
  Assert-Case 'boxed and Class types unchanged' ((Test-Has $t '  @Nullable Boolean boxed;') -and (Test-Has $t '  @Nullable Class<?> type;'))
  $bytes = [System.IO.File]::ReadAllBytes($crlf)
  Assert-Case 'CRLF kept, no BOM added' ($bytes[0] -eq [byte][char]'p' -and ($utf8.GetString($bytes) -replace "`r`n", '') -notmatch "`n")
  $lfBytes = [System.IO.File]::ReadAllBytes($lf)
  Assert-Case 'LF kept and BOM kept' ($lfBytes[0] -eq 0xEF -and $lfBytes[3] -eq [byte][char]'p' -and $utf8.GetString($lfBytes) -notmatch "`r")

  $t = Get-Text $nonnull
  Assert-Case 'Lombok field keeps a null check via lombok.NonNull' ((Test-Has $t "  @Getter @NonNull`n  private final Object field;") -and (Test-Has $t "import lombok.RequiredArgsConstructor;`nimport lombok.NonNull;`n"))
  Assert-Case '@Nonnull own line removed, parameter annotation removed' ((Test-Has $t "  private final Object field;`n") -and (Test-Has $t "  @Override`n  public String toString()") -and (Test-Has $t 'void m(Object a, int b)'))
  Assert-Case 'javax.annotation.Nonnull import dropped without a double blank line' (-not (Test-Has $t 'javax') -and -not (Test-Has $t "`n`n`n"))

  $t = Get-Text $plainNonnull
  Assert-Case 'no Lombok generator: field @Nonnull removed, not replaced' ((Test-Has $t "  @Deprecated`n  private Object field") -and -not (Test-Has $t 'NonNull') -and (Test-Has $t "  public Object get()"))
  Assert-Case 'import group of only Nonnull removed cleanly' (Test-Has $t "import java.util.List;`n`npublic class Plain")

  $t = Get-Text $check
  Assert-Case 'CheckForNull -> Nullable, placement applied to it' (Test-Has $t "  public Map.@Nullable Entry<String, String> a()")
  Assert-Case 'single jspecify import for CheckForNull + Nullable, other javax kept' ((Test-Has $t "import org.jspecify.annotations.Nullable;`nimport javax.annotation.OverridingMethodsMustInvokeSuper;`n") -and ([regex]::Matches($t, 'jspecify').Count -eq 1))
  Assert-Case 'FQN @javax.annotation.Nullable -> @Nullable when the file has the import' (Test-Has $t 'void d(@Nullable Object o)')
  Assert-Case 'FQN without the import -> JSpecify FQN' (Test-Has (Get-Text $fqnOnly) 'void d(@org.jspecify.annotations.Nullable Object o)')
  $t = Get-Text $onlyCheck
  Assert-Case 'only CheckForNull: import becomes the jspecify one' ((Test-Has $t 'import org.jspecify.annotations.Nullable;') -and (Test-Has $t '@Nullable Object a()'))
  $t = Get-Text $already
  Assert-Case 'existing jspecify import: javax import dropped' (([regex]::Matches($t, 'import ').Count -eq 1) -and (Test-Has $t 'import org.jspecify.annotations.Nullable;'))
  $t = Get-Text $noMarked
  Assert-Case '@Nonnull removed outside @NullMarked too (reported)' ((Test-Has $t 'void m(Object o)') -and -not (Test-Has $t 'javax'))
  $t = Get-Text $leftover
  Assert-Case 'package-info leftovers removed under @NullMarked' ($t -eq "@NullMarked`npackage legacy;`n`nimport org.jspecify.annotations.NullMarked;`n") $t

  $again = Invoke-Tool $root -Apply
  Assert-Case 'second apply is a no-op' ($again.Exit -eq 0 -and $again.Out -match 'written=0' -and $again.Out -match 'changed=0') ($again.Out -split "`n" | Select-String 'files=|written' | Out-String)

  $r = Invoke-Tool (Join-Path $tmp 'missing')
  Assert-Case 'missing root exits 1' ($r.Exit -eq 1)
}
finally {
  Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
if ($script:failures -gt 0) { Write-Host "$script:failures test(s) FAILED"; exit 1 }
Write-Host 'All tests PASSED'
exit 0
