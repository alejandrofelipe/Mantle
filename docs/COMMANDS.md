# Build & Run Commands — Mantle 1.21.1 / NeoForge port

Canonical, copy-paste-ready commands for this machine. The goal of this file is **zero retries**:
every command here is written for the real environment probed on 2026-06-27. Read the
[Environment](#environment-this-machine) and [One-time prerequisites](#one-time-prerequisites-do-these-first)
sections **before** running any Gradle task — skipping them is the #1 cause of failed runs.

> **Build-state precondition (read this).** The Gradle/NeoForge tasks below (`build`, `runClient`,
> `runData`, NeoForge `dependencies`, etc.) describe the **target** build. As of 2026-06-27 the repo's
> `build.gradle` / `settings.gradle` / `gradle.properties` are **still ForgeGradle (Forge 1.20.1, Java 17)** —
> they are migrated by plan Stage 0 **Tasks 1–4**. Until that migration is committed, these commands will
> either fail or run the *old Forge* build (and `build` still triggers `reobfJar`, producing a `1.20.1-*`
> jar). So the real prerequisites for everything here are: prereqs 1–3 **plus** the Stage 0 build-script
> migration. Commands marked *(post-migration)* only behave as described once that migration is in.

---

## Environment (this machine)

| Fact | Value | Consequence |
|------|-------|-------------|
| OS / shell | Windows 10, PowerShell 7 (`pwsh`) | **Use PowerShell for build commands.** Bash PATH is unreliable here (see `running-windows-scripts` skill). |
| Default Java | Temurin **17** (`JAVA_HOME` → `C:\Users\aleja\scoop\apps\temurin17-jdk\current`) | MC 1.21.1 / NeoForge need **Java 21** — must override `JAVA_HOME` per command. |
| Repo root | `C:\Users\aleja\DEV\New Tinkers\repo` | The working dir of the shell tools is the **parent** (`New Tinkers`). Always target the repo explicitly. |
| Branch | `1.21.1` | All port work lives here. |
| Gradle wrapper | **8.1.1** (`gradle/wrapper/gradle-wrapper.properties`) | NeoGradle 7 needs Gradle **8.8+** — must upgrade (prereq 2). |
| System Gradle | 9.5.1 (Scoop) | **Do not use** for build tasks — NeoGradle 7 targets Gradle 8.8; 9.x may break. Use the wrapper. |
| Scoop CLI tools | `git` 2.54, `grep`, `fd`, `jq`, `python`, `dotnet-sdk` | Available; `git`/`grep` work in both Bash and PowerShell. |

Paths (Scoop JDKs):
- Java 17: `C:\Users\aleja\scoop\apps\temurin17-jdk\current`
- Java 21: `C:\Users\aleja\scoop\apps\temurin21-jdk\current` *(after prereq 1)*

---

## One-time prerequisites (do these FIRST)

Run once, before any Gradle task. Skipping any of these produces a confusing failure that looks like a
code problem but is not.

### 1. Install JDK 21

```powershell
scoop install temurin21-jdk
```

(The `java` bucket is already added — `temurin17-jdk` came from it. If `scoop` reports the bucket is
missing, run `scoop bucket add java` first.)

### 2. Upgrade the Gradle wrapper to 8.8

Edit `gradle/wrapper/gradle-wrapper.properties` and change the `distributionUrl` line to:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
```

> **Why edit the file instead of running `gradlew wrapper`?** The current wrapper (8.1.1) and the system
> Gradle (9.5.1) both *configure* the build when the `wrapper` task runs, and NeoGradle 7 rejects both
> versions — a chicken-and-egg failure. Editing the properties file changes the version with **zero build
> evaluation**; the next `gradlew` call downloads 8.8 and uses it.

### 3. Point Gradle at Java 21 for every NeoForge task

`JAVA_HOME` controls the JVM that **launches** Gradle itself — that must be a version the wrapper/NeoGradle
accept (Java 21 is safe for both Gradle 8.8 and NeoForge 1.21.1). Set it in the **same** PowerShell command
as the Gradle call (PowerShell env vars do **not** persist between separate tool invocations):

> Note: `settings.gradle` applies the `foojay-resolver-convention` plugin, so for the *toolchain* (the JDK
> used to compile/run the mod) Gradle may **auto-provision** a Java 21 toolchain if it can't find one —
> a freshly downloaded JDK appearing under Gradle's caches is expected, not a misconfiguration. Setting
> `JAVA_HOME` to temurin21 still matters for the launching JVM, and avoids relying on a network download.

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\repo\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\repo" <task>
```

This is the canonical form used throughout this file. Alternatively, add this line to
`gradle.properties` once (then `JAVA_HOME` per-command is unnecessary):

```properties
org.gradle.java.installations.paths=C:\\Users\\aleja\\scoop\\apps\\temurin21-jdk\\current
```

---

## The canonical invocation

Because the shell tools start in `New Tinkers` (the parent of the repo), call the wrapper by absolute path
with `-p` so it works regardless of current directory and never triggers a `cd` permission prompt:

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\repo\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\repo" <task>
```

Shorthand used in the table below: **`GW <task>`** = the line above with `<task>` substituted.

Equivalent if you prefer to move into the repo first:

```powershell
Set-Location "C:\Users\aleja\DEV\New Tinkers\repo"; $env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; .\gradlew.bat <task>
```

> The Gradle **daemon is disabled** (`org.gradle.daemon=false`), so every invocation is a cold start —
> expect each command to take longer than a daemon-backed build. Do not "retry" a slow first run; background
> it instead (see the run-in-background note below).

---

## Task reference

| Goal | Task (`GW <task>`) | Notes / expected signal |
|------|--------------------|-------------------------|
| First sync / download deps | `--refresh-dependencies tasks` | **First run downloads NeoForge + decompiles Minecraft — minutes, large download. Run in background.** Success = task list prints, no resolution errors. |
| List available tasks | `tasks` | Shows `runClient`, `runData`, `runServer`, etc. once NeoGradle is applied. |
| Dependency tree *(post-migration)* | `dependencies --configuration runtimeClasspath` | Confirm `net.neoforged:neoforge:21.1.234` resolves (no `FAILED`). Before Stage 0 migration this still shows the Forge dep. |
| **Compile** (per-stage gate) | `compileJava` | The verification gate for port stages 0–7. Success = `BUILD SUCCESSFUL`, zero `error:` lines. |
| Compile tests (if present) | `compileTestJava` | Only if a test source set exists. |
| **Full build** | `build` | Compiles + runs `jar`. Output: `build/libs/Mantle-1.21.1-*.jar`. |
| **Datagen** | `runData` | Writes/refreshes `src/generated/resources/`. Background it; review the git diff afterward. |
| **Run client** *(post-migration)* | `runClient` | Launches the game. **Background it and watch the log**; success = main menu, `mantle` in the mod list, no `mantle` registration errors. |
| Run server *(post-migration)* | `runServer` | `--nogui` is preconfigured. Background it. |
| Game tests | `runGameTestServer` | **Does not exist by default** — no `gameTestServer` run is defined in `build.gradle runs{}` (current or planned). It only becomes a task if such a run is explicitly added. Don't run it expecting it to be there. |
| Clean | `clean` | Deletes `build/`. The next run re-downloads/re-decompiles — slow. |
| Upgrade wrapper (one-time) | see [prereq 2](#2-upgrade-the-gradle-wrapper-to-88) | Prefer editing the properties file. |

Run-in-background: for `--refresh-dependencies`, `build`, `runData`, `runClient`, `runServer`, use the shell
tool's **`run_in_background: true`** rather than a foreground call with a long timeout — you'll be notified
on completion and avoid spurious timeout retries.

### Per-stage compile gate (filtered)

The port plan's per-stage gate isn't a bare `compileJava` — it filters the output to the package being
ported, e.g. `compileJava 2>&1 | grep -E "network"`. That works in **PowerShell 7**: `2>&1` merges the
error stream and the pipe feeds the Scoop `grep` (on PATH). Canonical form:

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\repo\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\repo" compileJava 2>&1 | Select-String -Pattern "network"
```

`Select-String` is the PowerShell-native filter (no external tool); the Scoop `grep -E "network"` also works
if you prefer it. **Pass condition for the gate:** no `error:` line mentioning the in-scope package. Other
packages may still show errors at that stage — that's expected; only the stage's own package must be clean.

---

## Search & replace (code sweeps)

Stage 1 (Task 6) does repo-wide import renames and `new ResourceLocation(...)` rewrites. Prefer, in order:

1. **The editor's project-wide find/replace** (or the Edit tool with `replace_all`) — primary method; the
   plan calls this out first. Always diff before committing a mechanical sweep.
2. **The Grep tool** (ripgrep-backed) to *find* every site, e.g. pattern `new ResourceLocation\(` over
   `src/main/java`. This is the retry-proof way to locate occurrences — it does not depend on Bash PATH.
3. **PowerShell-native** search/replace if scripting it:

```powershell
# find sites
Select-String -Path "C:\Users\aleja\DEV\New Tinkers\repo\src\main\java\*" -Pattern "new ResourceLocation\(" -Recurse
# count remaining Forge imports per file
Select-String -Path "C:\Users\aleja\DEV\New Tinkers\repo\src\main\java\*" -Pattern "import net.minecraftforge" -Recurse | Group-Object Filename | Sort-Object Count
```

The Scoop `grep` and Git-Bash `find`/`sed` are present and work in the **Bash** tool for read-only searches
(`grep -rn "new ResourceLocation(" src/main/java`), but for edits prefer the editor/Edit tool so changes are
reviewable. Do **not** rely on `sed` in-place edits as the primary mechanism — they are hard to review and
easy to over-match.

## Git (port workflow)

The `1.21.1` branch already exists. The plan commits after every green step.

```powershell
# from the repo (shell tools start in the parent, so target the repo):
Set-Location "C:\Users\aleja\DEV\New Tinkers\repo"
git status
git add <paths>
git commit -m "port: <conventional message>"
git log --oneline -5
```

- Commit messages: conventional-commits style, in English.
- `LF will be replaced by CRLF` warnings on commit are **harmless** (Windows line endings) — not a retry trigger.

---

## Troubleshooting — symptom → cause → fix (all retry-savers)

| Symptom in output | Real cause | Fix |
|-------------------|-----------|-----|
| `Minimum supported Gradle version is 8.8. Current version is 8.1.1` | Wrapper not upgraded | [Prereq 2](#2-upgrade-the-gradle-wrapper-to-88) |
| `Unsupported class file major version` / toolchain error / `No compatible toolchains` | Gradle running on Java 17, or can't find Java 21 | [Prereq 1 + 3](#one-time-prerequisites-do-these-first) — set `JAVA_HOME` to temurin21 in the same command |
| `Plugin [id: 'net.neoforged.gradle.userdev'...] was not found` | `settings.gradle` missing the NeoForged maven, or offline | Ensure `maven { url = 'https://maven.neoforged.net/releases' }` is in `settings.gradle pluginManagement`; check network |
| `Could not resolve net.neoforged:neoforge:21.1.234` | Version typo or transient maven issue | Fall back to `21.1.233` in `gradle.properties` |
| `Could not resolve mezz.jei:...` | JEI version not published for this MC/loader | Reconfirm/adjust `jei_version` against the BlameJared maven |
| Command "hangs" for minutes | Cold start + decompile/download (daemon disabled) | **Not a hang** — background it; don't retry |
| `dotnet`/script "not found" in Bash | Scoop PATH unreliable in Bash | Use the **PowerShell** tool |
| `cd: permission denied` prompt | `cd` in a compound Bash command | Use the absolute `GW` form with `-p`, or `Set-Location` in PowerShell |

---

## Quick checklist before reporting "it failed"

1. Did you set `JAVA_HOME` to **temurin21** in this exact command?
2. Is the wrapper at **8.8** (`gradle-wrapper.properties`)?
3. Did you target the **repo** (`-p ...\repo` or `Set-Location`)?
4. Is it actually a failure, or a **slow cold start** that should be backgrounded?

If all four are satisfied and it still fails, it's a real code/port problem — debug it (see the
`systematic-debugging` skill), don't re-run blindly.
