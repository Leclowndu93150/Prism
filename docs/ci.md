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

      - run: ./gradlew build publishAllMods
        env:
          CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
          MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
```

### Required secrets

Add these to your repository settings (Settings > Secrets and variables > Actions):

| Secret | Source |
|--------|--------|
| `CURSEFORGE_TOKEN` | [CurseForge API tokens](https://www.curseforge.com/account/api-tokens) |
| `MODRINTH_TOKEN` | [Modrinth PAT settings](https://modrinth.com/settings/pats) |
