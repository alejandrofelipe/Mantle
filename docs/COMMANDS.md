# Build & Run Commands — Mantle / TinkersConstruct (MC 26.1.2 / NeoForge 26.1.2.130)

> **Current target: MC 26.1.2 / NeoForge 26.1.2.130** (branch `26.1.2` in both repos, the M7 release: Mantle `1.18` /
> Tinkers `3.17.0`; **every Gradle command there needs Java 25** — see the [26.1.2 notes](#2612-notes-m7)).
> Released lines: `1.21.11` = the 1.21.11 release, `1.21.10` = the 1.21.10 release, `1.21.8` = the 1.21.8 release,
> `1.21.5` = the 1.21.5 release, `1.21.3` = the 1.21.4 release, `1.21.1` = the 1.21.1 release (all Java 21).
> The 1.21.1 notes below still apply except where the [1.21.4 notes](#1214-notes-m1), [1.21.8 notes](#1218-notes-m4),
> [1.21.10 notes](#12110-notes-m5), [1.21.11 notes](#12111-notes-m6) or [26.1.2 notes](#2612-notes-m7) override them.
> Release tag messages follow [`RELEASE-TAGS.md`](RELEASE-TAGS.md).
> Migration gotchas: [`1.21.1-to-1.21.4.md`](migration/1.21.1-to-1.21.4.md), [`1.21.4-to-1.21.5.md`](migration/1.21.4-to-1.21.5.md),
> [`1.21.5-to-1.21.8.md`](migration/1.21.5-to-1.21.8.md), [`1.21.8-to-1.21.10.md`](migration/1.21.8-to-1.21.10.md),
> [`1.21.10-to-1.21.11.md`](migration/1.21.10-to-1.21.11.md), [`1.21.11-to-26.1.2.md`](migration/1.21.11-to-26.1.2.md).

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
| Default Java | Temurin **17** (`JAVA_HOME` → `C:\Users\aleja\scoop\apps\temurin17-jdk\current`) | MC 1.21.1 through 1.21.11 / NeoForge need **Java 21** — must override `JAVA_HOME` per command. From roadmap M7 (MC 26.1.2+) the game needs **Java 25** (installed at `C:\Users\aleja\scoop\apps\temurin25-jdk\current`). |
| Repo root | `C:\Users\aleja\DEV\New Tinkers\repo` | The working dir of the shell tools is the **parent** (`New Tinkers`). Always target the repo explicitly. |
| Branch | `26.1.2` | Current release (M7, Java 25). Released lines live on `1.21.11`, `1.21.10`, `1.21.8`, `1.21.5`, `1.21.3` (1.21.4) and `1.21.1` (Java 21). |
| Gradle wrapper | **9.2.1** (`gradle/wrapper/gradle-wrapper.properties`) | Paired with NeoGradle **7.1.39** (used from 1.21.4 through 1.21.11; 1.21.1 used 7.1.38). Already set; don't downgrade. |
| System Gradle | 9.5.1 (Scoop) | **Do not use** for build tasks — always use the wrapper (9.2.1), which pins the exact Gradle that NeoGradle 7.1.39 requires. |
| Scoop CLI tools | `git` 2.54, `grep`, `fd`, `jq`, `python`, `dotnet-sdk` | Available; `git`/`grep` work in both Bash and PowerShell. |

Paths (Scoop JDKs):
- Java 17: `C:\Users\aleja\scoop\apps\temurin17-jdk\current`
- Java 21: `C:\Users\aleja\scoop\apps\temurin21-jdk\current` *(after prereq 1)*
- Java 25: `C:\Users\aleja\scoop\apps\temurin25-jdk\current` *(for M7+; installing a scoop JDK repoints `JAVA_HOME`/`PATH` —
  undo with `scoop reset temurin17-jdk`)*

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

### 2. Upgrade the Gradle wrapper to 9.2.1

Edit `gradle/wrapper/gradle-wrapper.properties` and change the `distributionUrl` line to:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.1-bin.zip
```

> **The NeoGradle plugin version and the Gradle version must match.** NeoGradle 7.0.190 requires Gradle 8.13;
> NeoGradle **7.1.39** (what `build.gradle` uses; 7.1.38 on the `1.21.1` branch) requires Gradle **9.x**. We pin Gradle **9.2.1** + foojay
> **1.0.0** to mirror the official [1.21.1 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle). Change
> one, cross-check the others.

> **Why edit the file instead of running `gradlew wrapper`?** The current wrapper (8.1.1) and the system
> Gradle (9.5.1) both *configure* the build when the `wrapper` task runs, and NeoGradle 7 rejects both
> versions — a chicken-and-egg failure. Editing the properties file changes the version with **zero build
> evaluation**; the next `gradlew` call downloads 9.2.1 and uses it.

### 3. Point Gradle at Java 21 for every NeoForge task

`JAVA_HOME` controls the JVM that **launches** Gradle itself — that must be a version the wrapper/NeoGradle
accept (Java 21 is safe for both Gradle 9.2.1 and NeoForge 1.21.1). Set it in the **same** PowerShell command
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

## 1.21.4 notes (M1)

- **Datagen is two runs.** `runData` (run type `clientData`: models/blockstates/item defs/atlases/textures)
  and `runServerData` (run type `serverData`: recipes/tags/loot/advancements/fluid transfers), both writing
  to `src/generated/resources`. Run only the side you changed; the other side's files stay (M5, 2026-11-25):
  - The vanilla HashCache deletes every file of the output folder that no provider of the run produced.
    Mantle's `DatagenCacheKeeper` (lowest priority `GatherDataEvent` listener in Mantle and Tinkers)
    registers every provider with a cache file under `src/generated/resources/.cache` that the run lacks as
    a not-run provider, so the cache keeps its files. Texture generators (Mantle `GenericTextureGenerator`,
    Tinkers' generators) write through the data cache too, so they are no longer purged by their own run.
  - It needs the `.cache` files: Mantle commits them, Tinkers ignores them (local only). On a fresh Tinkers
    clone a single run still deletes the other side's files — run **both** once (either order) and the
    tree is complete; after that each run alone is safe.
  - A removed or renamed provider keeps its old files until its `.cache` file (the header names the
    provider) is deleted.
  - Check after any run: `git -C "<repo>" status --short src/generated` lists only intended changes.
  - The 129 Tinkers part textures that used to regenerate a few pixels off (M1's `GreyToColorMapping` port) now
    regenerate byte-identical to the committed upstream ones (fixed in M5).
- **Release tags** are annotated, on the release commit, with the message template in [`RELEASE-TAGS.md`](RELEASE-TAGS.md).
- **Release jars** drop `.DEV` via the `ARTIFACT_VERSION` env var (`version = <mc>-<ARTIFACT_VERSION>`). It
  leaks through the composite build, so build **Mantle and Tinkers separately**:
  ```powershell
  $env:ARTIFACT_VERSION = "1.13";   & "...\repo\gradlew.bat"    -p "...\repo" build
  $env:ARTIFACT_VERSION = "3.12.0"; & "...\tinkers\gradlew.bat" -p "...\tinkers" build
  Remove-Item Env:ARTIFACT_VERSION
  ```
- **Category-by-category porting gate:** plain `compileJava` stops at the first attribution failure. A
  scratchpad init script makes javac report method-body errors too (never commit it to the repo):
  ```groovy
  // flow.init.gradle
  allprojects { tasks.withType(JavaCompile).configureEach { options.compilerArgs.add('-XDshould-stop.ifError=FLOW') } }
  ```
  `gradlew.bat -p <repo> --init-script <scratch>\flow.init.gradle compileJava`
- **"Green" has layers.** Build + unit can pass while the mod fails to load, gametests (server-only) can pass
  while clients can't join, and a uitest `"ok"` only means "no crash" — open the PNGs to check the pixels.

## 1.21.8 notes (M4)

- Toolchain unchanged from 1.21.4 (Gradle 9.2.1 + NeoGradle 7.1.39, Java 21); datagen was still the two mutually
  purging runs (fixed in M5, see above).
- **uitest needs an unlocked screen** — see [`runClientUiTest`](#runclientuitest-self-driving-visual-suite).
- **The real uitest gate is a side-by-side review.** A capture bug once made every PNG show the previous scenario while
  all results said `"ok"`. For a milestone, run the same suite on the previous release (its tags checked out in
  throwaway worktrees outside the workspace) and compare every PNG pair; also check the client log for JEI plugin
  errors.
- Singleplayer never serializes packets: network codec coverage comes from the gametest
  `synced_recipes_stream_codec_round_trip`, not from the uitest.

## 1.21.10 notes (M5)

- Toolchain unchanged (Gradle 9.2.1 + NeoGradle 7.1.39, Java 21). Pins: NeoForge `21.10.64`, Parchment
  `1.21.10 / 2025.10.12`, JEI `26.3.0.31`, Crafting Tweaks `21.10.1` + Balm `21.10.11`, JSON Things `0.16.0` (re-added);
  Immersive Engineering still excluded.
- **Datagen keeps the other run's outputs** — no manual restore step any more (see the [1.21.4 notes](#1214-notes-m1)).
- **Tinkers' `runData` needs the 1.21.4 client jar** (SHA-1 `a7e5a602…`) for the spawn egg template layers. Looked up in
  order: `-PspawnEggTemplateJar=<path>`, the NeoGradle/ForgeGradle caches, the official launcher and Prism Launcher files;
  if none has it, Gradle downloads it once from `piston-data.mojang.com` into `caches/modules-2` (SHA-1 and size
  checked). Offline with nothing cached, `runData` fails naming the cause and `test` skips the real-layer egg check
  (logged). The jar is never committed.
- **`runClientUiTest` fails when any result is not `"ok"`** — see [`runClientUiTest`](#runclientuitest-self-driving-visual-suite).
- **JSON Things gametests:** `runGameTestServer` depends on `prepareGameTestThingpacks`, which syncs
  `src/gametest/thingpacks` into `run/gameTestServer/thingpacks` (JSON Things only reads `<game dir>/thingpacks`).
- Warning counts: `--rerun` belongs to the task right before it, so `compileJava compileTestJava --rerun` reruns only
  `compileTestJava`; use `compileJava --rerun` for main-source warnings.

## 1.21.11 notes (M6)

- Toolchain unchanged (Gradle 9.2.1 + NeoGradle 7.1.39, Java 21). Pins: NeoForge `21.11.48`, Parchment
  `1.21.11 / 2025.12.20` (the last Parchment release), JEI `27.55.0.122`, Crafting Tweaks `21.11.5` + Balm `21.11.9.1`,
  JSON Things `0.17.0`; Immersive Engineering still excluded.
- **Mechanical renames go through the scripts first** — `ResourceLocation` → `Identifier`, moved packages/classes and the
  `package-info` → `@NullMarked` rewrite: see [Mechanical renames](#mechanical-renames-between-versions-rename-map). Then
  the compiler-driven `ResourceKey#location()` → `identifier()` pass (never by regex: `TagKey`/`SoundEvent` keep
  `location()`).
- **The access transformer is not covered by the rename-map** (`src/**/*.java` only). After a bump, check every
  `accesstransformer.cfg` entry against the target jar (moved classes, `Identifier`/`criterion` descriptors): a stale entry
  only logs a warning and the build goes on.
- **Datagen golden diff** after every `runData`/`runServerData` (see [Automated tests](#automated-tests--screenshots)),
  with `-Allow` only for the outputs the step is meant to change.
- **Client log checks** for a uitest run: 0 `Duplicate sprite` (a texture stitched into both the blocks and the items
  atlas), 0 missing textures, no JEI broken-recipe or plugin errors. The 1.21.11 item atlas split fails silently otherwise.
- JEI API jars for `javap` diffs live under the scoop Gradle home (`C:\Users\aleja\scoop\apps\gradle\current\.gradle\caches`),
  not `~/.gradle`.

## 26.1.2 notes (M7)

- **Java 25** (branch `26.1.2` and later): use `C:\Users\aleja\scoop\apps\temurin25-jdk\current` as `JAVA_HOME` wherever this
  file says temurin21. Gradle 9.2.1 runs on it, and the build toolchain is 25 in both repos. NeoGradle 7.1.39 + Gradle 9.2.1
  are unchanged (they match the official `MDK-26.1.2-NeoGradle`):
  ```powershell
  $env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin25-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\repo\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\repo" <task>
  ```
- **No Parchment**: 26.1 ships unobfuscated with Mojang's parameter names, and the `parchment` block is gone.
- **Lombok is pinned to 1.18.42** (`lombok { version = … }` in both `build.gradle`): the freefair 8.6 default, 1.18.32, crashes
  javac 25 with `NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN`.
- Cold Mantle setup + `compileJava` takes about 7 min (NeoForm `26.1.2-1`). NeoForm's jar for the rename tools is still
  `repo\build\neoForm\neoFormJoined26.1.2-1\raw.jar`.
- `runClientHotswap` needs a JetBrains Runtime 25 registered in `org.gradle.java.installations.paths` (Tinkers
  `gradle.properties`); the JBR 21 there no longer matches.
- **Mechanical renames (D-TOOL)**: class rules come from the jar diff (`maps/26.1.2.txt`, regenerated from
  `maps/26.1.2.overrides.txt`), member renames from the compiler-driven applier with `maps/26.1.2-members.tsv` — workflow
  under [Mechanical renames](#mechanical-renames-between-versions-rename-map). Baseline proof on Mantle (Phase 0.5b, flow
  gate, unique errors): 770 → 300 after the map → 263 → 261 after two applier passes (third pass: 0 edits).
- **Battery on 26.1.2** (end of M7 Phase 6): Mantle `build` green; Tinkers `build` with **814 unit tests** (812 passing + 2
  `@Disabled`), `runGameTestServer` **172/172** (`All 172 required tests passed :)`), `runClientUiTest` **57/57** results (56
  scenarios + `mantle:suite_setup`). Same tasks as below, Java 25:
  ```powershell
  $env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin25-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" build
  $env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin25-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" runGameTestServer
  $env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin25-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" runClientUiTest
  ```
- **Data runs bind default components** (Mantle `DatagenComponents.bind` from a `GatherDataEvent` listener); without it a
  provider that builds a stack fails with "Components not bound yet". After the first `runData`/`runServerData` on a new
  version, also check that **no folder appeared at the drive root**: 26.1's `PackOutput.PathProvider` with an empty folder
  writes to `C:\<kind>\…`.
- **Client log checks** add, on 26.1.2: 0 `Can't create item stack` (an invalid `ItemStackTemplate` patch), 0 creative tab
  errors ("already been added to the tab"), and only the known `Missing FluidModel for fluid 'tconstruct:potion'`.
- **uitest startup screens:** the suite proceeds past third-party mod loading warnings and a plain world upgrade (a
  `warn:` result under `mantle:world_upgrade`, which the task reports without failing); loading errors, warnings from our
  own mods (`mantle:mod_loading`) and any other world prompt fail — see
  [`runClientUiTest`](#runclientuitest-self-driving-visual-suite).
- **Compat runtime:** JEI 29 needs the separate `mezz_config` mod (dev gets it transitively); `kuma_version` pins
  kuma-api to `26.1.2.4` at runtime (Balm's `26.1.2.2` crashes the client).
- Gotchas of the port: [`migration/1.21.11-to-26.1.2.md`](migration/1.21.11-to-26.1.2.md).

## Task reference

| Goal | Task (`GW <task>`) | Notes / expected signal |
|------|--------------------|-------------------------|
| First sync / download deps | `--refresh-dependencies tasks` | **First run downloads NeoForge + decompiles Minecraft — minutes, large download. Run in background.** Success = task list prints, no resolution errors. |
| List available tasks | `tasks` | Shows `runClient`, `runData`, `runServer`, etc. once NeoGradle is applied. |
| Dependency tree *(post-migration)* | `dependencies --configuration runtimeClasspath` | Confirm `net.neoforged:neoforge:21.11.48` resolves (no `FAILED`). Before Stage 0 migration this still shows the Forge dep. |
| **Compile** (per-stage gate) | `compileJava` | The verification gate for port stages 0–7. Success = `BUILD SUCCESSFUL`, zero `error:` lines. |
| Compile tests (if present) | `compileTestJava` | Only if a test source set exists. |
| **Full build** | `build` | Compiles + runs `jar`. Output: `build/libs/Mantle-1.21.11-*.jar`. |
| **Datagen** | `runData` and/or `runServerData` | 1.21.4: two runs (client assets / server data). Each keeps the other side's outputs since M5 (needs the local `.cache`) — see [1.21.4 notes](#1214-notes-m1) before committing `src/generated/resources/`. Tinkers' `runData` needs the 1.21.4 client jar for the spawn egg templates (found locally or downloaded once by Gradle, or `-PspawnEggTemplateJar=<path>` — see [1.21.10 notes](#12110-notes-m5)). Background them. |
| **Run client** *(post-migration)* | `runClient` | Launches the game. **Background it and watch the log**; success = main menu, `mantle` in the mod list, no `mantle` registration errors. |
| Run server *(post-migration)* | `runServer` | `--nogui` is preconfigured. Background it. |
| Game tests | `runGameTestServer` | **Not defined in Mantle's `build.gradle runs{}`** — Mantle has no game-test suite. The **tinkers** repo defines this run; see [Automated tests & screenshots](#automated-tests--screenshots). |
| Clean | `clean` | Deletes `build/`. The next run re-downloads/re-decompiles — slow. |
| Upgrade wrapper (one-time) | see [prereq 2](#2-upgrade-the-gradle-wrapper-to-921) | Prefer editing the properties file. |

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

## Mechanical renames between versions (rename-map)

From M6 (MC 1.21.11) the scripted renames of a version bump (e.g. `ResourceLocation` → `Identifier`) go through
`scripts/rename-map/`: a map file per milestone (`maps/<version>.txt`, rules `token`/`literal`/`class`) applied to
`src/**/*.java`. Dry run by default (hits per rule/file + string-literal warnings); `-Apply` rewrites, keeping line endings
and BOMs. Format, workflow and tests: [`scripts/rename-map/README.md`](../scripts/rename-map/README.md).

```powershell
# Mantle (dry run; add -Apply to rewrite)
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Invoke-RenameMap.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\repo" -Map "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\maps\1.21.11.txt"
# Tinkers (same tool, from ../repo/scripts/rename-map/)
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Invoke-RenameMap.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\tinkers" -Map "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\maps\1.21.11.txt"
```

Apply each repo's rename in its own commit, then fix the compiler-driven rest by hand.

Four helpers live next to it (same folder, details in its README):

```powershell
# seed moved-class rules: imports of <repo> that no longer exist in the target jar (NeoForm raw.jar, after the first build)
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Find-MovedClasses.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\tinkers" -Jar "<repo>\build\neoForm\neoFormJoined<version>-*\raw.jar"
# 1.21.11: package-info.java -> JSpecify @NullMarked (dry run; add -Apply)
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Convert-PackageInfoToNullMarked.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\tinkers"
# M7 Phase 0.6: javax.annotation (jsr305) -> JSpecify @Nullable with type-use placement (dry run; add -Apply)
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Convert-NullableToJSpecify.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\tinkers"
# every access transformer entry must still exist in the target jar (exit 2 + MISSING CLASS / BAD METHOD / BAD FIELD lines)
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Find-StaleAccessTransformer.ps1" -At "<repo>\src\main\resources\META-INF\accesstransformer.cfg" -Jar "<repo>\build\neoForm\neoFormJoined<version>-*\raw.jar"
```

None of the rename tools touches `accesstransformer.cfg`, and NeoForge skips a stale AT entry silently — run
`Find-StaleAccessTransformer.ps1` on both repos after every version bump (needs `JAVA_HOME` for `javap`; see the
[1.21.11 notes](#12111-notes-m6)).

From M7 (MC 26.1.2) the class rules and the member renames are **derived from the jars**, not written by hand (two Java 25
single-file programs, JDK `java.lang.classfile` API; `JAVA_HOME` = temurin25). Old jar = the previous version's NeoForm
`raw.jar` (e.g. `neoFormJoined1.21.11-20251209.172050`), new jar = the target's (`neoFormJoined26.1.2-1`):

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin25-jdk\current"; $rm = "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map"
$old = "C:\Users\aleja\DEV\New Tinkers\repo\build\neoForm\neoFormJoined1.21.11-20251209.172050\raw.jar"
$new = "C:\Users\aleja\DEV\New Tinkers\repo\build\neoForm\neoFormJoined26.1.2-1\raw.jar"
# 1. jar diff -> maps/26.1.2.txt (class rules) + maps/26.1.2-members.tsv (member table); ~4 s; read the WARNING lines
pwsh -NoProfile -File "$rm\Find-ApiRenames.ps1" -OldJar $old -NewJar $new -Overrides "$rm\maps\26.1.2.overrides.txt" -Out "$env:TEMP\apidiff" -MapOut "$rm\maps\26.1.2.txt" -MembersOut "$rm\maps\26.1.2-members.tsv" -Title "MC 1.21.11 -> MC 26.1.2"
# 2. class rules (dry run first, then -Apply; own commit)
pwsh -NoProfile -File "$rm\Invoke-RenameMap.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\repo" -Map "$rm\maps\26.1.2.txt"
# 3. loop: flow-gate compile -> member applier (dry run, then -Apply) -> compile ... until it reports edits=0
& "C:\Users\aleja\DEV\New Tinkers\repo\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\repo" compileJava --init-script "$rm\flow.init.gradle" *> "$env:TEMP\compile.log"
pwsh -NoProfile -File "$rm\Invoke-CompilerRenames.ps1" -Log "$env:TEMP\compile.log" -Members "$rm\maps\26.1.2-members.tsv" -Jar $new -Root "C:\Users\aleja\DEV\New Tinkers\repo"
# Tinkers: -Root "<tinkers>,<repo>" (Mantle sources are indexed for supertypes)
```

The applier only touches identifiers javac reported (`cannot find symbol` member calls/fields, `does not override`
declarations) and **reports** unresolved overrides (with the overrides file's hints), ambiguous spots, renames matched by
descriptor alone (review once each) and possible dead overrides (un-`@Override`d methods whose supertype method was
renamed/removed). Tests: `Test-FindApiRenames.ps1`, `Test-CompilerRenames.ps1`, `Test-RenameMap.ps1` (README).

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

## Hot-reload dev loop (tinkers `runClientHotswap`)

Apply Java changes to a **running** client without restarting — terminal-only, no IDE. Lives in the
**tinkers** repo (`tinkers/build.gradle` + `tinkers/hotswap.ps1`), proven live on this machine.

**How it works:** the `clientHotswap` run selects a **JetBrains Runtime 21** as its JVM (DCEVM built in —
`-XX:+AllowEnhancedClassRedefinition` allows structural class changes) and exposes **JDWP** on
`127.0.0.1:5005`. Reloads are triggered explicitly by `hotswap.ps1`, which finds recently recompiled
`.class` files (tinkers **and** mantle roots) and injects each via `jdb redefine` — the same JDI path IDE
hotswap uses. (HotswapAgent's `autoHotswap` file-watcher was tried first and is **unreliable under
ModLauncher/Windows** — watcher registered but silently delivered no events in 2 of 3 sessions.)

**The loop:**

```powershell
# 1. once per session (background; window opens, keep it open):
GW runClientHotswap           # from the tinkers repo: gradlew.bat -p tinkers runClientHotswap
# 2. edit code, then:
GW compileJava
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\aleja\DEV\New Tinkers\tinkers\hotswap.ps1"
# 3. see it in the running client (for datapack-driven paths, /reload in-game first)
```

Data/assets don't need any of this: `GW processResources`, then in-game `/reload` (recipes/tags/loot)
or **F3+T** (textures/models/lang).

**Hard limits (restart required):** newly ADDED classes, registration changes (blocks/items/menus),
new event listeners, changed static initializers. Method-body/GUI/render/recipe-logic edits reload fine.

**Setup notes & gotchas (all hit and solved on this machine):**

| Thing | Detail |
|---|---|
| JBR selection | `org.gradle.java.installations.paths=C:/Users/aleja/tools/jbr21` (tinkers `gradle.properties`) + `javaLauncher` with `JvmVendorSpec.JETBRAINS` in `build.gradle`. Gradle may satisfy the spec with its own cached JetBrains JDK — any DCEVM-capable JBR 21 is fine. A raw `executable` override does NOT work (Gradle 8+ validates it against `javaLauncher` and throws). |
| AMD driver crash at boot | With JDWP active, the FML early window can crash `atio6axx.dll` at "Trying GL version 4.6". Fix: `earlyWindowControl = false` in `tinkers/run/clientHotswap/config/fml.toml` (run-dir config — re-apply if the run dir is wiped). Boot shows no progress window; that's normal, not a hang. |
| Mantle jar stability | Mantle's dev version/manifest are deliberately stable (no git hash, no build timestamp — see repo `build.gradle`). Do NOT reintroduce per-build values: they made every tinkers compile rewrite the jar underneath the running client, breaking its `mantle` pack on `/reload`/rejoin (`Failed to read pack.mcmeta`). |
| `hotswap.ps1` internals | Stages classes into a space-free temp dir (jdb's `redefine` splits on whitespace), uses the explicit `SocketAttach` connector (Windows `-attach` means shared memory), ASCII pipe encoding (PS5 BOM ate the first command), and reports never-loaded classes (datagen-only) as skips. |
| `hotswap-agent.jar` | Still at `C:\Users\aleja\tools\` but **unused** since the jdb pivot — safe to delete. |

---

## Automated tests & screenshots

Three automated suites live in the **tinkers** repo (`tinkers/build.gradle`) — a repeatable alternative
to manual smoke testing after runtime-affecting changes: `test` for the plain-JVM unit suite,
`runGameTestServer` for headless logic tests, and `runClientUiTest` for a self-driving visual GUI suite.
Same `JAVA_HOME` + `-p` pattern as the
[canonical invocation](#the-canonical-invocation), just pointed at `tinkers` instead of `repo`.

> **Boundary contracts (R1).** [`tinkers/docs/boundary-contracts.md`](../../tinkers/docs/boundary-contracts.md) maps every
> vanilla/NeoForge integration point to the test that guards it (plus a gaps list) — review it in each milestone's
> Phase 0; a new integration point gets a row and a guard.
>
> **Datagen golden diff.** After `runData` + `runServerData`, check that generated resources didn't change unexpectedly
> (pass `-Allow '<glob,...>'` for outputs the step is meant to change; details in
> [`scripts/verify-generated/README.md`](../scripts/verify-generated/README.md)):
> ```powershell
> pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\verify-generated\Test-GeneratedUnchanged.ps1" -Repo "C:\Users\aleja\DEV\New Tinkers\tinkers"
> pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\verify-generated\Test-GeneratedUnchanged.ps1" -Repo "C:\Users\aleja\DEV\New Tinkers\repo"
> ```

### `test` (JVM unit tests)

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" test
```

Runs the JUnit 5 + Mockito unit suite (`src/test/java`, `slimeknights.tconstruct.*`) on a plain JVM —
no Minecraft boot. Current suite (1.21.11) = 804 tests: 802 passing + 2 `@Disabled` (each carries its reason in-code).
Mantle has no test source set: its unit tests (transfer helpers, `SingleFluidTank`, datagen cache keeper, …) live in this
suite.
Reports land in `build/reports/tests/test/` (HTML) and `build/test-results/test/` (JUnit XML).

> **UP-TO-DATE trap.** `build` already executes this suite, so a `test` right after a green `build`
> prints `> Task :test UP-TO-DATE` and runs nothing — that is still a pass (the tests ran inside
> `build`); read the reports above for the totals.

> **Coverage (JaCoCo).** `test` is `finalizedBy jacocoTestReport`, so a normal `test` run also refreshes
> a coverage report at `build/reports/jacoco/test/html/index.html` (machine-readable XML alongside at
> `jacocoTestReport.xml`). The measured % is scoped to the pure-logic core: it excludes client
> render/screens, datagen providers, compat plugins, gametests, and registration/holder classes — code
> that isn't unit-testable in isolation and is instead covered by `runClientUiTest` / `runData` / load —
> so it reads higher than a raw whole-mod number would.

### `runGameTestServer` (headless logic tests)

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" runGameTestServer
```

Runs every registered gametest. **1.21.5 framework:** `@GameTest`/`@GameTestHolder` are gone — Tinkers marks
test methods with its own `@TinkerGameTest(template, timeoutTicks)` and `TinkerGameTestRegistration` registers
each as a `TEST_FUNCTION` + `FunctionGameTestInstance` (id `tconstruct:<method_name>`, structure
`tconstruct:<template>`, environment `tconstruct:default`) on `RegisterGameTestsEvent`; the server runs every
registered instance, so the old `neoforge.enabledGameTestNamespaces` property is gone. In-game (dev client):
`/test run tconstruct:<name>`, `/test runfailed`, … Current suite (1.21.11) = 161 Tinkers tests (+ vanilla's built-in
`minecraft:always_pass`, so the log says 162) — smeltery/alloyer/casting/tool-crafting
logic (castless casting included), capabilities and the native transfer API (`CapabilityGameTests`,
`FluidTransferGameTests`, `SmelteryTransferGameTests`: transaction simulate/abort/commit, hopper/pipe interop, item
`ItemAccess` capabilities), menu slots over native handlers (`MenuSlotGameTests`, incl. the `slot_safety_*` safety net),
lazy tool verification and tool copies (`ToolGameTests`), `BlockEntityRoundTripGameTests` (save → load → re-save and client
sync of every Tinkers block entity, empty, filled and filled → empty), `DropGameTests`, `FluidEffectGameTests`,
`JsonThingsGameTests` (registered only when JSON Things is loaded; thingpack synced by `prepareGameTestThingpacks`), the
`*_lookup_populated` recipe-lookup checks,
`RecipeValidationGameTests` (every melting/casting/alloy recipe), `DatapackTagGapGameTests`, `WorldgenGameTests`
(structures/sets/features/biome modifiers resolve,
all 25 island templates load with the right palette, and every island, geode, cobalt ore and slime tree is
force-placed far from the test grid with fixed seeds — the `/place` path; natural generation, i.e. biome tags
+ structure-set spacing on real terrain, is not covered by the flat world), `SaveCompatGameTests` (block entity,
entity and built-tool save → load round-trips checked against the 1.21.5 on-disk keys/types, plus legacy NBT shapes),
`MantleCommandGameTests` (`/mantle` command suggestions and execution), `RecipeSyncGameTests`
(`synced_recipes_stream_codec_round_trip`: every client-synced recipe encoded → decoded → re-encoded with
`RecipeHolder.STREAM_CODEC`, byte-equal — the only coverage of the dedicated-server network path, since singleplayer
never serializes; `client_synced_recipe_types_complete`: every recipe type the client reads is actually sent),
`HandlerRegistrationGameTests` (every block entity type and item exposes exactly its expected item/fluid handlers), and
since 1.21.11 `PermissionGameTests` (op levels through `MantleCommand.hasPermission`), `DeathGameTests` (soulbound with
the `keep_inventory` game rule off/on), `ProjectileGameTests` (access-transformed arrow damage/pierce) and the equipment
asset checks `armor_equipment_assets` / `jsonthings_armor_equipment_assets`, and since M7 Phase 0.5c
`DatapackCompletenessGameTests` (every shipped recipe, loot table and global loot modifier JSON whose `neoforge:conditions`
pass is loaded). Every guard is mapped to its integration point in the boundary-contract catalogue. A green run takes ~1-4 min (cold) and prints
`All 162 required tests passed :)` + `BUILD SUCCESSFUL` (1.21.11).

> **Fresh world every run.** `runGameTestServer` depends on `cleanGameTestWorld`, which deletes
> `run/gameTestServer/world` first — the 1.21.4 server reused that world forever (flat preset, seed 0), so
> placed structures and chunks from a previous run leaked into the next one. The 1.21.5 server reportedly uses
> its own `run/gameTestServer/gametestserver/gametestworld` and recreates it each run — confirm on the first
> green 1.21.5 run, then the task can go.

> **Zero-tests trap.** With no matching tests the run still exits 0 (`No test functions were given!` +
> `BUILD SUCCESSFUL`) — green only means something once you know tests exist. With tests present, a
> failure exits non-zero as expected.

Arena templates are datagen-generated (`gametest/empty_5x5x5` / `empty_9x9x9`, via
`GameTestStructureProvider`); rigs come from `SmelteryRigs` — fail-fast helpers that throw with a
diagnostic message instead of timing out silently.

### `runClientUiTest` (self-driving visual suite)

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" runClientUiTest
```

Boots a real client straight into the committed superflat world `UITest` (`--quickPlaySingleplayer`,
1280×720, `-Dmantle.uitest=true`), drives the 48 registered scenarios (`tinker_station`, `part_builder`,
`crafting_station`, `smeltery`, `melter`, `casting_pour`, `book_materials_and_you`, `jei_melting_category`,
`jei_item_list_cleanup`, `book_interior`, `station_reflow`, `station_collapsed`,
`station_collapsed_overlay`, `extra_hearts`, `dynamic_block_models` — connected glass, a retextured table and a filled
tank in the world, the 1.21.5 block-state-level models —, `head_items` — every Tinkers head in a survival inventory,
one held and one worn, `dynamic_item_icons` — a survival inventory of custom-model items, asserting each has a
distinct, stable GUI model identity (the 1.21.6+ GUI item atlas key), `jei_<category>_category` for the other 11 Tinkers
JEI categories (`casting_table`, `casting_basin`, `molding`, `alloy`, `entity_melting`, `foundry`, `modifiers`,
`severing`, `tool_building`, `part_builder`, `worktable` — each asserts the category is registered and has recipes),
`book_structure` — the smeltery 3D structure preview in "Mighty Smelting" (Mantle's picture-in-picture structure
renderer) —, `crafting_tweaks_rotate` — presses the Crafting Tweaks rotate button on a filled crafting station and
asserts the grid rotated —, and since 1.21.10: `tool_inventory` (a pocket tool opens its inventory through the item
capability), `fluid_blocks` (melter/alloyer/gauge fluids and a faucet → channel → basin pour in the world),
`casting_cooling` (table and basin frozen mid cooling, output fading in), `aoe_outline` / `aoe_outline_high_contrast`
(3×3 hammer outlines + extra-block breaking overlay), `station_carried_clicks` (pick up, double-click gather, tab click
with a carried stack), `jei_tank_clickable` / `jei_smeltery_tank_clickable` / `jei_crafting_station_slot` /
`jei_smeltery_slot` (JEI mouse areas match the cursor), `jei_recipe_transfer` (transfer from a side-inventory chest) and
`jei_category_tooltips` (no-GUI check of the JEI category tooltip lines), and since 1.21.11: `item_atlas_grid` (item
icons across the blocks/items atlas split), `trimmed_armor` / `worn_armor` (worn Tinkers armor and trims, with pixel
asserts), `jei_tinker_station_transfer` / `jei_tool_inventory_transfer` (JEI 27 transfers), and since the R3 pilot `smeltery_tank_world` (the in-world smeltery tank: a liquid below a gas, pixel asserts)), writes
`run/clientUiTest/uitest-screenshots/*.png` +
`run/clientUiTest/uitest-results.json` (scenario id → `"ok"` / `"fail: ..."`), then **exits on its own**
(verified full run ≈ 6-7 min cold including the build, ~3 min in-world, zero leftover
processes). The suite ignores your mouse and keyboard, so you can keep working while it runs (see **Input isolation**
below). A green 26.1.2 run records 60 results: the 56 Tinkers scenarios, the Mantle self-checks `mantle:input_isolation`,
`mantle:progress_hidden` and `mantle:clear_inventory`, plus `mantle:suite_setup` (below).

> **The task fails on any failing result (since M5).** The client always exits 0, so `runClientUiTest` deletes
> `uitest-results.json` before launching and reads it after the client exits: it fails (`BUILD FAILED`, listing each
> `id: result`) when any result — `mantle:suite_setup` included — is neither `"ok"` nor a `"warn: ..."` note, when
> `mantle:suite_setup` is missing, or when the file is missing or older than the run (the client died or quit before the
> suite finished). Since M7, `"warn: ..."` results (e.g. `mantle:world_upgrade` when the committed world needed a plain
> upgrade) are logged as warnings without failing the task. A green run prints `uitest: all N result(s) ok` +
> `BUILD SUCCESSFUL`. The JSON is still written for the details.

> **Screen must be unlocked.** If the Windows session is locked, GL window creation fails in `glfwCreateWindow`
> (`atio6axx.dll`, AMD driver) and the client never boots. That is the lock, not a flake — unlock and rerun.

**Deterministic environment.** While the suite is active the MUSIC sound source is muted (set to 0 in this run
dir's `options.txt`; sound effects stay on) and mobs are disabled: at world load the server turns off the game rules
`spawn_mobs`/`spawn_patrols`/`spawn_wandering_traders`/`spawn_phantoms` (1.21.11 `gamerules` ids; before 1.21.11
`doMobSpawning`/`doPatrolSpawning`/`doTraderSpawning`/`doInsomnia` — the new `spawn_monsters` is only a sub-gate of
`spawn_mobs`) and discards every `Mob` in the loaded levels, and an
`EntityJoinLevelEvent` listener cancels any `Mob` joining a server level for the rest of the run (saved mobs in chunks
loaded later by a scenario's `/tp` included). Non-mob entities — armor stands, item frames, displays — are untouched.
The suite verifies this before the first scenario and records it as `"mantle:suite_setup"` in `uitest-results.json`
(`"ok"` or `"fail: ..."`). A scenario that needs a mob tags it with `UiTestSuite.ALLOW_MOB_TAG`
(`mantle_uitest_allow_mob`, e.g. `mob.addTag(...)` or `Tags:["mantle_uitest_allow_mob"]` in `/summon`) before spawning.

**Input isolation.** From the moment the suite arms (client setup) until it finishes — on every exit path: done, suite
timeout, a failed setup — Mantle's `UiTestInput` swaps the GLFW callbacks vanilla installs for ones that drop the events:
mouse move, buttons, scroll and file drop, keys, typed characters, IME composition and status, and window focus. Moving the mouse over
the window, clicking or typing into it changes nothing in the game. The scenarios still drive the game directly
(`UiTestContext#parkMouse`/`moveMouse` write the `MouseHandler` position, synthetic screen events, `setScreen`, key
presses sent to a screen), which never goes through those callbacks.
- **The cursor stays free and visible.** Vanilla grabs it (disabled and warped to the window center) on `setScreen(null)`
  or a click in the world, but only while the window is focused. With the focus callback dropped, the game keeps seeing an
  unfocused window, as in an unattended background run, so it never grabs or warps the cursor; you can move the mouse in
  and out of the window freely. A cursor already grabbed when the suite arms is released in place (no
  `MouseHandler#releaseMouse`, which would warp it).
- **Pause on lost focus is off while the suite runs** (the run dir's `options.txt` has `pauseOnLostFocus:true`): an
  unfocused window would otherwise open the pause menu over in-world captures. Restored at the end, not saved to disk.
- **Each scenario starts with the cursor at the window center**, where vanilla's grab and release used to leave it, so a
  scenario never depends on where the previous one parked the mouse.
- Not covered: code polling the keyboard directly (`Minecraft#hasShiftDown`/`hasControlDown`/`hasAltDown` read GLFW key
  state), so holding Shift, Ctrl or Alt over the window can still change e.g. a tooltip. Closing the window (title bar X,
  Alt+F4) still works.
- Guarded by `mantle:input_isolation` (tags `suite`, `smoke`): through the callbacks installed on the window it types a
  character into a screen with a focused edit box (vanilla only types into a screen), then fakes a focus gain and feeds cursor
  moves, a left click, a scroll and the W and E keys in the world. It asserts the edit box holds only the character typed into
  the screen directly (control), the parked mouse, the screen (none), the player's position, rotation and hotbar slot are
  unchanged and the cursor is not grabbed (`MouseHandler#isMouseGrabbed` false, GLFW cursor mode normal).

**Progress display.** While the scenarios run, a thin bar on the left edge of the window (XP bar colors: black outline,
dark track, green fill) fills up as scenarios finish, with an `n/N` label above it, and the window title reads
`uitest n/N — <scenario id>` (vanilla's title comes back at the end). The bar is extracted as the last GUI element:
after the HUD layers (`RenderGuiEvent.Post`) with no screen open, else after the screen (`ScreenEvent.Render.Post`) in a new
stratum, so it draws over blur and tooltips. It **never reaches a PNG or a pixel read**: the suite hides it from the tick
before each capture and only captures once a frame was rendered without it (up to 10 ticks, then the capture fails), and
`UiTestContext#captureFrame()` — the suite's capture and every pixel assert — fails with `the progress bar is on the frame
being captured` when the frame still had it. Guarded by `mantle:progress_hidden` (tags `suite`, `smoke`), which opens a
solid-color screen and checks the left strip of two of its frames pixel by pixel: the last frame before the capture wait, read
raw, must show the bar's green fill (control: the bar is drawn over a screen at all), the captured frame must have no trace of
the bar or label; plus that the title shows the progress.

> **Result-reading rule.** A `FATAL` `Error executing task` line in the log overrides an `"ok"` in
> `uitest-results.json` — server-thread scenario guards log there instead of failing the recorded result,
> so check the log even when the JSON (and so the task) looks clean.

World regeneration: delete `run/clientUiTest/saves/UITest`; the `prepareUiTestWorld` task re-extracts the
committed `src/uitest/uitest-world.zip` automatically before `runClientUiTest` whenever the world is
missing.

**Adding a scenario:** implement `slimeknights.mantle.client.uitest.UiTestScenario` — driven by the suite
through `id` → `prepare` → `prepareSettleTicks` (wait) → `open` → `settleTicks` (wait) → capture
(screenshot) → `close` → `cleanup` (`close` runs only after a successful capture, so assert there; `cleanup` runs on
every exit path — success, a failing phase, a timeout — so unregister listeners and restore screens, window size and
game mode there) — and call `UiTestScenarios.register(...)` during client setup, gated on
`UiTestScenarios.isActive()`; see `slimeknights.tconstruct.client.uitest.TinkerUiTestScenarios` as the
template. Registration must happen before world load — the suite snapshots the registry at world load.
Every scenario declares `tags()`: at least one, all known to `UiTestTags` (see [tags](#uitest-tags-and-profiles)); the
suite fails a scenario with no or unknown tags (`fail: tags: …`) instead of running it, and Tinkers' unit test
`TinkerUiTestScenariosTest` checks its scenarios at build time. Pixel asserts read the frame with
`UiTestContext#captureFrame()` (never `RenderTargetCapture` directly), which refuses a frame showing the progress bar.
Each scenario starts with the cursor centered, so hover tooltips can cover the capture: call `UiTestContext#parkMouse()` (top
left corner; the JEI category scenarios re-park every tick until capture). Handlers that read the real cursor instead of
the event (Mantle `BookScreen`, JEI key actions) need `UiTestContext#moveMouse(guiX, guiY)` first; synthetic clicks use
`new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0))`.
**Commands vs `runOnServer`:** `UiTestContext#sendCommand` takes a longer way to the server (network thread, then the server
queue) than `UiTestContext#runOnServer` (straight into the queue), and a scenario's `cleanup` runs in the same tick as the next
one's `prepare`. So server work scheduled with `runOnServer` can run **before** a command sent just earlier: a `clear @s` in one
scenario's cleanup used to wipe the armor or tool the next scenario equipped (flaky `trimmed_armor`, `aoe_outline_high_contrast`).
Empty the inventory with `UiTestContext#clearInventory()` (same as `/clear @s`, scheduled through `runOnServer`), and never let
`runOnServer` work depend on a command sent before it; work scheduled with `runOnServer` before a command always runs first.
Guarded by `mantle:clear_inventory` (tags `suite`, `smoke`), which holds the server thread while the clear and a following give
are scheduled, so a clear going through a command would land after the give and fail it.

### Focused testing — run only the changed domain

When a change touched one area, run just its slice instead of the whole battery.

**Unit** — native Gradle test filtering (`--tests`, glob on the FQCN/method):

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" test --tests "*ResponsiveLayout*"
```

**uitest** — `-PuitestOnly=<substring>` keeps only scenarios whose id path contains the token
(comma-separated for several); e.g. `station_` runs `station_reflow`, `station_collapsed` and
`station_collapsed_overlay`:

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin21-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" runClientUiTest -PuitestOnly=station_
```

The suite logs `uitest: filtered to N of M scenario(s) via mantle.uitest.only=station_`; an empty match (a typo, or an
empty intersection with `-PuitestTags`) finishes cleanly (no crash) but fails `mantle:filter` (`fail: … matched no scenarios`), so
the task fails instead of passing green with nothing run. Property unset = all scenarios run (the default).

#### uitest tags and profiles

`-PuitestTags=<tag,...>` (sysprop `mantle.uitest.tags`) keeps the scenarios having **any** of the listed tags. With
`-PuitestOnly` as well, **both** filters apply (intersection). The suite logs
`uitest: filtered to N of M scenario(s) via mantle.uitest.only=…, mantle.uitest.tags=…`; a tag the suite doesn't know, or
filters keeping no scenario at all, fail `mantle:filter` (so a typo can't pass as an empty green run). In PowerShell, **quote a property holding a comma** — an
unquoted `-PuitestTags=jei,compat` is parsed as an array and the command fails before Gradle starts:

```powershell
$env:JAVA_HOME = "C:\Users\aleja\scoop\apps\temurin25-jdk\current"; & "C:\Users\aleja\DEV\New Tinkers\tinkers\gradlew.bat" -p "C:\Users\aleja\DEV\New Tinkers\tinkers" runClientUiTest "-PuitestTags=jei,compat"
```

Tags are defined once: the shared ones as constants in Mantle's `UiTestTags`, a mod's own with
`UiTestTags.register(tag, description)` (Tinkers: `TinkerUiTestScenarios.STATION` / `ARMOR`). A scenario usually has one
"where" tag (`gui` / `world`) plus its area tags.

| Tag | Defined by | Meaning |
|-----|------------|---------|
| `smoke` | Mantle | quick cross-section: `tinker_station` (station GUI), `casting_pour` (in-world render), `book_materials_and_you`, `jei_melting_category`, plus the suite self-checks — boot is covered by `mantle:suite_setup` (≈15 s in-world, ≈1 min 15 s with Gradle) |
| `suite` | Mantle | checks of the suite itself: `mantle:input_isolation`, `mantle:progress_hidden`, `mantle:clear_inventory` |
| `gui` | Mantle | captures or checks a screen |
| `world` | Mantle | captures the world with no screen open (rigs, HUD) |
| `render` | Mantle | model, texture, atlas or render type checks |
| `fluid` | Mantle | fluid rendering or handling (tanks, casting, fluid blocks) |
| `jei` | Mantle | JEI integration (categories, transfer, lookups) |
| `book` | Mantle | Mantle's book system (pages, structures, export) |
| `compat` | Mantle | integration with another third-party mod (Crafting Tweaks: `crafting_station`, `crafting_tweaks_rotate`) |
| `station` | Tinkers | Tinkers station screens: tinker station, part builder, crafting station (layout tiers, input, JEI into them) |
| `armor` | Tinkers | Tinkers armor: trims, armor worn by the player and other entities |

`mantle:suite_setup` (and `mantle:world_upgrade` / `mantle:mod_loading` / `mantle:filter`) are results the suite records
itself, not scenarios: they are never filtered out.

**Profiles** — which slice to run when:

| Situation | Run |
|-----------|-----|
| Iterating on a fix | the area's tags, e.g. `-PuitestTags=station` or `"-PuitestTags=fluid,render"`; add `-PuitestOnly` to narrow further |
| Datagen phase of a milestone | `-PuitestTags=smoke` |
| Compat phase | `"-PuitestTags=jei,compat"` |
| Runtime phase, phase gates, release | everything (no filter) |

> A tag subset changes which scenario runs before which. A scenario that only passes after a particular neighbour has a
> hidden ordering dependency — fix the scenario rather than the profile.

**gametest** — 1.21.5: `-PgametestOnly=<selector>` forwards to the gametest launcher's `--tests` (wildcards
allowed), e.g. `runGameTestServer -PgametestOnly=tconstruct:smeltery_*`; unset = all 162 tests (161 Tinkers +
`minecraft:always_pass`).

---

## Troubleshooting — symptom → cause → fix (all retry-savers)

| Symptom in output | Real cause | Fix |
|-------------------|-----------|-----|
| `No matching variant of net.neoforged.gradle:userdev` / `Minimum supported Gradle version` | Wrapper ↔ NeoGradle plugin version mismatch | [Prereq 2](#2-upgrade-the-gradle-wrapper-to-921) — pair Gradle 9.2.1 with NeoGradle 7.1.39 (7.1.38 on `1.21.1`) |
| `Unsupported class file major version` / toolchain error / `No compatible toolchains` | Gradle running on Java 17, or can't find Java 21 | [Prereq 1 + 3](#one-time-prerequisites-do-these-first) — set `JAVA_HOME` to temurin21 in the same command |
| `Plugin [id: 'net.neoforged.gradle.userdev'...] was not found` | `settings.gradle` missing the NeoForged maven, or offline | Ensure `maven { url = 'https://maven.neoforged.net/releases' }` is in `settings.gradle pluginManagement`; check network |
| `Could not resolve net.neoforged:neoforge:21.11.48` | Version typo or transient maven issue | Recheck `neoforge_version` in `gradle.properties` against the NeoForged maven |
| `Could not resolve mezz.jei:...` | JEI version not published for this MC/loader | Reconfirm/adjust `jei_version` against the BlameJared maven |
| Command "hangs" for minutes | Cold start + decompile/download (daemon disabled) | **Not a hang** — background it; don't retry |
| `dotnet`/script "not found" in Bash | Scoop PATH unreliable in Bash | Use the **PowerShell** tool |
| `cd: permission denied` prompt | `cd` in a compound Bash command | Use the absolute `GW` form with `-p`, or `Set-Location` in PowerShell |
| uitest client dies in `glfwCreateWindow` / `atio6axx.dll` before the menu | Windows session locked (no usable display for GL) | Unlock the screen and rerun once — not a flake to retry in a loop |

---

## Quick checklist before reporting "it failed"

1. Did you set `JAVA_HOME` to **temurin21** in this exact command?
2. Is the wrapper at **9.2.1** (`gradle-wrapper.properties`), with NeoGradle **7.1.39** in `build.gradle` (7.1.38 on `1.21.1`)?
3. Did you target the **repo** (`-p ...\repo` or `Set-Location`)?
4. Is it actually a failure, or a **slow cold start** that should be backgrounded?

If all four are satisfied and it still fails, it's a real code/port problem — debug it (see the
`systematic-debugging` skill), don't re-run blindly.
