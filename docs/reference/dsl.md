---
sidebar_position: 1
---

# DSL Reference

## Settings plugin

**Plugin ID:** `dev.prism.settings`

Applied in `settings.gradle.kts`. Registers subprojects and configures repository access.

```kotlin
prism {
    sharedCommon()                   // optional: common/ in project root
    sharedCommon("versions/common")  // optional: common/ inside versions/

    version(minecraftVersion: String) {
        // Multi-loader: common() + multiple loaders
        common()       // required for multi-loader
        fabric()
        forge()        // Forge 1.17–1.20.1 via MDG Legacy
        neoforge()     // NeoForge 1.20.2+ via ModDevGradle
        lexForge()     // Forge 1.21.1+ via ForgeGradle 7
        legacyForge()  // Forge 1.7.10–1.12.2 via RetroFuturaGradle

        // Single-loader: just one loader, no common()
        // neoforge()  // single-loader mode, no common/loader split
    }

    // Multi-mod workspace (opt-in)
    mod(moduleName: String) {
        version(minecraftVersion: String) {
            common()
            fabric()
            neoforge()
        }
    }
}
```

## Project plugin

**Plugin ID:** `dev.prism`

Applied in the root `build.gradle.kts`. Configures all subprojects.

### prism

```kotlin
prism {
    metadata { ... }
    version(minecraftVersion: String) { ... }
    publishing { ... }

    kotlin()                               // enable Kotlin for ALL versions (default 2.1.20)
    kotlin(version: String)                // enable Kotlin for ALL versions with specific version

    sharedCommon {                         // configure the shared common project
        mixin()                            // add Mixin as compileOnly
        mixinExtras()                      // add Mixin + MixinExtras as compileOnly
        dependencies { ... }               // additional dependencies
        rawProject { project -> ... }      // escape hatch for :common
    }

    mod(moduleName: String) { ... }     // multi-mod workspace (see below)

    curseMaven()                   // add CurseMaven repository
    modrinthMaven()                // add Modrinth Maven repository
    maven(name: String, url: String)  // add custom Maven repository
}
```

The top-level `kotlin()` propagates to every version that doesn't already have its own `kotlin()` call. Per-version `kotlin()` takes precedence.

### metadata

```kotlin
metadata {
    modId: String           // required
    name: String
    description: String
    license: String
    version: String         // defaults to project.version
    group: String           // defaults to project.group
    author(name: String)    // repeatable
    credit(name: String)    // repeatable
}
```

### version

```kotlin
version("1.21.1") {
    javaVersion: Int?                      // auto-detected from MC version
    neoFormVersion: String?                // auto-resolved from Maven
    parchmentMinecraftVersion: String?     // requires parchmentMappingsVersion
    parchmentMappingsVersion: String?
    minecraftVersionRange: String?         // version range string for template expansion

    changelog: String?                     // per-version changelog (overrides global)
    changelogFile: String?                 // per-version changelog file path

    kotlin()                               // enable Kotlin (default 2.1.20)
    kotlin(version: String)                // enable Kotlin with specific version

    accessWidener(path: String)            // unified AW file, auto-converted to AT for Forge/NeoForge

    minecraftVersions("1.21", "1.21.1")    // explicit Minecraft versions for publishing
    rawCommonProject { project -> ... }    // escape hatch for versions/{mc}/common

    common {                               // shared dependencies
        api(dep: String)
        implementation(dep: String)
        compileOnlyApi(dep: String)
        compileOnly(dep: String)
        runtimeOnly(dep: String)
        annotationProcessor(dep: String)
        configuration(name: String, dep: String)
        modConfiguration(name: String, dep: String)
    }

    publishingDependencies { ... }         // per-version publishing dependencies

    fabric { ... }
    forge { ... }
    neoforge { ... }
    lexForge { ... }
    legacyForge { ... }
}
```

### fabric

```kotlin
fabric {
    loaderVersion: String      // required
    apiVersion: String?        // set via fabricApi()

    changelog: String?         // per-loader changelog (overrides version and global)
    changelogFile: String?     // per-loader changelog file path

    fabricApi(version: String) // shorthand for apiVersion
    yarn(version: String)      // use Yarn mappings instead of Mojang
    datagen()                  // enable Fabric API datagen run

    dependencies {
        api(dep: String)
        implementation(dep: String)
        modApi(dep: String)
        modImplementation(dep: String)   // remapped by Loom
        compileOnlyApi(dep: String)
        compileOnly(dep: String)
        modCompileOnlyApi(dep: String)
        modCompileOnly(dep: String)      // remapped by Loom
        runtimeOnly(dep: String)
        modRuntimeOnly(dep: String)      // remapped by Loom
        jarJar(dep: String)              // maps to Loom include
        shadow(dep: String)              // maps to implementation + include
        annotationProcessor(dep: String)
        localJar(path: String)                         // local JAR, defaults to compileOnly
        localJar(path: String, configuration: String)  // local JAR with custom configuration
        configuration(name: String, dep: String)
        modConfiguration(name: String, dep: String)
    }

    configuration(name: String)            // create a custom Gradle configuration

    mixins {
        autoDetect(enabled: Boolean)
        disableAutoDetect()
        config(path: String)
        configs(vararg paths: String)
        refmap(name: String)
    }

    rawLoom { loom -> ... }
    rawProject { project -> ... }

    runs {
        client("myClient") {            // custom client run
            username = "Dev"             // optional
            ideConfigGenerated = true    // default: generate IntelliJ run config
        }
        server("myServer") { }          // custom server run
        run("custom") {                 // fully custom run
            client()                     // type: CLIENT, SERVER, DATA, CLIENT_DATA, SERVER_DATA
            username = "TestPlayer"
            jvmArg("-Xmx4G")
            programArg("--demo")
            systemProperty("key", "value")
            runDir = "runs/custom"       // optional, auto-generated if not set
        }
    }

    publishingDependencies { ... }
}
```

### shadow block (inside dependencies)

The `shadow(dep) { }` and `shadow { }` overloads accept a configuration block:

```kotlin
dependencies {
    shadow("com.example:lib:1.0") {
        relocation(enabled: Boolean)                    // enable/disable auto-relocation
        relocationPrefix(prefix: String)                // set relocation destination prefix
        includeRelocation(pattern: String)              // restrict which packages are relocated
        excludeRelocation(pattern: String)              // exclude packages from relocation
        relocate(pattern: String, destination: String)  // explicit relocation rule
        relocate(pattern, destination) {
            include(pattern: String)
            exclude(pattern: String)
            skipStringConstants(skip: Boolean)
        }
        exclude(vararg patterns: String)                // exclude files from the JAR
        strip(vararg patterns: String)                  // strip file entries by Ant pattern
        removeManifestAttribute(name: String)           // remove a manifest attribute
        mergeServiceFiles()                             // merge all META-INF/services/
        mergeServiceFiles(rootPath: String)             // merge a specific service file
        raw { shadowJar -> ... }                        // direct ShadowJar task access
    }

    // Top-level shorthands on the dependencies block:
    shadowRelocation(enabled: Boolean)
    shadowRelocationPrefix(prefix: String)
}
```

By default, Prism auto-relocates all packages to a prefix derived from the mod group. `Class-Path` and `Multi-Release` manifest attributes are removed automatically.

### forge

```kotlin
forge {
    loaderVersion: String          // required
    loaderVersionRange: String?    // for template expansion

    changelog: String?             // per-loader changelog (overrides version and global)
    changelogFile: String?         // per-loader changelog file path

    dependencies {
        api(dep: String)
        implementation(dep: String)
        modApi(dep: String)              // remapped by MDG Legacy
        modImplementation(dep: String)   // remapped by MDG Legacy
        compileOnlyApi(dep: String)
        compileOnly(dep: String)
        modCompileOnlyApi(dep: String)   // remapped by MDG Legacy
        modCompileOnly(dep: String)      // remapped by MDG Legacy
        runtimeOnly(dep: String)
        modRuntimeOnly(dep: String)      // remapped by MDG Legacy
        jarJar(dep: String)              // maps to MDG Legacy jarJar
        shadow(dep: String)              // maps to Shadow + additionalRuntimeClasspath
        annotationProcessor(dep: String)
        localJar(path: String)                         // local JAR, defaults to compileOnly
        localJar(path: String, configuration: String)  // local JAR with custom configuration
        configuration(name: String, dep: String)
        modConfiguration(name: String, dep: String)
    }

    configuration(name: String)
    remapConfiguration(name: String)      // create custom + mod{Name} via MDG Legacy

    mixins {
        autoDetect(enabled: Boolean)
        disableAutoDetect()
        config(path: String)
        configs(vararg paths: String)
        refmap(name: String)
    }

    rawLegacyForge { ext -> ... }
    rawProject { project -> ... }

    runs {
        client("second") { username = "Player2" }
        server("testServer") { }
    }

    publishingDependencies { ... }
}
```

### neoforge

```kotlin
neoforge {
    loaderVersion: String          // required
    loaderVersionRange: String?    // for template expansion

    changelog: String?             // per-loader changelog (overrides version and global)
    changelogFile: String?         // per-loader changelog file path

    dependencies {
        api(dep: String)
        implementation(dep: String)
        modApi(dep: String)
        compileOnlyApi(dep: String)
        compileOnly(dep: String)
        modCompileOnlyApi(dep: String)
        modCompileOnly(dep: String)
        runtimeOnly(dep: String)
        modRuntimeOnly(dep: String)
        jarJar(dep: String)              // maps to MDG jarJar
        shadow(dep: String)              // maps to Shadow
        localJar(path: String)                         // local JAR, defaults to compileOnly
        localJar(path: String, configuration: String)  // local JAR with custom configuration
        configuration(name: String, dep: String)
        modConfiguration(name: String, dep: String)
    }

    configuration(name: String)

    mixins {
        autoDetect(enabled: Boolean)
        disableAutoDetect()
        config(path: String)
        configs(vararg paths: String)
        refmap(name: String)
    }

    rawNeoForge { ext -> ... }
    rawProject { project -> ... }

    runs {
        client("second") { username = "Player2" }
        server("testServer") { }
    }

    publishingDependencies { ... }
}
```

### lexForge

```kotlin
lexForge {
    loaderVersion: String          // required; Forge version (without MC prefix)
    loaderVersionRange: String?    // version range for template expansion
    mappingsChannel: String?       // explicit mappings channel (official, parchment, or custom)
    mappingsVersion: String?       // explicit mappings version

    changelog: String?             // per-loader changelog (overrides version and global)
    changelogFile: String?         // per-loader changelog file path

    mappings(channel: String, version: String)  // set mappings channel + version together

    dependencies {
        api(dep: String)
        implementation(dep: String)
        modApi(dep: String)
        modImplementation(dep: String)
        compileOnlyApi(dep: String)
        compileOnly(dep: String)
        modCompileOnlyApi(dep: String)
        modCompileOnly(dep: String)
        runtimeOnly(dep: String)
        modRuntimeOnly(dep: String)
        jarJar(dep: String)
        shadow(dep: String)
        annotationProcessor(dep: String)
        localJar(path: String)
        localJar(path: String, configuration: String)
        configuration(name: String, dep: String)
        modConfiguration(name: String, dep: String)
    }

    mixins {
        autoDetect(enabled: Boolean)
        disableAutoDetect()
        config(path: String)
        configs(vararg paths: String)
        refmap(name: String)
    }

    runs { ... }
    publishingDependencies { ... }

    rawLexForge { minecraft: MinecraftExtensionForProject -> ... }
    rawProject { project -> ... }
}
```

The `publishLoaderSlug` for LexForge is `forge` — it publishes to CurseForge/Modrinth under the `forge` loader slug, same as Forge (1.17–1.20.1).

### legacyForge

```kotlin
legacyForge {
    mcVersion: String              // e.g. "1.12.2", "1.7.10"
    forgeVersion: String           // e.g. "14.23.5.2847"
    mappingChannel: String         // default "stable"
    mappingVersion: String         // default "39"
    username: String               // default "Developer"
    useModernJavaSyntax: Boolean   // default false (Java 8); enable Java 17+ syntax via ECJ
    mixinBooter: Boolean           // default true; adds MixinBooter + CleanroomMC maven
    mixinBooterVersion: String     // default "10.7"; pin a specific MixinBooter version

    changelog: String?             // per-loader changelog (overrides version and global)
    changelogFile: String?         // per-loader changelog file path

    accessTransformer(path: String)  // add AT file
    mixin()                          // legacy: register MixinTweaker in jar manifest
    coreMod(fqn: String)             // override auto-detected IFMLLoadingPlugin class

    mixins {
        autoDetect(enabled: Boolean)
        disableAutoDetect()
        config(path: String)
        configs(vararg paths: String)
        refmap(name: String)
    }

    dependencies { ... }
    runs { ... }
    publishingDependencies { ... }
    configuration(name: String)
    rawProject { project -> ... }
}
```

### module

For multi-mod workspaces. Each module is an independent mod with its own metadata and publishing.

```kotlin
mod("corpse-curios") {
    metadata {
        modId = "corpse_curios_compat"
        name = "Corpse x Curios Compat"
        author("MyName")
    }

    dependsOn("other-module")             // compile-time dep on another module
    kotlin()                               // enable Kotlin for this module

    version("1.21.1") {
        neoforge { loaderVersion = "21.1.26" }
        fabric {
            loaderVersion = "0.18.6"
            fabricApi("0.102.1")
        }
    }

    publishing {
        curseforge { projectId = "123456" }
        modrinth { projectId = "abcdef" }
    }
}
```

| Method | Description |
|--------|-------------|
| `metadata(action)` | Module-specific mod metadata |
| `dependsOn(vararg moduleNames)` | Adds compile-time dependency on other modules' common code |
| `kotlin(version)` | Enable Kotlin for this module |
| `version(mcVersion, action)` | Configure a Minecraft version for this module |
| `publishing(action)` | Module-specific publishing configuration |

Modules use the directory layout `modules/{moduleName}/versions/{mc}/{loader}/` and create Gradle subprojects like `:corpse-curios:1.21.1:neoforge`. Repositories (`curseMaven()`, `modrinthMaven()`, etc.) are shared from the top-level `prism` block.

### publishing

```kotlin
publishing {
    changelog: String?
    changelogFile: String?         // path relative to root project
    type: ReleaseType              // STABLE, BETA, ALPHA (constants also available as STABLE, BETA, ALPHA)
    displayName: String?           // defaults to JAR filename

    artifactTask(name: String)     // override artifact task for platform publishing
    artifactFile(path: String)     // override artifact path for platform publishing

    publishCommonJar: Boolean      // also publish common JARs (for library mods)

    curseforge {
        accessToken: Provider<String>
        projectId: String
        gameVersion(version: String)   // add extra CF game version IDs (Java, Client, Server, etc.)
    }

    modrinth {
        accessToken: Provider<String>
        projectId: String
        featured: Boolean             // default true; feature the version on Modrinth
        loader(name: String)          // add extra Modrinth loader slugs
    }

    github {
        accessToken: Provider<String>  // default: GITHUB_TOKEN, then GH_TOKEN
        repository: String             // "owner/repo"
        tagName: String?               // default: mod version
        commitish: String              // default: "main"
        draft: Boolean
        prerelease: Boolean
        generateReleaseNotes: Boolean  // ask GitHub to auto-generate release notes
        reuseExistingRelease: Boolean  // default true
    }

    gitea {
        accessToken: Provider<String>
        apiEndpoint: String            // e.g. "https://gitea.example.com/api/v1"
        repository: String
        tagName: String?
        commitish: String              // default: "main"
        draft: Boolean
        prerelease: Boolean
    }

    gitlab {
        accessToken: Provider<String>
        apiEndpoint: String            // default: "https://gitlab.com/api/v4"
        projectId: Long
        tagName: String?
        commitish: String              // default: "main"
    }

    discord {
        webhookUrl: Provider<String>
        username: String?
        avatarUrl: String?
        content: String?
        embedTitle: String?
        embedDescription: String?
        embedColor: Int?               // 0xRRGGBB
        includeProjectLinks: Boolean   // default true
    }

    dependencies {
        requires(slug: String, platform: PublishingPlatform = BOTH)
        optional(slug: String, platform: PublishingPlatform = BOTH)
        incompatible(slug: String, platform: PublishingPlatform = BOTH)
        embeds(slug: String, platform: PublishingPlatform = BOTH)
        curseforge {               // CurseForge-only deps
            requires(slug: String)
            optional(slug: String)
            incompatible(slug: String)
            embeds(slug: String)
        }
        modrinth {                 // Modrinth-only deps
            requires(slug: String)
            optional(slug: String)
            incompatible(slug: String)
            embeds(slug: String)
        }
    }
    // PublishingPlatform values: BOTH, CURSEFORGE, MODRINTH

    mavenLocal()
    githubPackages(owner: String, repo: String)   // auto-uses GITHUB_ACTOR + GITHUB_TOKEN
    maven {
        name: String
        url: String
        credentials(user: String, pass: String)
        credentialsFromEnv(userEnv: String, passEnv: String)
    }
}
```

Publishing uses `minecraftVersions()` from the version block if set, otherwise the exact Minecraft version string. See [Publishing](../publishing.md) for a prose guide.

## Doctor task

```bash
./gradlew prismDoctor
```

Prints a full configuration and wiring report. Example output:

```
Prism Doctor
root: :
sharedCommon: true

version: 1.21.1
java: 21
loaders: neoforge, fabric
commonRawHooks: 0
  loader: neoforge
  project: :1.21.1:neoforge
  underlying: net.neoforged.moddev
  mappings: neoform named dev
  mixins: autoDetect=true, explicit=[], refmap=default
  publishTask: jar
  modConfigs: modApi, modCompileOnly, modImplementation, modRuntimeOnly
  loader: fabric
  project: :1.21.1:fabric
  underlying: fabric-loom
  mappings: named dev / intermediary production
  mixins: autoDetect=true, explicit=[], refmap=default
  publishTask: remapJar
  modConfigs: modApi, modCompileOnly, modImplementation, modRuntimeOnly
```

Fields:

| Field | Description |
|-------|-------------|
| `sharedCommon` | Whether a `:common` shared project exists |
| `version` | Minecraft version |
| `java` | Resolved Java toolchain version |
| `loaders` | Configured loader names |
| `commonRawHooks` | Number of `rawCommonProject {}` lambdas registered |
| `loader` | Loader name |
| `project` | Gradle project path |
| `underlying` | The Gradle plugin applied to this project |
| `mappings` | Resolved mapping mode (`neoform named dev`, `named dev / intermediary production`, `fg7 official`, `fg7 parchment`, `mcp`, `unobfuscated`) |
| `mixins` | Auto-detect status, explicit configs, refmap name |
| `publishTask` | The Gradle task selected for publishing artifact |
| `modConfigs` | All `mod*`-prefixed configurations in the project |
