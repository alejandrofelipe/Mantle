# Release tags: naming and message template

Every Mantle and TinkersConstruct release in this fork gets an **annotated** tag. Its message is the release note: it is
what `git tag -n`, GitHub's tag page and the next milestone's plan read. Write it to this template so all tags look the
same. The template was codified on M7 from the M5/M6 tags.

## Naming and placement

- Tag name: `v<mc>-<version>`, e.g. `v26.1.2-1.18` (Mantle) and `v26.1.2-3.17.0` (Tinkers).
- Point the tag at the **release commit**, i.e. the `gradle.properties` bump (`build: release Mantle <ver> for MC <mc>` /
  `build(tinkers): release <ver> for MC <mc> (requires Mantle <ver>)`), not at a later docs commit.
- Create it with `git tag -a <name> <commit> -F <message-file>` (a file keeps the blank lines and avoids shell quoting),
  then `git push origin <name>`. Tags, tag pushes and the GitHub default-branch change are outward-facing: they need the
  owner's explicit OK.

## Style rules

- **Plain text, English.** Use `->`, not `→`. No Markdown headings or tables. Inline code names stay bare (no backticks).
- **Line 1 is the title**, then a blank line. After that, each section is **one paragraph on one line**, labelled as
  below, with a blank line between sections. A patch release may use `- ` bullets, one per fix.
- **Every fact comes from the plan, the migration guide or the release-state battery.** Counts are the ones from the
  battery run on the release commit, not from an earlier phase.
- Group API changes by area (Recipes/loot, Rendering/models, GUI, Data/components, uitest, Tooling). Name the members,
  as the downstream table does.
- "Fixed during the port" lists only user-visible bugs that also existed in the previous release (say since when), so a
  reader can decide on a backport.
- "Known issues" is the guide's "Known issues" section, shortened. Keep items that still apply from earlier milestones.

## Milestone release: Mantle

```
Mantle <ver> for Minecraft <mc> / NeoForge <nf>

Roadmap milestone <Mx>[ (<versions folded in and why>)]. [Toolchain: <JDK/build changes downstream must follow>.] Breaking API changes for downstream: <area: change; area: change; ...>. [New API: <...>.] [uitest: <suite behaviour changes>.] [Tooling: <scripts>.] [Fixed during the port: <bug> (since <version>); ...]

Validated through TinkersConstruct <ver>: build + <n> unit + <n> gametest + <n>/<n> uitest. Gotchas and the full downstream API table: docs/migration/<from>-to-<to>.md
```

## Milestone release: Tinkers

```
Tinkers Construct <ver> for Minecraft <mc> / NeoForge <nf>

Roadmap milestone <Mx>[ (<folded in>)]. Requires Mantle <ver> (<why>). Battery: build + <n> unit (<k> skipped) + <n> gametest + <n>/<n> uitest (<s> scenarios + mantle:suite_setup; PNGs reviewed against a <prev> reference run); runData + runServerData leave the tree clean[ (<golden diff note>)]. Compat: JEI <v>[ (<runtime note>)], Crafting Tweaks <v> + Balm <v>[ + kuma <v>], JSON Things <v>; <excluded mods>.

Notable: <the port's main moves and the behaviour changes a player or addon author notices; new guards (gametests, uitest scenarios, pixel asserts)>. Fixed during the port: <bug> (since <version>); ...

Known issues: <item>; <item>; ... Gotchas: repo/docs/migration/<from>-to-<to>.md (Mantle repo).
```

## Patch release (either repo)

```
<Mantle|Tinkers Construct> <ver> for Minecraft <mc> / NeoForge <nf>

Patch release.
- <fix: what the player saw, then the cause>.
- <fix>.

[Adds <tests that guard the fixes>.]

Battery: build + <n> unit + <n> gametest[ + <n>/<n> uitest]. Requires Mantle <ver>[ (<ver> recommended)].
```

For Mantle patches, the last line is `Validated through TinkersConstruct <ver>: build + <n> unit + <n> gametest.`
For a backport hotfix series (the same fix on several release lines), each tag names the bug once and its own battery.

## Checklist before tagging

1. The release commit is on the release branch and pushed; the tree is clean.
2. The battery on that commit is green, and its counts are in the message.
3. The release jars (built with `ARTIFACT_VERSION`, each repo separately; see [COMMANDS.md](COMMANDS.md)) carry no `.DEV`.
4. The message follows this template. Check it with `git tag -n100 <name>` before pushing.
