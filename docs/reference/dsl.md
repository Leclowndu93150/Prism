---
sidebar_position: 1
---

# DSL Reference

## Settings plugin

**Plugin ID:** `dev.prism.settings`

Applied in `settings.gradle.kts`. Registers subprojects and configures repository access.

```kotlin
prism {
    version(minecraftVersion: String) {
        common()     // always required
        fabric()     // optional
        forge()      // optional
        neoforge()   // optional
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

    curseMaven()                   // add CurseMaven repository
    modrinthMaven()                // add Modrinth Maven repository
    maven(name: String, url: String)  // add custom Maven repository
}
```

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

    minecraftVersions("1.21", "1.21.1")    // version range for publishing

    common {                               // shared dependencies
        implementation(dep: String)
        compileOnly(dep: String)
        runtimeOnly(dep: String)
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
    datagen()                  // enable Fabric API datagen run

    dependencies {
        implementation(dep: String)
        modImplementation(dep: String)   // remapped by Loom
        compileOnly(dep: String)
        modCompileOnly(dep: String)      // remapped by Loom
        runtimeOnly(dep: String)
        modRuntimeOnly(dep: String)      // remapped by Loom
        jarJar(dep: String)              // maps to Loom include
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
        compileOnly(dep: String)
        runtimeOnly(dep: String)
        jarJar(dep: String)              // maps to MDG Legacy jarJar
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
    }

    runs {
        client("second") { username = "Player2" }
        server("testServer") { }
    }
}
```

### publishing

```kotlin
publishing {
    changelog: String?
    changelogFile: String?         // path relative to root project
    type: ReleaseType              // STABLE, BETA, ALPHA

    curseforge {
        accessToken: Provider<String>
        projectId: String
    }

    modrinth {
        accessToken: Provider<String>
        projectId: String
    }
}
```

Publishing uses `minecraftVersions()` from the version block if set, otherwise the exact Minecraft version.
