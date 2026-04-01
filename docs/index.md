# Prism

A Gradle plugin for multi-version, multi-loader Minecraft mod development in a single branch.

Prism wraps [Fabric Loom](https://github.com/FabricMC/fabric-loom), [ModDevGradle](https://github.com/neoforged/ModDevGradle), and MDG Legacy. You get full IDE support, run configurations, and all the features of the underlying plugins without managing multiple branches or complex build scripts.

## What Prism does

- Generates Gradle subprojects for each version and loader combination
- Applies the correct toolchain plugin to each subproject
- Wires common code into loader compilations
- Configures run configurations with version-specific names
- Handles template expansion in metadata files
- Optionally wraps mod-publish-plugin for CurseForge and Modrinth

## Project layout

```
your-mod/
  build.gradle.kts          # Prism DSL configuration
  settings.gradle.kts       # Declares versions and loaders
  versions/
    1.20.1/
      common/src/main/java/
      fabric/src/main/java/
      fabric/src/main/resources/fabric.mod.json
      forge/src/main/java/
      forge/src/main/resources/META-INF/mods.toml
    1.21.1/
      common/src/main/java/
      fabric/src/main/java/
      neoforge/src/main/java/
```

Each version is completely independent. There is no cross-version shared code.

## Supported loaders

| Loader   | Plugin used                          | Minecraft versions |
|----------|--------------------------------------|--------------------|
| Fabric   | Fabric Loom                          | Any                |
| NeoForge | ModDevGradle                         | 1.20.1+            |
| Forge    | ModDevGradle Legacy                  | 1.17 - 1.20.1      |

## Next steps

Head to [Getting Started](getting-started.md) to set up your first project.
