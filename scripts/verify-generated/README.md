# verify-generated — datagen golden diff

`Test-GeneratedUnchanged.ps1` fails when a datagen run changed the committed generated resources in a way the current
step did not intend. Use it after every `runData` / `runServerData` during a port (M6 Phase 3 onward): datagen output
must stay byte-identical unless the change says otherwise.

```powershell
# after runData + runServerData in the repo being checked
& "<repo>\scripts\verify-generated\Test-GeneratedUnchanged.ps1" -Repo "<repo or tinkers path>"
# a step that is meant to change some outputs lists them (globs, * crosses folders)
& "...\Test-GeneratedUnchanged.ps1" -Repo "<tinkers path>" -Allow 'src/generated/resources/data/tconstruct/recipe/*,src/generated/resources/assets/tconstruct/models/item/*'
```

Tinkers runs it through the Mantle checkout: `..\repo\scripts\verify-generated\Test-GeneratedUnchanged.ps1 -Repo .`.

- **What it checks:** `git status` under `src/generated` (`-Generated` to override; both Mantle and Tinkers write to
  `src/generated/resources`): modified, added/staged, deleted, renamed and untracked files. Gitignored files are not
  seen — Tinkers ignores its `src/generated/resources/.cache`, Mantle commits its `.cache` files.
- **Exit codes:** `0` nothing unexpected, `1` unexpected changes (each path printed with its git status code), `2`
  usage error (no such folder, not a git work tree).
- **Fresh Tinkers worktrees** have no `.cache`, so a single datagen run deletes the other run's files: run **both**
  `runData` and `runServerData` before checking (see the datagen notes in `docs/COMMANDS.md`).
- **Self-test:** `Test-VerifyGenerated.ps1` builds a throwaway git repo in the temp folder and checks the exit code of
  each case (clean, modified, allowed by path/glob/comma list, untracked, deleted, staged, outside the folder,
  gitignored, missing repo). Run it after editing the checker.

The checker only compares against what git has committed: commit (or stash) unrelated generated changes first, or the
check reports them too.
