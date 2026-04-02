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

        // Display name on CurseForge/Modrinth
        // Defaults to the JAR filename (e.g. mymod-1.21.1-NeoForge-1.0.0.jar)
        // displayName = "My Mod v1.0.0"

        curseforge {
            accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            projectId = "123456"
        }

        modrinth {
            accessToken = providers.environmentVariable("MODRINTH_TOKEN")
            projectId = "abcdef12"
        }

        // Global publishing dependencies (apply to ALL versions and loaders)
        dependencies {
            requires("fabric-api")
            optional("jei")
        }
    }
}
```

## Publishing dependencies

Declare mod relationships that show up on CurseForge and Modrinth project pages. Dependencies can be set at three levels, and they stack:

### Global (all versions, all loaders)

```kotlin
publishing {
    dependencies {
        requires("jei")
        optional("jade")
        incompatible("optifine")
    }
}
```

### Per version (all loaders for that version)

```kotlin
version("1.21.1") {
    publishingDependencies {
        requires("fabric-api")
    }
}
```

### Per loader (specific loader for specific version)

```kotlin
version("1.21.1") {
    fabric {
        publishingDependencies {
            requires("fabric-api")
            optional("modmenu")
        }
    }
    neoforge {
        publishingDependencies {
            optional("jei")
        }
    }
}
```

### Platform-specific dependencies

If a slug differs between CurseForge and Modrinth, use platform-specific blocks:

```kotlin
publishingDependencies {
    curseforge {
        requires("jei")           // CurseForge slug
    }
    modrinth {
        requires("jei-mod")       // Modrinth slug (if different)
    }
}
```

Or use the `platform` parameter:

```kotlin
publishingDependencies {
    requires("fabric-api", PublishingPlatform.MODRINTH)
    requires("fabric-api", PublishingPlatform.CURSEFORGE)
    requires("some-mod", PublishingPlatform.BOTH)  // default
}
```

### Dependency types

| Method | CurseForge | Modrinth |
|--------|------------|----------|
| `requires(slug)` | Required | Required |
| `optional(slug)` | Optional | Optional |
| `incompatible(slug)` | Incompatible | Incompatible |
| `embeds(slug)` | Embedded | Embedded |

## What Prism configures

For each loader subproject, Prism applies mod-publish-plugin and sets:

- `file` to the output JAR of that subproject
- `modLoaders` to the loader name (`fabric`, `neoforge`, or `forge`)
- `minecraftVersions` to the Minecraft version of that subproject
- `changelog`, `version`, and `type` from the root configuration
- CurseForge and Modrinth credentials from the root configuration
- Publishing dependencies from global + version + loader levels (stacked)

## Maven publishing

Publish your mod as a library to Maven repositories for other mods to depend on.

### Local Maven

```kotlin
publishing {
    mavenLocal()
}
```

### Custom Maven repository

```kotlin
publishing {
    maven {
        name = "MyMaven"
        url = "https://maven.example.com/releases"
        credentialsFromEnv("MAVEN_USER", "MAVEN_PASS")
    }
}
```

### GitHub Packages

```kotlin
publishing {
    githubPackages("YourName", "your-repo")
}
```

Automatically uses `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables.

### Multiple Maven repositories

```kotlin
publishing {
    mavenLocal()
    githubPackages("YourName", "your-repo")
    maven {
        name = "Production"
        url = "https://maven.example.com/releases"
        credentials("admin", "password")  // or credentialsFromEnv()
    }
}
```

### Maven artifact coordinates

Each loader subproject publishes with:
- **groupId**: your project group
- **artifactId**: `{modId}-{mcVersion}-{loader}` (e.g. `mymod-1.21.1-neoforge`)
- **version**: your project version

## Running

```bash
# CurseForge + Modrinth
./gradlew publishAllMods
./gradlew :1.21.1:fabric:publishMods     # specific target

# Maven
./gradlew publishAllMaven
./gradlew :1.21.1:publish                # specific target
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
