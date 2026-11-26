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
| `Find-ApiRenames.ps1` + `FindApiRenames.java` | jar-to-jar API diff: generates `maps/<version>.txt` (class rules) and `maps/<version>-members.tsv` (member table) from `maps/<version>.overrides.txt` (see below); tests: `Test-FindApiRenames.ps1` |
| `Invoke-CompilerRenames.ps1` + `CompilerRenames.java` | compiler-driven member renames from a flow-gate compile log + the member table (see below); tests: `Test-CompilerRenames.ps1` |
| `flow.init.gradle` | Gradle init script: javac keeps reporting past attribution errors, no 100-error cap |
| `Test-ApiFixtures.ps1` | the two tiny fixture APIs both Java tools are tested on (dot-sourced) |
| `Convert-NullableToJSpecify.ps1` | jsr305 `javax.annotation.*` → JSpecify `@Nullable` with type-use placement (see below); tests: `Test-ConvertNullableToJSpecify.ps1` |

## Map format

One rule per line, `<kind> <old> <new>`, fields separated by whitespace (so no spaces inside `old`/`new`). Blank lines and
lines starting with `#` are ignored; trailing comments are a syntax error. Rules apply **top to bottom**, each on the
output of the previous ones — put specific rules (import FQNs, longer names) before the general token rule.

| Kind | Matches | Example |
|------|---------|---------|
| `token` | whole word: `(?<![\w$])OLD(?![\w$])`, `OLD` taken literally | `token ResourceLocation Identifier` renames `ResourceLocation` but not `ResourceLocationLoadable`, `MyResourceLocation`, `RESOURCE_LOCATION` |
| `literal` | exact, case-sensitive substring | `literal net.minecraft.ResourceLocationException net.minecraft.IdentifierException`, `literal readResourceLocation( readIdentifier(` |
| `class` | a moved/renamed class, two FQNs (`$` = nested): the dotted FQN as a whole word in every file (imports, static imports, inline FQNs, javadoc); the simple name (not after a `.`) **only in files that import the old class**; files importing the old outer of a nested class get `Outer.Inner` → `NewOuter.NewInner` (+ an import of the new outer when it is another top-level class). Wildcard imports are not followed | `class net.minecraft.client.resources.model.Material net.minecraft.client.resources.model.sprite.SpriteId` renames `Material` in files importing it, never Tinkers' own `Material` |

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

## Deriving the map from the jars (`Find-ApiRenames.ps1`, M7+)

`Find-ApiRenames.ps1 -OldJar <old raw.jar> -NewJar <new raw.jar> -Overrides maps/<v>.overrides.txt -Out <dir>
-MapOut maps/<v>.txt -MembersOut maps/<v>-members.tsv` runs `FindApiRenames.java` (Java 24+, JDK ClassFile API, no
dependency; ~4 s for 10k classes). Both maps are **generated — edit the overrides file and regenerate**. Class matching,
in order (`classes.tsv` records which rule matched each pair):

1. `override` — `class` lines of the overrides file;
2. `moved` — exactly one added class with the same simple name, **unless the shapes differ** (similarity < 0.2, inherited
   members included): then it is a suspect and the next steps may find a better class;
3. `inner-classes(n)` — a removed outer class whose nested classes reappear under one new outer (majority vote);
   `outer-renamed` / `nested-classes` — nested classes follow their outer, and an outer follows its mapped nested classes;
4. `similarity[-xpkg]` — self-reference-aware Jaccard over method/field signatures (+ supertypes), same package first
   (≥ 0.5), then across packages (≥ 0.55, or ≥ 0.3 when the camel-case name tokens overlap ≥ 0.75), margin 0.2 over the
   runner-up, repeated while it finds pairs (each pair improves the descriptors of the next round). Enum constants count
   as fields, so `ClickType` → `ContainerInput` matches;
5. `shape-over-name` / `moved-split` / `moved-low-shape` — suspects: a clearly better shape anywhere wins (old
   `model.Material` → `sprite.SpriteId`, not the new `sprite.Material`); a same-name class that *extends* the shape match
   is a split and keeps the name (`BlockAndTintGetter`); otherwise the same-name pair is kept with a warning;
6. `swap` — an FQN that still exists but now names another class whose old shape lives in an added class
   (`BlockModel` record → `CuboidModel`, `BlockModel` is a new interface).

Every semantic swap is printed as a `WARNING` (`warnings.txt`): "same simple name, different shape" and "same FQN,
different class/shape". Review them before applying. Member renames (`members.tsv`: kind, old/new owner, old/new name,
old/new descriptor, static, how): per class pair, removed vs added members grouped by remapped descriptor; a 1×1 group is
`desc-unique(score)`, a larger group is paired greedily by name similarity (`name-tiebreak(score)`, ≥ 0.3, exact ties
skipped) — `Screen#render`/`renderBackground` → `extractRenderState`/`extractBackground`. The score is the name similarity:
a low score on a `desc-unique` row (`incrementFrameNumber` → `endFrame`) is a guess the applier lists for review.
Overrides file lines: `class <old> <new>`, `method|field <oldOwner> <oldName> <oldDesc|*> <newName>`,
`hint <owner> <name> <message>` (printed by the applier for an override/call with no rename, e.g. `renderBg`),
`rule <kind> <old> <new>` (copied into the map). `-Explain "old.Fqn,new.Fqn"` prints the similarity features of a pair.

## Compiler-driven member renames (`Invoke-CompilerRenames.ps1`, M7+)

Member renames by name alone over-match (`getId`, `draw`), and most of them are hidden until the class rules are applied
(calls on an unresolved `GuiGraphics` report nothing). So after the class map, loop: compile with the flow gate
(`compileJava --init-script flow.init.gradle`) → `Invoke-CompilerRenames.ps1 -Log <log> -Members maps/<v>-members.tsv
-Jar <new raw.jar> -Root <repo>[,<other source roots>]` (dry run, then `-Apply`) → compile, until `edits=0`.
`CompilerRenames.java` indexes every `src/**/*.java` under the roots (a small lexer-level scanner: types, anonymous
classes, method headers, `@Override`) and the jar's class hierarchy, then edits only:

- **(a)** `cannot find symbol: method|variable X` (also inside `invalid method reference`) at javac's caret, when the
  location type — or, for `super.x(..)`/`this.x(..)` (javac prints no location), the enclosing class — or one of its
  supertypes (source chain, then jar) has a rename `X → Y` with a compatible arity; several fitting new names are
  disambiguated by the argument types javac prints, else listed as ambiguous;
- **(b)** `method does not override or implement a method from a supertype`: the declaration is renamed when a supertype
  method with that name/arity was renamed (refused when the class already declares the new name);

and reports: unresolved overrides and hinted calls (with the overrides file's `hint`), ambiguous spots, low-similarity
renames used, and **(c) possible dead overrides** — methods without `@Override` whose name/arity matches a renamed or
hinted supertype method that no longer exists (they compile and are never called: the `renderBg` trap; also run it with an
empty log after the class map to get only this report). Gradle repeats every error in its failure summary, indented; the
tool dedupes. Edits keep encoding, BOM and line endings. Exit `0` ok, `1` bad arguments. Last line:
`errors=<n> edits=<n> files=<n> ambiguous=<n> unresolvedOverrides=<n> deadOverrides=<n>`.

Known limits: anonymous classes are typed by their `new X(..)` header (a lambda with a block body inside that header
hides it); enum constant bodies and local classes inside lambdas are not indexed; supertypes outside the jar and the
roots (NeoForge, libraries) end the chain, so renames inherited through them are missed; removed supertype methods with no
rename and no `hint` are only visible as unresolved overrides, not as dead overrides.

## Checking the access transformer (`Find-StaleAccessTransformer.ps1`)

The rename map never touches `accesstransformer.cfg`, and NeoForge skips an entry whose class or member no longer exists
without failing the build (M6: Tinkers' four arrow entries went stale when the classes moved to `projectile.arrow`).
`Find-StaleAccessTransformer.ps1 -At <accesstransformer.cfg> -Jar <target raw.jar>` checks every `<access> <class>
[<member>]` line with `javap`: a missing class is reported with same-simple-name candidates (moved package), a method needs
its name **and** descriptor, a field its name. Output `entries=<n> problems=<n>`; exit `0` clean, `1` bad arguments, `2`
problems. `javap` comes from `JAVA_HOME` (or `-Javap`). Run it on both repos after every version bump, after the rename.

## jsr305 → JSpecify `@Nullable` (`Convert-NullableToJSpecify.ps1`)

M7 Phase 0.6 (decision D-N2), run once per repo on `1.21.11`. `-Root <repo>` (dry run; `-Apply` rewrites, keeping line
endings and BOMs; comments, strings and text blocks are never touched):

- `import javax.annotation.Nullable;` / `CheckForNull` → one `import org.jspecify.annotations.Nullable;`; `@CheckForNull` and
  `@javax.annotation.Nullable` → `@Nullable` (or the JSpecify FQN when the file has no import).
- **Placement**: JSpecify `@Nullable` is `TYPE_USE`, so `@Nullable Outer.Inner` → `Outer.@Nullable Inner` (javac rejects the
  old form for nested classes and package prefixes) and `@Nullable String[]` → `String @Nullable []` (the old form now means
  nullable *elements*). Varargs, primitives, `void`/`var` and generic outer types are reported, not rewritten.
- `@Nonnull` is removed (packages are `@NullMarked`), except on a field of a file with a Lombok constructor/setter generator,
  where it becomes `lombok.NonNull` so Lombok keeps generating the same null check. Other `javax.annotation` imports are
  dropped when unused, else kept and reported.

Every placement rewrite is listed (`file:line before -> after`); summary `files=… changed=… imports=… checkForNull=…
nonnullRemoved=… nonnullLombok=… qualified=… arrays=… fqn=… notes=…`. Verify with a full `build` and a bytecode comparison
(`javap -c -p` of every class before/after, constant-pool indices stripped): annotations are not code, so it must be identical.
