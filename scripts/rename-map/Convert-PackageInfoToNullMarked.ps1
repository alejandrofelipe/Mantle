<#
.SYNOPSIS
  MC 1.21.11 (M6, decision D-N1): rewrites package-info.java files from the jsr305/vanilla pair
  @ParametersAreNonnullByDefault + @MethodsReturnNonnullByDefault (the vanilla annotation was removed in 1.21.11)
  to JSpecify @NullMarked, which is what vanilla and NeoForge use now (same meaning: non-null unless @Nullable).

.DESCRIPTION
  For every src/**/package-info.java under -Root:
  - the first of the two annotation lines becomes @NullMarked, the other one is dropped;
  - import javax.annotation.ParametersAreNonnullByDefault; becomes import org.jspecify.annotations.NullMarked;
    and import net.minecraft.MethodsReturnNonnullByDefault; is dropped.
  Other lines (javadoc, @Deprecated, ...) are kept. Encoding (BOM or not) and line endings are preserved.
  Dry run by default; -Apply writes the files. Prints a summary line files=<n> changed=<n> skipped=<n>.
#>
param(
  [Parameter(Mandatory)] [string] $Root,
  [switch] $Apply
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Write-Error "No such folder: $Root"; exit 1 }

$annotations = @('@ParametersAreNonnullByDefault', '@MethodsReturnNonnullByDefault')
$importSwap = 'import javax.annotation.ParametersAreNonnullByDefault;'
$importDrop = 'import net.minecraft.MethodsReturnNonnullByDefault;'
$utf8 = [System.Text.UTF8Encoding]::new($false, $true)

$files = Get-ChildItem -LiteralPath (Join-Path $Root 'src') -Recurse -Filter 'package-info.java' -File
$changed = 0; $skipped = 0
foreach ($file in $files) {
  $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
  $bom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
  $text = $utf8.GetString($bytes, ($bom ? 3 : 0), $bytes.Length - ($bom ? 3 : 0))
  $eol = $text.Contains("`r`n") ? "`r`n" : "`n"
  $lines = $text -split "`r?`n", -1
  $out = [System.Collections.Generic.List[string]]::new()
  $marked = $false
  foreach ($line in $lines) {
    $trim = $line.Trim()
    if ($annotations -contains $trim) {
      if (-not $marked) { $out.Add($line.Replace($trim, '@NullMarked')); $marked = $true }
      continue
    }
    if ($trim -eq $importDrop) { continue }
    if ($trim -eq $importSwap) { $out.Add($line.Replace($trim, 'import org.jspecify.annotations.NullMarked;')); continue }
    $out.Add($line)
  }
  if (-not $marked) { $skipped++; continue }
  $newText = $out -join $eol
  if ($newText -ceq $text) { continue }
  # a file that had only the vanilla import would now lack the NullMarked import
  if ($newText -notmatch 'import org\.jspecify\.annotations\.NullMarked;') {
    Write-Warning "$($file.FullName): no jsr305 import to swap, add the NullMarked import by hand"
  }
  $changed++
  if ($Apply) {
    $enc = $utf8.GetBytes($newText)
    if ($bom) { $enc = [byte[]](0xEF, 0xBB, 0xBF) + $enc }
    [System.IO.File]::WriteAllBytes($file.FullName, $enc)
  }
}
Write-Output "files=$($files.Count) changed=$changed skipped=$skipped mode=$($Apply ? 'apply' : 'dry-run')"
