# Dependencies

## Built-in dependencies

Prism automatically adds the following to the common subproject:

- `org.spongepowered:mixin:0.8.5` (compileOnly)
- `io.github.llamalad7:mixinextras-common:0.3.5` (compileOnly + annotation processor)

For Fabric subprojects:

- `com.mojang:minecraft:{mcVersion}`
- `net.fabricmc:fabric-loader:{loaderVersion}`
- `net.fabricmc.fabric-api:fabric-api:{apiVersion}` (if `fabricApi()` is called)

For NeoForge and Forge subprojects, the loader handles Minecraft and loader dependencies through the extension.

## Adding custom dependencies

Since subprojects have no build files, you add custom dependencies from the root `build.gradle.kts` by accessing the subproject directly:

```kotlin
// After the prism {} block
subprojects {
    afterEvaluate {
        if (path.contains("common")) {
            dependencies {
                add("implementation", "some:common-library:1.0")
            }
        }
    }
}
```

Or target a specific subproject:

```kotlin
project(":1.21.1:fabric").afterEvaluate {
    dependencies {
        add("modImplementation", "some:fabric-mod:1.0")
    }
}
```

## Common dependencies flow to loaders

Dependencies declared on the common subproject's `compileOnly` or `implementation` configurations are available to loader subprojects because the common source is compiled as part of each loader's compilation.

Note that common uses `compileOnly` for its dependency on the loader subprojects. This means the common subproject can reference common APIs, but the actual classes come from the loader's classpath at compile time.
