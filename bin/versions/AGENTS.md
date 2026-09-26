# AGENTS.md — Versions

`bin/versions/version.sh` saves the exact commit of eme-server and every plugin in `plugins/` to `bin/versions/<version>.json`, and can put all of them back to those commits later.

Run every command from the server root (the folder that contains `bin/` and `plugins/`).

```bash
bin/versions/version.sh create <version> [-f]   # write bin/versions/<version>.json (-f overwrites)
bin/versions/version.sh <version>               # shorthand for create
bin/versions/version.sh restore <version>       # check out every component at the recorded commit
bin/versions/version.sh list                    # show saved versions and their dates
bin/versions/version.sh status                  # confirm nothing is pending (exit 1 if something is)
```

## Rule: nothing may be pending

A version file stores commits only. Uncommitted edits are not recorded, and commits that were never pushed cannot be fetched on another machine. Restore also skips any component with uncommitted changes. So before creating or restoring a version, eme-server and every plugin must be committed and pushed.

## Check for pending changes

Run:

```bash
bin/versions/version.sh status
```

It checks eme-server and every plugin, prints one line per component, and ends with one of:

- `No pending changes. Safe to create or restore a version.` (exit 0): go ahead.
- `Pending changes found. ...` (exit 1): fix each red line using the steps below, then run `status` again until it is clean.

A component counts as pending when:
- it has uncommitted or untracked files (listed under `<name> has uncommitted changes:`),
- its commit is not on the last-known `origin/<branch>` (`... is not on last-known origin/<branch>`), or
- its `origin/<branch>` was never fetched, or a plugin is not checked out.

Being behind origin is fine, and so is sitting on a commit that a restore put there. `status` doesn't fetch, so it compares against the last fetch. If `status` also finds a saved version that matches every checkout exactly, it prints `Checkouts match saved version: <version>`.

To fix what `status` reports:

1. Plugins:

   ```bash
   bin/plugins.sh status
   ```

   It must finish with `All plugins are up to date and have no pending changes.`. If it lists a plugin instead:
   - **Uncommitted changes:** run `bin/plugins.sh push "<message>"`. This commits, fast-forwards and pushes each plugin.
   - **`local HEAD differs from last-known origin/<branch>`:** run `bin/plugins.sh pull` if you are behind, or `bin/plugins.sh push` if the local commits are your own work.
   - **`Cannot fast-forward` / `diverged`:** stop and ask the user. Do not reset or force anything.

   `bin/plugins.sh status` shows the same plugin problems in more detail.

2. eme-server itself: commit and push with `branchpush`. Pass `.` as the server path, because you are in the server root:

   ```bash
   bin/eme.sh branchpush . "<commit message>"
   ```

   If `branchpush` reports a merge conflict or a rejected push, stop and ask the user.

3. Run `bin/versions/version.sh status` again. Do not continue until it exits 0.

## Create a version

1. Follow **Check for pending changes** above until everything is clean.
2. Create the file:

   ```bash
   bin/versions/version.sh create 1.1
   ```

   If it prints `Warning: <name> has uncommitted changes`, the check was not clean. Fix that plugin, then recreate the file with `-f`.

3. Commit and push the new version file so other servers can restore it:

   ```bash
   bin/eme.sh branchpush . "Add version 1.1"
   ```

The file records the eme-server commit from before the version file was committed. That is expected.

## Restore a version

1. Follow **Check for pending changes** above until everything is clean.
2. Make sure the version file exists: `bin/versions/version.sh list`. If it is missing, pull first with `bin/eme.sh update .`.
3. Restore:

   ```bash
   bin/versions/version.sh restore 1.1
   ```

   It must end with `Version 1.1 restored.`. If it ends with `restored with errors`, read the red lines. Each one names a component that was skipped because it had uncommitted changes or its commit could not be fetched. Fix the cause and run restore again. Components that are already correct are left alone.

4. Restart the server:

   ```bash
   bin/restart.sh
   ```

## What to expect after a restore

- Every component's local branch now points at the recorded commit. `bin/versions/version.sh status` shows these as `(saved version commit)` or `(behind origin/<branch>)` and stays clean. `bin/plugins.sh status` reports `local HEAD differs from last-known origin/<branch>` for them instead. That is expected. Do **not** run `plugins.sh push` at this point.
- Restore keeps a copy of the applied version file at `.git/eme-restored-version.json`, so `status` still recognises its commits after eme-server moves to an older commit.
- If eme-server was moved to an older commit, version files added after that commit disappear from `bin/versions/` until you return to the latest.
- Before moving, each component's previous commit is tagged `pre-restore-<timestamp>`, so nothing is lost.

## Return to the latest

```bash
bin/eme.sh update .
bin/restart.sh
```

`update` pulls eme-server and runs `bin/plugins.sh update`, which resets each plugin to the latest commit on its origin branch.
