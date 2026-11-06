# Local commit workflow

Use the current repository [AGENTS.md](../AGENTS.md) and the user's current
instructions for authorization, identity, commit format and validation.

When local commits are requested, prepare a dependency-ordered series before
staging. A prompt or milestone may contain several independently reviewable
changes; it does not imply a single commit or a predetermined subject.

1. Inspect status, staged and unstaged changes, and recent history. Identify
   task-owned paths and hunks; preserve unrelated work.
2. Describe each independent change and its dependencies. Separate reusable
   refactors, feature behavior, additional regression coverage and documentation
   when each resulting revision is valid. Include changed test expectations with
   the behavior that requires them. Keep genuinely inseparable changes together;
   do not impose a fixed commit count.
3. Validate each intermediate code revision using its actual tree, preferably in
   an isolated worktree when later edits are already present. Run affected checks
   and the repository's required final build. A successful final-tree build alone
   does not validate earlier revisions. Documentation needs link/path and diff
   checks. Record actual checks and pending gates honestly.
4. Stage explicit paths or hunks. Review the full staged diff, its stat and
   `git diff --cached --check`. Confirm it serves one concrete purpose and that its
   dependencies are already committed. Do not use blanket staging commands.
5. Choose an English `type(scope): imperative description` subject from that
   staged change. Explain behavior, reason and compatibility for substantial
   changes, with `Validation:` for checks actually performed. Use the configured
   Git identity and respect hooks. Never add co-author trailers, AI credits or
   automatic agent signatures. For multiline messages, use a UTF-8 message file
   with `git commit -F`.
6. Inspect every new commit's contents and full message, then final status.
   Report hashes, scopes, verification and remaining changes. A commit does not
   complete a pending acceptance gate. Do not publish, push, merge, tag or rewrite
   existing history without the user's authorization for that action.

Prompt packs should link here instead of duplicating this procedure or supplying
ready-made commit subjects. Revisions to prompts do not require rerunning work
that is already complete.
