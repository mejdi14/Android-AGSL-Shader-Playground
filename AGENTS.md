# AGENTS.md

Read [CLAUDE.md](CLAUDE.md) for project-specific instructions.

## Agent workflow

- **Clarify the prompt first.** Before planning or acting on each user request, organize it into a concise working brief. Capture the goal, relevant context, constraints, and expected deliverable. Preserve the user's intent and include only details needed to do the work. Use this brief when delegating.
- **Delegate straightforward work.** When running a higher-capability model, delegate self-contained tasks that need little reasoning to a lighter model when the client supports model selection for subagents. Give the subagent a clear scope and completion criteria. Keep complex decisions with the main agent and verify delegated results before relying on them.
- **Commit every logical change.** In a Git repository, verify and commit each completed logical change before handing work back. Stage only the task's changes, keep unrelated work out of the commit, and follow the project's commit conventions.
