---
slug: /
sidebar_position: 1
---

# Prism

A Gradle plugin for multi-version, multi-loader Minecraft mod development in a single branch.

Prism wraps [Fabric Loom](https://github.com/FabricMC/fabric-loom), [ModDevGradle](https://github.com/neoforged/ModDevGradle), and MDG Legacy. You get full IDE support, run configurations, and all the features of the underlying plugins without managing multiple branches or complex build scripts.

## What Prism does

- All versions and loaders in one branch, one build file
- Full IntelliJ run configurations per target
- Kotlin and Java support
- Mojang or Yarn mappings, Parchment
- Per-loader dependency blocks with Jar-in-Jar
- Custom run configurations with optional dev username
- CurseMaven and Modrinth Maven built in
- Version-aware datagen (split client/server for 1.21.4+)
- Handles unobfuscated Minecraft (26.x) automatically
- Auto-detects access wideners and access transformers
- Template variable expansion in metadata files
- CurseForge and Modrinth publishing with platform-specific dependencies
- Optional shared common across all versions for pure Java API code

## Project layout

```
common/                          optional shared code (no Minecraft)
versions/
  1.20.1/
    common/src/main/java/        vanilla MC classes available
    fabric/src/main/java/        Fabric API available
    forge/src/main/java/         Forge API available
  1.21.1/
    common/src/main/java/
    fabric/src/main/java/
    neoforge/src/main/java/
  26.1/
    common/src/main/java/
    fabric/src/main/java/
    neoforge/src/main/java/
```

## Supported loaders

| Loader       | Plugin used                    | Minecraft versions |
|--------------|--------------------------------|--------------------|
| Fabric       | Fabric Loom                    | Any                |
| NeoForge     | ModDevGradle                   | 1.20.2+            |
| Forge        | ModDevGradle Legacy            | 1.17 - 1.20.1      |
| Legacy Forge | RetroFuturaGradle              | 1.7.10 - 1.12.2    |

## Next steps

- Install the [IntelliJ plugin](https://github.com/Leclowndu93150/Prism-Generator) for a guided project wizard
- Use the [Mod Template](https://github.com/Leclowndu93150/prism-mod-template) for a quick start
- Read [Getting Started](getting-started.md) for manual setup
