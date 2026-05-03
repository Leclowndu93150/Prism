---
slug: /
sidebar_position: 1
---

# Prism

A Gradle plugin for multi-version, multi-loader Minecraft mod development in a single branch.

Prism wraps [Fabric Loom](https://github.com/FabricMC/fabric-loom), [ModDevGradle](https://github.com/neoforged/ModDevGradle), [ForgeGradle 7](https://github.com/MinecraftForge/ForgeGradle), and [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle). You get full IDE support, run configurations, and all the features of the underlying plugins without managing multiple branches or complex build scripts.

## What Prism does

- All versions and loaders in one branch, one build file
- Full IntelliJ run configurations per target
- Kotlin and Java support
- Mojang or Yarn mappings, Parchment
- Per-loader dependency blocks with Jar-in-Jar and Shadow
- Custom run configurations with optional dev username
- CurseMaven and Modrinth Maven built in
- Version-aware datagen (split client/server for 1.21.4+)
- Handles unobfuscated Minecraft (26.x) automatically
- Auto-detects access wideners and access transformers
- Template variable expansion in metadata files
- CurseForge, Modrinth, GitHub, Gitea, GitLab, and Maven publishing
- Optional shared common across all versions for pure Java API code
- Multi-mod workspaces with independent metadata and publishing per module
- Raw project and underlying-plugin hooks when the Prism DSL is not enough
- `prismDoctor` for auditing resolved wiring, publish artifacts, and remap configs

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
modules/                         optional: independent mods in one repo
  my-addon/
    versions/1.21.1/
      neoforge/src/main/java/
```

## Supported loaders

| Loader       | Plugin used                    | Minecraft versions | Gradle path suffix |
|--------------|--------------------------------|--------------------|--------------------|
| Fabric       | Fabric Loom                    | Any                | `:fabric`          |
| NeoForge     | ModDevGradle                   | 1.20.2+            | `:neoforge`        |
| Forge        | ModDevGradle Legacy            | 1.17 – 1.20.1      | `:forge`           |
| LexForge     | ForgeGradle 7                  | 1.21.1+            | `:lexforge`        |
| Legacy Forge | RetroFuturaGradle              | 1.7.10 – 1.12.2    | `:legacyforge`     |

LexForge is MinecraftForge proper (the original fork) for 1.21.1 and later. Forge (1.17–1.20.1) uses ModDevGradle Legacy, which is a different toolchain from the same MC version range.

## Next steps

- Install the [IntelliJ plugin](https://github.com/Leclowndu93150/Prism-Generator) for a guided project wizard
- Use the [Mod Template](https://github.com/Leclowndu93150/prism-mod-template) for a quick start
- Read [Getting Started](getting-started.md) for manual setup
