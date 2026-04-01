---
sidebar_position: 4
---

# Publishing

Prism wraps [mod-publish-plugin](https://github.com/modmuss50/mod-publish-plugin) to publish your mod JARs to CurseForge and Modrinth.

Publishing is optional. If you do not configure the `publishing` block, the mod-publish-plugin is never applied.

## Configuration

```kotlin
prism {
    publishing {
        changelog = "Fixed bugs and added features."
        // Or read from a file:
        // changelogFile = "CHANGELOG.md"

        type = ReleaseType.STABLE  // STABLE, BETA, or ALPHA

        curseforge {
            accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            projectId = "123456"
        }

        modrinth {
            accessToken = providers.environmentVariable("MODRINTH_TOKEN")
            projectId = "abcdef12"
        }
    }
}
```

## What Prism configures

For each loader subproject, Prism applies mod-publish-plugin and sets:

- `file` to the output JAR of that subproject
- `modLoaders` to the loader name (`fabric`, `neoforge`, or `forge`)
- `minecraftVersions` to the Minecraft version of that subproject
- `changelog`, `version`, and `type` from the root configuration
- CurseForge and Modrinth credentials from the root configuration

## Running

```bash
# Publish all targets
./gradlew publishAllMods

# Publish a specific target
./gradlew :1.21.1:fabric:publishMods
```

## Access tokens

Store tokens as environment variables. Never commit them to source control.

```bash
export CURSEFORGE_TOKEN=your_token_here
export MODRINTH_TOKEN=your_token_here
```

In CI (GitHub Actions):

```yaml
env:
  CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
  MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
```
