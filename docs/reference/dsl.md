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

    fabric { ... }
    forge { ... }
    neoforge { ... }
}
```

### fabric

```kotlin
fabric {
    loaderVersion: String      // required
    apiVersion: String?        // set via fabricApi("version")

    fabricApi(version: String) // shorthand for apiVersion
}
```

### forge

```kotlin
forge {
    loaderVersion: String          // required
    loaderVersionRange: String?    // for template expansion
}
```

### neoforge

```kotlin
neoforge {
    loaderVersion: String          // required
    loaderVersionRange: String?    // for template expansion
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
