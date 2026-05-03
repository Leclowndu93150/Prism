---
sidebar_position: 2
---

# Template Variables

Prism expands `${variable}` placeholders in metadata files during the build.

## Supported files

- `fabric.mod.json`
- `META-INF/mods.toml`
- `META-INF/neoforge.mods.toml`
- `pack.mcmeta`
- `*.mixins.json`

JSON files (`fabric.mod.json`, `pack.mcmeta`, `*.mixins.json`) have newlines escaped as `\\n`.
TOML files (`mods.toml`, `neoforge.mods.toml`) are expanded as-is.

## Available variables

### Common (all subprojects)

| Variable                         | Source                    | Example            |
|----------------------------------|---------------------------|---------------------|
| `version`                        | metadata.version          | `1.0.0`            |
| `group`                          | metadata.group            | `com.example`      |
| `minecraft_version`              | version block             | `1.21.1`           |
| `mod_name`                       | metadata.name             | `My Mod`           |
| `mod_author`                     | metadata.authors (joined) | `Alice, Bob`       |
| `mod_id`                         | metadata.modId            | `mymod`            |
| `license`                        | metadata.license          | `MIT`              |
| `description`                    | metadata.description      | `A mod.`           |
| `java_version`                   | detected or override      | `21`               |
| `credits`                        | metadata.credits (joined) | `Charlie`          |

### Fabric subprojects only

| Variable                         | Source                    | Example            |
|----------------------------------|---------------------------|---------------------|
| `fabric_loader_version`          | fabric.loaderVersion      | `0.16.2`           |
| `fabric_version`                 | fabric.apiVersion         | `0.102.1`          |

### NeoForge subprojects only

| Variable                           | Source                        | Example   |
|------------------------------------|-------------------------------|-----------|
| `neoforge_version`                 | neoforge.loaderVersion        | `21.1.26` |
| `neoforge_loader_version_range`    | neoforge.loaderVersionRange   | `[4,)`    |

### Forge subprojects only (MDG Legacy, 1.17–1.20.1)

| Variable                         | Source                    | Example   |
|----------------------------------|---------------------------|-----------|
| `forge_version`                  | forge.loaderVersion       | `47.2.0`  |
| `forge_loader_version_range`     | forge.loaderVersionRange  | `[47,)`   |

### LexForge subprojects only (ForgeGradle 7, 1.21.1+)

| Variable                           | Source                        | Example    |
|------------------------------------|-------------------------------|------------|
| `lexforge_version`                 | lexForge.loaderVersion        | `52.0.0`   |
| `lexforge_loader_version_range`    | lexForge.loaderVersionRange   | `[52,)`    |

Loader-specific variables are only available in subprojects for that loader — `fabric_loader_version` is only set in Fabric subprojects, and so on. Using a Fabric variable in a NeoForge TOML file will leave the placeholder unexpanded.

## Example

**fabric.mod.json**
```json
{
  "schemaVersion": 1,
  "id": "${mod_id}",
  "version": "${version}",
  "name": "${mod_name}",
  "description": "${description}",
  "authors": ["${mod_author}"],
  "license": "${license}",
  "depends": {
    "fabricloader": ">=${fabric_loader_version}",
    "minecraft": "${minecraft_version}"
  }
}
```

**META-INF/neoforge.mods.toml**
```toml
modLoader = "javafml"
loaderVersion = "${neoforge_loader_version_range}"

[[mods]]
modId = "${mod_id}"
version = "${version}"
displayName = "${mod_name}"
description = "${description}"
authors = "${mod_author}"
license = "${license}"
```

**META-INF/mods.toml** (LexForge, 1.21.1+)
```toml
modLoader = "javafml"
loaderVersion = "${lexforge_loader_version_range}"

[[mods]]
modId = "${mod_id}"
version = "${version}"
displayName = "${mod_name}"
```
