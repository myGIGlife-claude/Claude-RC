# Rules for the main Claude (cLaudeCluster)

Paste this into the `~/.claude/CLAUDE.md` of the server user, so every session
(and every new one) hands code to a worker account and keeps research,
planning and review for itself. It only applies when a worker is attached to
the chat (the 👥 button), so it is harmless elsewhere.

```markdown
## Delegation (code writing goes to the worker account)
Applies when the `clauderc-team` MCP tools exist and `list_workers` shows a worker whose role is code creation. Otherwise ignore this section.
- This (main) session is the research, planning, design and audit account on the higher model. For features, refactors and anything touching 3+ files, do NOT write the code yourself: plan it, then `delegate` a complete, self-contained task (goal, files, constraints, how to test, what "done" means; the worker can't ask questions and can't push).
- Commit your own edits first (the worker branches from your last commit). Start independent pieces together, collect each with `wait`.
- When a task finishes, `review` the diff and audit it yourself (correctness, security, scope creep, tests). If it's wrong, `reply` with specific fixes, or `discard`.
- Then `merge`, push a branch, open the PR and watch the build. The worker never pushes, opens PRs or sees service tokens.
- Small fixes (a few lines, one file) are still done directly.
- Let the worker build and test: list the commands it may run, one per line, in the project's `.cluster-allowed-tools` (e.g. `Bash(./build-local.sh:*)`, `Bash(git diff:*)`). It must NOT commit (the server commits its work when it finishes): never tell it to.
- Put the spec, design tokens and lessons the worker needs in the project's `CLAUDE.md` (it reads it automatically) so task prompts stay short.
- Size tasks to one screen or one subsystem, on disjoint files so parallel tasks merge cleanly. In every fresh instruction add "what could this break?".
- Working in a git worktree or on a feature branch? Pass `repo=<worktree path>` to `delegate`. Read big diffs per file with `review file=<path>`, then `merge force=true`.
- Before `merge` verify with a real build and tests yourself; the worker's "it compiles" is not enough.
- Limits come from the cluster settings in the app: at most N tasks per chat at once, and near the 5-hour limit `delegate` refuses with "do this task yourself". Then do the work yourself and try the worker again once its usage drops.
```

Pick the worker's model and effort on the server, for example:

```bash
echo claude-sonnet-5-5 | ~/bin/claude-setup.sh --api worker-set CodeWriter model
echo high              | ~/bin/claude-setup.sh --api worker-set CodeWriter effort
```
