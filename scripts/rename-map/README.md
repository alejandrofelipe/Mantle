# rename-map — mechanical renames between Minecraft versions

Applies a map of `old → new` renames (import FQNs, class names, member names) to the Java sources of a repo, so the
mechanical part of a version bump (e.g. MC 1.21.11's `ResourceLocation` → `Identifier`) is one reviewable,
reproducible commit instead of an ad-hoc script per milestone. Default is a **dry run**.

| File | What |
|------|------|
| `Invoke-RenameMap.ps1` | the tool (`-Root`, `-Map`, `-Apply`, `-Include`, `-Top`) |
| `maps/<version>.txt` | one map per milestone (`maps/1.21.11.txt` = M6 plan D-R1 steps 1–2) |
| `Test-RenameMap.ps1` | self-contained tests (no Pester): `pwsh -NoProfile -File Test-RenameMap.ps1` |
| `Find-StaleAccessTransformer.ps1` | checks `accesstransformer.cfg` entries against the target jar (see below); tests: `Test-FindStaleAccessTransformer.ps1` (needs `JAVA_HOME` = a JDK) |

## Map format

One rule per line, `<kind> <old> <new>`, fields separated by whitespace (so no spaces inside `old`/`new`). Blank lines and
lines starting with `#` are ignored; trailing comments are a syntax error. Rules apply **top to bottom**, each on the
output of the previous ones — put specific rules (import FQNs, longer names) before the general token rule.

| Kind | Matches | Example |
|------|---------|---------|
| `token` | whole word: `(?<![\w$])OLD(?![\w$])`, `OLD` taken literally | `token ResourceLocation Identifier` renames `ResourceLocation` but not `ResourceLocationLoadable`, `MyResourceLocation`, `RESOURCE_LOCATION` |
| `literal` | exact, case-sensitive substring | `literal net.minecraft.ResourceLocationException net.minecraft.IdentifierException`, `literal readResourceLocation( readIdentifier(` |

## Output

Dry run and `-Apply` print the same report: hits per rule (and files per rule), hits per file (top `-Top`, default 20,
plus the total), a **WARNING** list of hits that look like they are inside a Java string literal (odd number of unescaped
`"` before the hit on its line — still counted and still replaced, just flagged for the diff review), and
`rules=<n> files=<n> hits=<n> stringHits=<n>`. `-Apply` also prints `written=<n>`; it rewrites only changed files and keeps
each file's line endings (CRLF/LF) and BOM (or lack of one). Exit code: `0` ok, `1` bad root/map path or a non-UTF-8
file, `2` bad map syntax.

## Usage

Default `-Include` is `src/**/*.java` (main, test, gametest, uitest, generated). Mantle:

```powershell
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Invoke-RenameMap.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\repo" -Map "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\maps\1.21.11.txt"
```

Tinkers uses the same tool from the Mantle checkout (`../repo/scripts/rename-map/` relative to `tinkers/`):

```powershell
pwsh -NoProfile -File "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\Invoke-RenameMap.ps1" -Root "C:\Users\aleja\DEV\New Tinkers\tinkers" -Map "C:\Users\aleja\DEV\New Tinkers\repo\scripts\rename-map\maps\1.21.11.txt"
```

Add `-Apply` to rewrite. Narrow with `-Include "src/main/**/*.java,src/test/**/*.java"`.

## Per-milestone workflow

1. **Phase 0:** write `maps/<version>.txt` from the NeoForge/MC primer (header cites the source and the plan decision;
   compiler-driven changes — e.g. receiver-dependent method renames like `ResourceKey#location()` — stay out of the map).
2. Dry run on both repos; check the per-rule counts against the Phase 0 compile baseline and review every
   string-literal warning.
3. Apply, per repo, in **its own commit** (no other change in it; review the diff for strings and javadoc).
4. Compile and fix the rest by hand (compiler-driven renames, cascades).

## Seeding moved-class rules from the jar (`Find-MovedClasses.ps1`)

Primers are non-exhaustive about classes that changed package. `Find-MovedClasses.ps1 -Root <repo> -Jar <target raw.jar>`
lists every `net.minecraft`/`com.mojang` import of the repo that no longer exists in the target jar and, when exactly one
class with the same simple name exists there, prints `literal <old>;`/`literal <old>.` rules for it (ambiguous and
removed classes are printed as comments). Review the output and append it to the milestone's map. The jar is NeoForm's
`repo/build/neoForm/neoFormJoined<version>-*/raw.jar` (exists after the first build on the new version). M6 used it for
Tinkers' 33 moved classes (entity/projectile/model subpackages, `RenderType`, `GameRules`).

## Checking the access transformer (`Find-StaleAccessTransformer.ps1`)

The rename map never touches `accesstransformer.cfg`, and NeoForge skips an entry whose class or member no longer exists
without failing the build (M6: Tinkers' four arrow entries went stale when the classes moved to `projectile.arrow`).
`Find-StaleAccessTransformer.ps1 -At <accesstransformer.cfg> -Jar <target raw.jar>` checks every `<access> <class>
[<member>]` line with `javap`: a missing class is reported with same-simple-name candidates (moved package), a method needs
its name **and** descriptor, a field its name. Output `entries=<n> problems=<n>`; exit `0` clean, `1` bad arguments, `2`
problems. `javap` comes from `JAVA_HOME` (or `-Javap`). Run it on both repos after every version bump, after the rename.
