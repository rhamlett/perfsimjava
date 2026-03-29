# Copilot Instructions

## Session Startup (MANDATORY)
- At the start of every new chat session, before answering any questions, read `/memories/repo/project-overview.md` to load project-specific context.
- If the file does not exist, continue normally without error.

## Git & Deployment Restrictions
- NEVER run `git push`, `git commit`, or any command that pushes changes to remote repositories
- NEVER trigger deployments or CI/CD pipelines
- The user will handle all commits, pushes, and deployments manually
- You may stage files with `git add` only when explicitly requested

## Azure CLI Restrictions
- NEVER run any `az` commands that create, modify, or delete Azure resources
- NEVER run commands like: `az webapp create`, `az ad app create`, `az webapp config set`, `az group create`, `az appservice plan create`, `az ad app delete`, `az webapp restart`, `az webapp deploy`, etc.
- Only read-only commands like `az webapp list`, `az account show` are permitted, and only when explicitly requested by the user
- Do NOT make assumptions about Azure infrastructure - ask the user first
