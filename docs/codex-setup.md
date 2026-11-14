# Codex skills and workflow

Inspected the installed skill catalog and the system skill-installer instructions.
Listed the official openai/skills curated catalog with its list-skills.py helper.
Read each selected SKILL.md before installation; installed through the official helper:

```text
python <skill-installer>/scripts/install-skill-from-github.py --repo openai/skills --path skills/.curated/gh-address-comments skills/.curated/gh-fix-ci
```

Installed user-wide (available to Codex next turn):
- [gh-address-comments](https://github.com/openai/skills/tree/main/skills/.curated/gh-address-comments):
  inspect and address human PR feedback.
- [gh-fix-ci](https://github.com/openai/skills/tree/main/skills/.curated/gh-fix-ci):
  inspect GitHub Actions failures and repair approved changes.

The source catalog is https://github.com/openai/skills/tree/main/skills/.curated.
No appropriate general Kotlin/Android, architecture, JUnit/TDD or documentation skill
was present in that catalog. Those conventions are configured directly in root AGENTS.md.
No unreviewed third-party skills were installed. Skills are not vendored into this repository.

The Atlassian MCP connection was verified and CB-10 and CB-1 read before implementation.
Use that connection for ticket context and PR handoff; do not store Atlassian credentials.
GitHub CLI authentication was verified with network access. Ordinary sandbox calls may
misreport authentication when the credential/network helper cannot run.
