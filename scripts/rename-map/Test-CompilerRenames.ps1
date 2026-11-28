<#
.SYNOPSIS
  End-to-end tests for CompilerRenames.java / Invoke-CompilerRenames.ps1 (no Pester): builds the fixture APIs of
  Test-ApiFixtures.ps1, derives the member table with FindApiRenames.java, compiles a small client (new class names, old
  member names - the state after the class rename map) against the new jar with javac's flow gate, runs the applier
  (dry run, then -Apply), recompiles and checks what is left. Needs JAVA_HOME = a JDK 24+.

.EXAMPLE
  pwsh -NoProfile -File Test-CompilerRenames.ps1
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("compiler-renames-test-" + [guid]::NewGuid().ToString('N'))
$script:failures = 0
function Assert-Case([string] $name, [bool] $ok, [string] $detail = '') {
  if ($ok) { Write-Host "PASS  $name" }
  else { $script:failures++; Write-Host "FAIL  $name $(if ($detail) { "-- $detail" })" }
}
function New-FixtureJar([string] $dir, [hashtable] $sources, [string] $jar) {
  $files = foreach ($k in $sources.Keys) {
    $p = Join-Path $dir "src/$k"
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    Set-Content -LiteralPath $p -Value $sources[$k]
    $p
  }
  $classes = Join-Path $dir 'classes'
  New-Item -ItemType Directory -Force $classes | Out-Null
  & (Join-Path $env:JAVA_HOME 'bin/javac') -d $classes @files
  if ($LASTEXITCODE -ne 0) { throw "javac failed for $dir" }
  [System.IO.Compression.ZipFile]::CreateFromDirectory($classes, $jar)
}
. (Join-Path $PSScriptRoot 'Test-ApiFixtures.ps1')

$client = @{
  'client/MyScreen.java' = @'
package client;

import net.minecraft.fix.GraphicsExtractor;
import net.minecraft.fix.Screen;
import net.minecraft.fix.Other;
import net.minecraft.fix.Unrelated;

public class MyScreen extends Screen {
	@Override
	public void render(GraphicsExtractor g, int x, int y, float pt) {
		super.render(g, x, y, pt);
		g.drawString("drawString stays in this string", 1);
		g
		  .drawString("chained", 2);
		new Other().drawString("other keeps its name", 3);
		int c = GraphicsExtractor.OLD_CONST;
	}

	@Override
	public void renderBackground(GraphicsExtractor g, int x, int y, float pt) {
	}

	@Override
	protected void renderBg(GraphicsExtractor g, float pt, int x, int y) {
	}

	protected void renderLabels(GraphicsExtractor g, int x, int y) {
	}

	public void helper(Unrelated u) {
		this.render(null, 0, 0, 0f);
		u.drawString("unrelated receiver", 4);
		render(null, 1, 1, 1f);
	}
}
'@
  'client/Anon.java' = @'
package client;

import net.minecraft.fix.GraphicsExtractor;
import net.minecraft.fix.Screen;

class Anon {
  @SuppressWarnings({"unused"})
  Object o = register(new Screen() {
    @Override
    public void render(GraphicsExtractor g, int x, int y, float pt) {}
  });

  static Object register(Object o) { return o; }
}
'@
}

function Invoke-Javac([string] $out, [string] $log) {
  $files = Get-ChildItem -LiteralPath (Join-Path $tmp 'client/src') -Recurse -Filter *.java | ForEach-Object FullName
  New-Item -ItemType Directory -Force $out | Out-Null
  & (Join-Path $env:JAVA_HOME 'bin/javac') -d $out -cp $script:newJar '-XDshould-stop.ifError=FLOW' -Xmaxerrs 1000 @files *> $log
  # simulate Gradle, which repeats every error in its failure summary, indented by two spaces
  $lines = @(Get-Content -LiteralPath $log | ForEach-Object { "  $_" }); Add-Content -LiteralPath $log -Value $lines
}
function Invoke-Applier([string] $log, [switch] $Apply) {
  $a = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'Invoke-CompilerRenames.ps1'), '-Log', $log, '-Members', $script:members,
    '-Jar', $script:newJar, '-Root', (Join-Path $tmp 'client'))
  if ($Apply) { $a += '-Apply' }
  $o = & pwsh @a 2>&1 | Out-String
  [pscustomobject]@{ Exit = $LASTEXITCODE; Out = $o }
}

try {
  if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point to a JDK 24+' }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  New-Item -ItemType Directory -Force $tmp | Out-Null
  $oldJar = Join-Path $tmp 'old.jar'; $script:newJar = Join-Path $tmp 'new.jar'
  New-FixtureJar (Join-Path $tmp 'old') $OldApi $oldJar
  New-FixtureJar (Join-Path $tmp 'new') $NewApi $script:newJar
  $ov = Join-Path $tmp 'overrides.txt'
  Set-Content -LiteralPath $ov -Value @(
    'hint net.minecraft.fix.Screen renderBg merge into extractBackground'
    'moved net.minecraft.fix.Minecraft screen gui.screen()'
    'moved net.minecraft.fix.Minecraft setScreen gui.setScreen'
    'moved net.minecraft.fix.Minecraft getMainRenderTarget gameRenderer.mainRenderTarget'
  )
  $script:members = Join-Path $tmp 'members.tsv'
  $null = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Find-ApiRenames.ps1') -OldJar $oldJar -NewJar $script:newJar -Out (Join-Path $tmp 'diff') -Overrides $ov -MembersOut $script:members
  if ($LASTEXITCODE -ne 0) { throw 'Find-ApiRenames failed' }

  foreach ($k in $client.Keys) {
    $p = Join-Path $tmp "client/src/main/java/$k"
    New-Item -ItemType Directory -Force (Split-Path $p) | Out-Null
    [System.IO.File]::WriteAllText($p, $client[$k].Replace("`r`n", "`n").Replace("`n", "`r`n"))
  }
  $screen = Join-Path $tmp 'client/src/main/java/client/MyScreen.java'
  $anon = Join-Path $tmp 'client/src/main/java/client/Anon.java'
  $before = Get-FileHash $screen

  $log1 = Join-Path $tmp 'c1.log'
  Invoke-Javac (Join-Path $tmp 'o1') $log1
  $dry = Invoke-Applier $log1
  Assert-Case 'dry run exits 0' ($dry.Exit -eq 0) $dry.Out
  Assert-Case 'dry run writes nothing' ((Get-FileHash $screen).Hash -eq $before.Hash)
  Assert-Case 'repeated (indented) log errors are counted once' ($dry.Out -match 'raw=18 unique=9;') $dry.Out
  Assert-Case '(b) override renamed (render -> extractRenderState)' ($dry.Out -match 'MyScreen\.java:10  render -> extractRenderState  \[b:override') $dry.Out
  Assert-Case '(b) same-descriptor sibling renamed by name (renderBackground -> extractBackground)' ($dry.Out -match 'MyScreen\.java:20  renderBackground -> extractBackground  \[b:override') $dry.Out
  Assert-Case '(b) override inside an anonymous class passed as an argument' ($dry.Out -match 'Anon\.java:10  render -> extractRenderState  \[b:override') $dry.Out
  Assert-Case '(a) super call' ($dry.Out -match 'MyScreen\.java:11  render -> extractRenderState  \[a:call') $dry.Out
  Assert-Case '(a) call on a variable of a renamed type' ($dry.Out -match 'MyScreen\.java:12  drawString -> text  \[a:call') $dry.Out
  Assert-Case '(a) chained call on its own line' ($dry.Out -match 'MyScreen\.java:14  drawString -> text  \[a:call') $dry.Out
  Assert-Case '(a) static field' ($dry.Out -match 'MyScreen\.java:16  OLD_CONST -> NEW_CONST  \[a:field') $dry.Out
  Assert-Case 'unrelated receiver untouched; this.render still resolves to MyScreen.render in pass 1' ($dry.Out -notmatch 'MyScreen\.java:3[123] ') $dry.Out
  Assert-Case 'removed super method: unresolved override with hint' ($dry.Out -match 'MyScreen\.java:23  MyScreen#renderBg/4  HINT Screen#renderBg: merge into extractBackground') $dry.Out
  Assert-Case '(c) dead override reported' ($dry.Out -match 'MyScreen\.java:27  MyScreen#renderLabels/3 -> super renamed to extractLabels@Screen') $dry.Out
  Assert-Case 'summary line' ($dry.Out -match 'edits=7 files=2 ambiguous=0 unresolvedOverrides=1 deadOverrides=1') ($dry.Out -split "`n" | Select-String 'errors=' | Out-String)

  $app = Invoke-Applier $log1 -Apply
  Assert-Case 'apply exits 0 and writes 2 files' ($app.Exit -eq 0 -and $app.Out -match 'written=2') $app.Out
  $t = [System.IO.File]::ReadAllText($screen)
  Assert-Case 'string literal untouched' ($t -match '"drawString stays in this string"' -and $t -match 'new Other\(\)\.drawString\(') $t
  Assert-Case 'CRLF and tabs kept' ($t -match "`r`n" -and ($t -replace "`r`n", '') -notmatch "`n" -and $t -match "`tpublic void extractRenderState\(") $t

  # pass 2: renaming MyScreen.render exposed this.render(..) (javac prints no location for this./super.)
  $log2 = Join-Path $tmp 'c2.log'
  Invoke-Javac (Join-Path $tmp 'o2') $log2
  $p2 = Invoke-Applier $log2 -Apply
  Assert-Case 'pass 2: this.render and an unqualified render resolved through the enclosing source class' ($p2.Out -match 'MyScreen\.java:31  render -> extractRenderState  \[a:call' -and $p2.Out -match 'MyScreen\.java:33  render -> extractRenderState  \[a:call' -and $p2.Out -match 'edits=2 ') $p2.Out

  $log3 = Join-Path $tmp 'c3.log'
  Invoke-Javac (Join-Path $tmp 'o3') $log3
  $left = @(Get-Content $log3 | Select-String ': error: ' | ForEach-Object { $_.Line.Trim() } | Sort-Object -Unique)
  Assert-Case 'recompile: only renderBg and the unrelated receiver are left' ($left.Count -eq 2 -and ($left -join "`n") -match 'MyScreen\.java:23: error: method does not override' -and ($left -join "`n") -match 'MyScreen\.java:32: error: cannot find symbol') ($left -join "`n")
  $again = Invoke-Applier $log3
  Assert-Case 'pass 3 changes nothing (loop ends), dead override still reported' ($again.Out -match 'edits=0 ' -and $again.Out -match 'deadOverrides=1') $again.Out

  # --- 'moved' lines: members reached through another object now (Minecraft#screen -> gui.screen()) ---
  $mcDir = Join-Path $tmp 'client2/src/main/java/client2'
  New-Item -ItemType Directory -Force $mcDir | Out-Null
  $mcFile = Join-Path $mcDir 'UsesMc.java'
  [System.IO.File]::WriteAllText($mcFile, (@'
package client2;

import java.util.function.Supplier;
import net.minecraft.fix.Minecraft;
import net.minecraft.fix.Screen;
import net.minecraft.fix.Unrelated;

public class UsesMc {
	record Holder(Minecraft mc) {
		Object target() { return mc.getMainRenderTarget(); }
	}

	void run(Minecraft mc, Screen s, Unrelated u) {
		Screen cur = mc.screen;
		mc.setScreen(s);
		Minecraft.getInstance()
			.setScreen(null);
		Object rt = mc.getMainRenderTarget();
		mc.screen = s;
		Supplier<Object> sup = mc::getMainRenderTarget;
		Object other = u.screen;
		String str = "mc.screen stays in this string";
		mc.setScreen(mc.screen);
	}
}
'@).Replace("`r`n", "`n").Replace("`n", "`r`n"))
  function Invoke-Javac2([string] $out, [string] $log) {
    New-Item -ItemType Directory -Force $out | Out-Null
    & (Join-Path $env:JAVA_HOME 'bin/javac') -d $out -cp $script:newJar '-XDshould-stop.ifError=FLOW' -Xmaxerrs 1000 $mcFile *> $log
  }
  function Invoke-Applier2([string] $log, [switch] $Apply) {
    $a = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'Invoke-CompilerRenames.ps1'), '-Log', $log, '-Members', $script:members, '-Jar', $script:newJar, '-Root', (Join-Path $tmp 'client2'))
    if ($Apply) { $a += '-Apply' }
    $o = & pwsh @a 2>&1 | Out-String
    [pscustomobject]@{ Exit = $LASTEXITCODE; Out = $o }
  }
  $m1 = Join-Path $tmp 'm1.log'
  Invoke-Javac2 (Join-Path $tmp 'm1') $m1
  $mv = Invoke-Applier2 $m1 -Apply
  Assert-Case 'moved: field access becomes a call through the new owner' ($mv.Out -match 'UsesMc\.java:14  screen -> gui\.screen\(\)  \[a:moved Minecraft\]') $mv.Out
  Assert-Case 'moved: call keeps its arguments (variable receiver)' ($mv.Out -match 'UsesMc\.java:15  setScreen -> gui\.setScreen  \[a:moved Minecraft\]') $mv.Out
  Assert-Case 'moved: chained call on its own line (location = class)' ($mv.Out -match 'UsesMc\.java:17  setScreen -> gui\.setScreen  \[a:moved Minecraft\]') $mv.Out
  Assert-Case 'moved: record component receiver' ($mv.Out -match 'UsesMc\.java:10  getMainRenderTarget -> gameRenderer\.mainRenderTarget  \[a:moved Minecraft\]') $mv.Out
  Assert-Case 'moved: assignment and method reference refused and listed' ($mv.Out -match 'UsesMc\.java:19  Minecraft#screen -> gui\.screen\(\)  MOVED, not rewritten: assignment' -and $mv.Out -match 'UsesMc\.java:20  Minecraft#getMainRenderTarget -> gameRenderer\.mainRenderTarget  MOVED, not rewritten: method reference') $mv.Out
  # javac reports only the argument of mc.setScreen(mc.screen): the outer call shows up in the next compile (pass 2)
  Assert-Case 'moved: two moved errors on one line, pass 1 rewrites the argument only' ($mv.Out -match 'UsesMc\.java:23  screen -> gui\.screen\(\)  \[a:moved Minecraft\]' -and $mv.Out -notmatch 'UsesMc\.java:23  setScreen') $mv.Out
  Assert-Case 'moved: summary counts the moved edits' ($mv.Out -match 'edits=6 files=1 .* moved=6') ($mv.Out -split "`n" | Select-String 'errors=' | Out-String)
  $t2 = [System.IO.File]::ReadAllText($mcFile)
  Assert-Case 'moved: rewritten text (string and unrelated receiver untouched, CRLF kept)' ($t2.Contains("Screen cur = mc.gui.screen();`r`n") -and $t2.Contains("mc.gui.setScreen(s);") -and $t2.Contains("`t`t`t.gui.setScreen(null);") -and $t2.Contains("Object rt = mc.gameRenderer.mainRenderTarget();") -and $t2.Contains("return mc.gameRenderer.mainRenderTarget();") -and $t2.Contains('Object other = u.screen;') -and $t2.Contains('"mc.screen stays in this string"') -and $t2.Contains('mc.setScreen(mc.gui.screen());')) $t2
  $stale = Invoke-Applier2 $m1 -Apply
  Assert-Case 'moved: re-applying a stale log changes nothing (no gui.gui.screen()())' ($stale.Out -match 'edits=0 ' -and $stale.Out -match 'written=0' -and [System.IO.File]::ReadAllText($mcFile) -eq $t2 -and $stale.Out -match 'UsesMc\.java:14  Minecraft#screen -> gui\.screen\(\)  MOVED, not rewritten: ') ($stale.Out + [System.IO.File]::ReadAllText($mcFile))
  $m2 = Join-Path $tmp 'm2.log'
  Invoke-Javac2 (Join-Path $tmp 'm2') $m2
  $left2 = @(Get-Content $m2 | Select-String ': error: ' | ForEach-Object { $_.Line.Trim() })
  Assert-Case 'moved: recompile reports the outer call of line 23' ($left2.Count -eq 4 -and ($left2 -join "`n") -match 'UsesMc\.java:23:') ($left2 -join "`n")
  $p2m = Invoke-Applier2 $m2 -Apply
  Assert-Case 'moved: pass 2 rewrites the outer call (mc.gui.setScreen(mc.gui.screen()))' ($p2m.Out -match 'UsesMc\.java:23  setScreen -> gui\.setScreen  \[a:moved Minecraft\]' -and $p2m.Out -match 'edits=1 ' -and [System.IO.File]::ReadAllText($mcFile).Contains('mc.gui.setScreen(mc.gui.screen());')) ($p2m.Out + [System.IO.File]::ReadAllText($mcFile))
  $m3 = Join-Path $tmp 'm3.log'
  Invoke-Javac2 (Join-Path $tmp 'm3') $m3
  $left3 = @(Get-Content $m3 | Select-String ': error: ' | ForEach-Object { $_.Line.Trim() })
  Assert-Case 'moved: recompile leaves only the assignment, the method reference and the unrelated receiver' ($left3.Count -eq 3 -and ($left3 -join "`n") -match 'UsesMc\.java:19:' -and ($left3 -join "`n") -match 'UsesMc\.java:20:' -and ($left3 -join "`n") -match 'UsesMc\.java:21:') ($left3 -join "`n")

  $r = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-CompilerRenames.ps1') -Log (Join-Path $tmp 'nope.log') -Members $script:members -Jar $script:newJar -Root $tmp 2>&1 | Out-String
  Assert-Case 'missing log exits 1' ($LASTEXITCODE -eq 1) $r
} finally {
  Remove-Item -Recurse -Force -LiteralPath $tmp -ErrorAction SilentlyContinue
}
if ($script:failures -gt 0) { Write-Host "$($script:failures) failure(s)"; exit 1 }
Write-Host 'all passed'
