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
- `modLoaders` to the loader slug expected by CurseForge/Modrinth (`fabric`, `neoforge`, or `forge` — both MDG-Legacy `forge { }` and LexForge `lexForge { }` publish as `forge`)
- `minecraftVersions` to the Minecraft version of that subproject
- `changelog`, `version`, and `type` from the root configuration
- CurseForge and Modrinth credentials from the root configuration
- Publishing dependencies from global + version + loader levels (stacked)

### Artifact selection

Prism picks the publishable artifact task per loader:

| Loader | Task |
|--------|------|
| Fabric (Loom) | `remapJar` |
| NeoForge (ModDevGradle) | `jar` |
| Forge 1.17–1.20.1 (MDG Legacy) | `jar` |
| LexForge 1.21.1+ (ForgeGradle 7) | `jar` |
| Legacy Forge 1.7.10–1.12.2 (RFG) | `reobfJar` |

Override with `artifactTask("myTask")` or `artifactFile("build/libs/custom.jar")` under `publishing { }`. This only affects platform publishing; Maven publishing still uses the Gradle Java component unless you override it through raw Gradle hooks.

Platform publish tasks also build the selected artifact before upload. By default Prism wires each leaf loader's `publishMods`, `publishCurseforge`, and `publishModrinth` to:

- run `clean`
- run the selected artifact task (or your `artifactTask(...)` override)
- upload the freshly built file

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

Prism registers a set of `prism*` aggregate tasks so you can publish at any level of the project tree. The aggregates are named with a `prism` prefix to avoid colliding with mod-publish-plugin's own per-loader `publishCurseforge`/`publishModrinth`/`publishMods` tasks (which are hidden from the task list but still accessible by absolute path).

### Task hierarchy

| Level          | Task path                                       | Scope                                          |
|----------------|-------------------------------------------------|------------------------------------------------|
| Root           | `prismPublishCurseforge`                        | Every mod × version × loader in the build      |
| Module         | `:<mod>:prismPublishCurseforge`                 | One mod, every version/loader                  |
| Version        | `:<mod>:<mc>:prismPublishCurseforge`            | One version of one mod, every loader           |
| Leaf (loader)  | `:<mod>:<mc>:<loader>:publishCurseforge`        | Just that one loader (native mod-publish task) |

Same pattern for `prismPublishModrinth`, `prismPublishMods`, `prismPublishAll` (the last depends on all three platforms).

For Maven publishing use `prismPublishMaven` at any level; the leaf is `:<path>:publish`.

### Examples

```bash
# Publish everything in the build to CurseForge + Modrinth
./gradlew prismPublishAll

# Publish only the "boids" mod to CurseForge, every version and loader
./gradlew :boids:prismPublishCurseforge

# Publish only the 1.21.1 version of "boids" to Modrinth
./gradlew :boids:1.21.1:prismPublishModrinth

# Publish only the fabric jar of boids 1.20.1 to CurseForge (leaf task)
./gradlew :boids:1.20.1:fabric:publishCurseforge

# Maven
./gradlew prismPublishMaven
./gradlew :boids:1.21.1:prismPublishMaven
./gradlew :boids:1.21.1:fabric:publish
```

:::tip Prefer `prism*` aggregates
Running `./gradlew publishCurseforge` without a project path will make Gradle execute every leaf task with that name — across every mod and version in the build, just like `./gradlew build`. Use the `prism*` aggregates with an explicit project path to scope to a single mod or version.
:::

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
