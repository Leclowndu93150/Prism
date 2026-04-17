---
sidebar_position: 5
---

# CI/CD

## GitHub Actions

Here is a recommended workflow for building and publishing your mod with Prism.

### Build workflow

Create `.github/workflows/build.yml`:

```yaml
name: Build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v4

      - run: ./gradlew build

      - uses: actions/upload-artifact@v4
        with:
          name: jars
          path: |
            versions/*/fabric/build/libs/*.jar
            versions/*/neoforge/build/libs/*.jar
            versions/*/forge/build/libs/*.jar
            !**/*-dev.jar
            !**/*-sources.jar
```

### Publish workflow

Create `.github/workflows/publish.yml`:

```yaml
name: Publish

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v4

      - run: ./gradlew build prismPublishAll
        env:
          CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
          MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
          # GITHUB_TOKEN is injected by Actions; Prism picks it up automatically.
          # Only set these if the corresponding `publishing { … }` block is configured:
          GITEA_TOKEN: ${{ secrets.GITEA_TOKEN }}
          GITLAB_TOKEN: ${{ secrets.GITLAB_TOKEN }}
          DISCORD_WEBHOOK_URL: ${{ secrets.DISCORD_WEBHOOK_URL }}
```

### Required secrets

Add these to your repository settings (Settings > Secrets and variables > Actions). Only the ones matching platforms you actually publish to are needed.

| Secret | Source |
|--------|--------|
| `CURSEFORGE_TOKEN` | [CurseForge API tokens](https://www.curseforge.com/account/api-tokens) |
| `MODRINTH_TOKEN`   | [Modrinth PAT settings](https://modrinth.com/settings/pats) |
| `GITHUB_TOKEN`     | Auto-injected by GitHub Actions. Outside Actions, set `GH_TOKEN` to a personal access token instead. |
| `GITEA_TOKEN`      | Your Gitea user settings → Applications → Generate New Token |
| `GITLAB_TOKEN`     | GitLab → User Settings → Access Tokens (scope: `api`) |
| `DISCORD_WEBHOOK_URL` | Your Discord server → Channel settings → Integrations → Webhooks |
