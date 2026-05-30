---
sidebar_position: 3
---

# Metadata

## Configuration

The `metadata` block sets values used for template expansion and JAR naming.

```kotlin
prism {
    metadata {
        modId = "mymod"
        name = "My Mod"
        description = "Does things."
        license = "MIT"
        author("Alice")
        author("Bob")
        credit("Charlie")
    }
}
```

| Property      | Required | Description                                |
|---------------|----------|--------------------------------------------|
| `modId`       | Yes      | Mod identifier, used in file names and templates |
| `name`        | Yes      | Display name                               |
| `description` | No       | Mod description                            |
| `license`     | No       | License identifier (MIT, GPL-3.0, etc.)    |
| `version`     | No       | Mod version. Defaults to `project.version` |
| `group`       | No       | Maven group. Defaults to `project.group`   |
| `authors`     | No       | List of author names (set directly or via `author()`) |
| `credits`     | No       | List of credit names (set directly or via `credit()`) |
| `archivesName`| No       | Override the output JAR name. Supports `{modId}`, `{mc}`, `{loader}`, `{version}` |

`author(name)` and `credit(name)` are the idiomatic builder methods and can be called multiple times. You can also assign the `authors` and `credits` lists directly if you prefer.

## Custom template variables

`expand(key, value)` adds extra `${key}` placeholders for [template expansion](../reference/template-variables.md). The value can be a plain string or a `Provider<String>` (resolved lazily at build time), so computed values like the git commit hash work:

```kotlin
prism {
    metadata {
        modId = "mymod"
        expand("commit", gitCommit())
        expand("buildtime", "2026")
    }
}
```

`gitCommit()` returns a `Provider<String>` of the current short commit hash (`gitCommit(short = false)` for the full hash), falling back to `unknown` outside a git repo.

## Including files in the JAR

`includeFile(path, rename)` copies a file from the root project into every output JAR. `rename` is optional and supports `{name}` (the original file name) and `{archivesName}`:

```kotlin
prism {
    metadata {
        includeFile("LICENSE", "{name}_{archivesName}")
    }
}
```

## Writing metadata files

Prism does not generate `fabric.mod.json`, `mods.toml`, or `neoforge.mods.toml` for you. You write these files yourself and place them in the loader's `src/main/resources/` directory.

Prism expands `${variable}` placeholders in these files during the build. See [Template Variables](../reference/template-variables.md) for the full list.

## JAR naming

Output JARs follow the format:

```
{modId}-{mcVersion}-{Loader}-{version}.jar
```

Examples:

```
mymod-1.20.1-Fabric-1.0.0.jar
mymod-1.21.1-NeoForge-1.0.0.jar
mymod-1.21.1-LexForge-1.0.0.jar
```

Override the format with `metadata.archivesName`, using `{modId}`, `{mc}`, `{loader}`, and `{version}`:

```kotlin
prism {
    metadata {
        archivesName = "{modId}-{loader}-{version}"
    }
}
```

The same name is used for the Maven `artifactId` when publishing.
