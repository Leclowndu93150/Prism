---
sidebar_position: 4
---

# Publishing

Prism publishes your mod JARs directly to CurseForge, Modrinth, GitHub Releases, Gitea, and GitLab, then optionally posts an announcement to Discord. The upload code is a vendored port of [mod-publish-plugin](https://github.com/modmuss50/mod-publish-plugin) (MIT) — Prism owns the task graph end-to-end, with no external publish plugin applied.

Publishing is optional. If you do not configure any `curseforge { }`, `modrinth { }`, `github { }`, `gitea { }`, or `gitlab { }` block inside `publishing { }`, no publish tasks are registered.

## Configuration

```kotlin
prism {
    publishing {
        changelog = "Fixed bugs and added features."
        // Or read from a file:
        // changelogFile = "CHANGELOG.md"
        // Per-version overrides are also supported (see below)

        type = STABLE  // STABLE, BETA, or ALPHA

        // Display name on CurseForge/Modrinth
        // Defaults to the JAR filename (e.g. mymod-1.21.1-NeoForge-1.0.0.jar)
        // displayName = "My Mod v1.0.0"

        // Optional override for CurseForge/Modrinth artifact selection
        // artifactTask("reobfJar")
        // artifactFile("build/libs/custom.jar")

        curseforge {
            accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            projectId = "123456"
        }

        modrinth {
            accessToken = providers.environmentVariable("MODRINTH_TOKEN")
            projectId = "abcdef12"
        }

        github {
            // accessToken = providers.environmentVariable("GITHUB_TOKEN")  // default: GITHUB_TOKEN then GH_TOKEN
            repository = "MyName/my-mod"
            // tagName = "v1.0.0"        // default: modVersion
            // commitish = "main"
            // prerelease = false
            // reuseExistingRelease = true
        }

        gitea {
            accessToken = providers.environmentVariable("GITEA_TOKEN")
            apiEndpoint = "https://gitea.example.com/api/v1"
            repository = "MyName/my-mod"
        }

        gitlab {
            accessToken = providers.environmentVariable("GITLAB_TOKEN")
            projectId = 12345L
            // apiEndpoint = "https://gitlab.com/api/v4"  // default
        }

        discord {
            webhookUrl = providers.environmentVariable("DISCORD_WEBHOOK_URL")
            username = "Prism"
            embedTitle = "New release"
            embedColor = 0x5865F2
        }

        // Global publishing dependencies (apply to ALL versions and loaders)
        dependencies {
            requires("fabric-api")
            optional("jei")
        }
    }
}
```

## Per-version and per-loader changelog

Override the global changelog at the version or loader level. Resolution order is **loader → version → global**; the first non-null value wins. If none is set, an empty string is used.

### Per version

```kotlin
prism {
    publishing {
        changelog = "General changelog for all versions."
    }
}

version("1.21.1") {
    changelog = "1.21.1: Added new features."
    // Or from a file:
    // changelogFile = "changelogs/1.21.1.md"
}

version("1.20.1") {
    changelog = "1.20.1: Backport fixes."
}
```

### Per loader

```kotlin
version("1.21.1") {
    changelog = "1.21.1 changelog."

    neoforge {
        changelog = "1.21.1 NeoForge-specific notes."
        // changelogFile = "changelogs/1.21.1-neoforge.md"
    }

    fabric {
        changelog = "1.21.1 Fabric-specific notes."
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

For each loader subproject, Prism registers its own upload tasks and sets:

- `file` to the output JAR of that subproject (selected per loader — see below)
- `loader` to the slug expected by CurseForge/Modrinth (`fabric`, `neoforge`, or `forge` — both MDG-Legacy `forge { }` and LexForge `lexForge { }` publish as `forge`)
- `minecraftVersions` to the Minecraft version of that subproject (plus any `gameVersion("...")` extras declared in `curseforge { }`)
- For CurseForge: auto-populates Java version + `Client` + `Server` gameVersion IDs from the loader's resolved Java toolchain
- `changelog`, `version`, and `type` — resolved loader → version → global (first non-null wins)
- Platform credentials from the root configuration or environment variables
- Publishing dependencies from global + version + loader levels (deduped, most-specific wins)

### Artifact selection

Prism picks the publishable artifact task per loader:

| Loader | Task |
|--------|------|
| Fabric (Loom) | `remapJar` |
| NeoForge (ModDevGradle) | `jar` |
| Forge 1.17–1.20.1 (MDG Legacy) | `reobfJar` |
| LexForge 1.21.1+ (ForgeGradle 7) | `jar` |
| Legacy Forge 1.7.10–1.12.2 (RFG) | `reobfJar` |

Override with `artifactTask("myTask")` or `artifactFile("build/libs/custom.jar")` under `publishing { }`. This only affects platform publishing; Maven publishing still uses the Gradle Java component unless you override it through raw Gradle hooks.

Platform publish tasks build the selected artifact before upload. Each leaf loader's `prismPublish<Platform>` task:

- depends on `clean`
- depends on the selected artifact task (or your `artifactTask(...)` override)
- uploads the freshly built file
- (optional) triggers `prismAnnounceDiscord` as a finalizer once the upload succeeds

### Duplicate dependency handling

Dependencies declared at multiple tiers (global + version + loader) are merged with the **most-specific tier winning** per `(platform, slug)` pair. Declaring the same slug at two tiers is safe — Prism dedupes silently — but the per-loader entry overrides the global one if the type differs. For example:

```kotlin
publishing {
    dependencies { requires("modmenu") }       // global: REQUIRED
}
fabric {
    publishingDependencies { optional("modmenu") }   // fabric: OPTIONAL → wins
}
```

The final CurseForge/Modrinth upload for the fabric loader sends `modmenu` as `OPTIONAL` exactly once.

## Git platforms (GitHub / Gitea / GitLab)

These platforms publish the jar as an asset on a named release. Prism creates the release if it doesn't exist, uploads the artifact, and prints the release URL.

### GitHub Releases

```kotlin
publishing {
    github {
        // Token: auto-picks GITHUB_TOKEN (Actions), falls back to GH_TOKEN,
        // or override explicitly:
        // accessToken = providers.environmentVariable("MY_GH_PAT")

        repository = "MyName/my-mod"        // required
        tagName = "v1.0.0"                  // default: mod version
        commitish = "main"                  // default: main
        draft = false
        prerelease = false
        reuseExistingRelease = true         // if a release for this tag exists, upload there
    }
}
```

### Gitea

```kotlin
publishing {
    gitea {
        accessToken = providers.environmentVariable("GITEA_TOKEN")
        apiEndpoint = "https://gitea.example.com/api/v1"   // your instance
        repository = "MyName/my-mod"
        tagName = "v1.0.0"
        draft = false
        prerelease = false
    }
}
```

### GitLab

```kotlin
publishing {
    gitlab {
        accessToken = providers.environmentVariable("GITLAB_TOKEN")
        apiEndpoint = "https://gitlab.com/api/v4"   // default; override for self-hosted
        projectId = 12345L                          // numeric project ID
        tagName = "v1.0.0"
        commitish = "main"
    }
}
```

## Discord announcements

Post a webhook message to Discord after the CF/Modrinth/GitHub/Gitea/GitLab uploads succeed. The announcement runs as a finalizer on the platform tasks and includes a link to each published artifact.

```kotlin
publishing {
    discord {
        webhookUrl = providers.environmentVariable("DISCORD_WEBHOOK_URL")
        username = "Prism"                          // optional
        avatarUrl = "https://example.com/avatar.png"  // optional
        content = "New release of My Mod"            // optional plain content
        embedTitle = "My Mod 1.0.0 released"
        embedDescription = "Highlights: …"
        embedColor = 0x5865F2
        includeProjectLinks = true                   // default: true; adds an embed field per platform
    }
}
```

Each platform upload task calls `finalizedBy(prismAnnounceDiscord)`. A single Discord message is posted after the finalized upload(s) finish — not one per platform.

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

### Publishing common JARs (for library mods)

If you're building a library mod, other multiloader mods need to depend on your common API. Enable common JAR publishing:

```kotlin
publishing {
    publishCommonJar = true
    mavenLocal()
}
```

This publishes each version's common project as a separate artifact alongside the loader JARs:

```
com.example:mymod-1.21.1-common:1.0.0    # common API
com.example:mymod-1.21.1-fabric:1.0.0    # fabric implementation
com.example:mymod-1.21.1-neoforge:1.0.0  # neoforge implementation
```

Consumers of your library can then:
- Depend on `mymod-1.21.1-common` in their common
- Depend on `mymod-1.21.1-fabric` in their fabric
- Depend on `mymod-1.21.1-neoforge` in their neoforge

Only works for multi-loader versions (versions with `common()`). Single-loader versions don't have a separate common.

### Maven artifact coordinates

Each loader subproject publishes with:
- **groupId**: your project group
- **artifactId**: `{modId}-{mcVersion}-{loader}` (e.g. `mymod-1.21.1-neoforge`)
- **version**: your project version

With `publishCommonJar = true`, common subprojects also publish as `{modId}-{mcVersion}-common`.

## Running

Prism registers `prism*` tasks at every level of the project tree. All tasks are owned by Prism — there is no longer any collision with a third-party publish plugin.

### Task hierarchy

| Level          | Task path                                          | Scope                                   |
|----------------|----------------------------------------------------|-----------------------------------------|
| Root           | `prismPublishCurseforge`                           | Every mod × version × loader            |
| Module         | `:<mod>:prismPublishCurseforge`                    | One mod, every version × loader         |
| Version        | `:<mod>:<mc>:prismPublishCurseforge`               | One version of one mod, every loader    |
| Leaf (loader)  | `:<mod>:<mc>:<loader>:prismPublishCurseforge`      | Just that one loader                    |

Same pattern for:

| Task                        | Platform            |
|-----------------------------|---------------------|
| `prismPublishCurseforge`    | CurseForge          |
| `prismPublishModrinth`      | Modrinth            |
| `prismPublishGithub`        | GitHub Releases     |
| `prismPublishGitea`         | Gitea Releases      |
| `prismPublishGitlab`        | GitLab Releases     |
| `prismPublishAll`           | All of the above    |
| `prismPublishMaven`         | Configured Maven repos (separate aggregate) |

Discord announcements run as a **finalizer** (`prismAnnounceDiscord`) attached to the per-loader platform tasks. After the uploads succeed, Prism posts a single message to the configured webhook with links to each uploaded artifact.

### Examples

```bash
# Everything, everywhere
./gradlew prismPublishAll

# Just the "boids" mod, all versions, all configured platforms
./gradlew :boids:prismPublishAll

# Boids 1.21.1 to CurseForge only
./gradlew :boids:1.21.1:prismPublishCurseforge

# One loader jar to Modrinth
./gradlew :boids:1.20.1:fabric:prismPublishModrinth

# Maven to your own repo
./gradlew :boids:prismPublishMaven
```

### Dry run

Every upload task supports a dry-run mode that logs the outgoing payload without making a network call. Useful for validating CI configs and secrets plumbing before shipping anything real.

```bash
./gradlew :boids:1.20.1:fabric:prismPublishCurseforge -Pprism.publishDryRun=true
```

The task logs the projectId, Minecraft versions, loader, declared deps, and file path that *would* be uploaded. Same flag works for Modrinth, GitHub, Gitea, GitLab, and Discord.

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
