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
        forge()
        neoforge()
        legacyForge()  // 1.7.10-1.12.2 via RetroFuturaGradle

        // Single-loader: just one loader, no common()
        // neoforge()  // single-loader mode, no common/loader split
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
    }

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

    kotlin()                               // enable Kotlin (default 2.1.20)
    kotlin(version: String)                // enable Kotlin with specific version

    accessWidener(path: String)            // unified AW file, auto-converted to AT for Forge/NeoForge

    minecraftVersions("1.21", "1.21.1")    // version range for publishing

    common {                               // shared dependencies
        implementation(dep: String)
        compileOnly(dep: String)
        runtimeOnly(dep: String)
        annotationProcessor(dep: String)
    }

    fabric { ... }
    forge { ... }
    neoforge { ... }
}
```

### fabric

```kotlin
fabric {
    loaderVersion: String      // required
    apiVersion: String?        // set via fabricApi()

    fabricApi(version: String) // shorthand for apiVersion
    yarn(version: String)      // use Yarn mappings instead of Mojang
    datagen()                  // enable Fabric API datagen run

    dependencies {
        implementation(dep: String)
        modImplementation(dep: String)   // remapped by Loom
        compileOnly(dep: String)
        modCompileOnly(dep: String)      // remapped by Loom
        runtimeOnly(dep: String)
        modRuntimeOnly(dep: String)      // remapped by Loom
        jarJar(dep: String)              // maps to Loom include
        annotationProcessor(dep: String)
        localJar(path: String)                         // local JAR, defaults to compileOnly
        localJar(path: String, configuration: String)  // local JAR with custom configuration
    }

    runs {
        client("myClient") {            // custom client run
            username = "Dev"             // optional
        }
        server("myServer") { }          // custom server run
        run("custom") {                 // fully custom run
            client()                     // or server()
            username = "TestPlayer"
            jvmArg("-Xmx4G")
            programArg("--demo")
            systemProperty("key", "value")
            runDir = "runs/custom"       // optional, auto-generated if not set
        }
    }
}
```

### forge

```kotlin
forge {
    loaderVersion: String          // required
    loaderVersionRange: String?    // for template expansion

    dependencies {
        implementation(dep: String)
        modImplementation(dep: String)   // remapped by MDG Legacy
        compileOnly(dep: String)
        modCompileOnly(dep: String)      // remapped by MDG Legacy
        runtimeOnly(dep: String)
        modRuntimeOnly(dep: String)      // remapped by MDG Legacy
        jarJar(dep: String)              // maps to MDG Legacy jarJar
        annotationProcessor(dep: String)
        localJar(path: String)                         // local JAR, defaults to compileOnly
        localJar(path: String, configuration: String)  // local JAR with custom configuration
    }

    runs {
        client("second") { username = "Player2" }
        server("testServer") { }
    }
}
```

### neoforge

```kotlin
neoforge {
    loaderVersion: String          // required
    loaderVersionRange: String?    // for template expansion

    dependencies {
        implementation(dep: String)
        compileOnly(dep: String)
        runtimeOnly(dep: String)
        jarJar(dep: String)              // maps to MDG jarJar
        localJar(path: String)                         // local JAR, defaults to compileOnly
        localJar(path: String, configuration: String)  // local JAR with custom configuration
    }

    runs {
        client("second") { username = "Player2" }
        server("testServer") { }
    }
}
```

### legacyForge

```kotlin
legacyForge {
    mcVersion: String              // e.g. "1.12.2", "1.7.10"
    forgeVersion: String           // e.g. "14.23.5.2847"
    mappingChannel: String         // default "stable"
    mappingVersion: String         // default "39"
    username: String               // default "Developer"
    useModernJavaSyntax: Boolean   // default false (Java 8)

    accessTransformer(path: String)  // add AT file
    mixin()                          // enable MixinTweaker

    dependencies { ... }
    runs { ... }
    publishingDependencies { ... }
}
```

### publishing

```kotlin
publishing {
    changelog: String?
    changelogFile: String?         // path relative to root project
    type: ReleaseType              // STABLE, BETA, ALPHA
    displayName: String?           // defaults to JAR filename

    curseforge {
        accessToken: Provider<String>
        projectId: String
    }

    modrinth {
        accessToken: Provider<String>
        projectId: String
    }

    dependencies {                 // publishing deps (global level)
        requires(slug: String)
        optional(slug: String)
        incompatible(slug: String)
        embeds(slug: String)
    }

    // Maven publishing
    publishCommonJar: Boolean              // also publish common JARs (for library mods)
    mavenLocal()                           // publish to ~/.m2
    githubPackages(owner, repo)            // GitHub Packages (auto-credentials)
    maven {                                // custom Maven repo
        name: String
        url: String
        credentials(user, pass)            // inline credentials
        credentialsFromEnv(userEnv, passEnv) // from environment variables
    }
}
```

Publishing uses `minecraftVersions()` from the version block if set, otherwise the exact Minecraft version.
