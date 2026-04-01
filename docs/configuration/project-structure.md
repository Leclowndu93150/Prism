# Project Structure

## Directory layout

All mod source code lives under `versions/`. Each Minecraft version gets its own folder containing a `common` subproject and one or more loader subprojects.

```
versions/
  1.20.1/
    common/
      src/main/java/
      src/main/resources/
    fabric/
      src/main/java/
      src/main/resources/
    forge/
      src/main/java/
      src/main/resources/
```

## Common code

The `common` subproject is compiled against vanilla Minecraft using NeoForm (no loader APIs). Code here is shared across all loaders for that version.

Common source files are not compiled into a separate JAR. Instead, they are fed into each loader's compilation so they get compiled against the full loader classpath. This is the same approach used by the [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template).

## Loader code

Each loader subproject has access to:

- All common code for that version
- The full Minecraft source with loader modifications
- Loader-specific APIs (Fabric API, NeoForge events, Forge events)

## Independence between versions

Each version folder is completely independent. There is no shared code between `1.20.1` and `1.21.1`. If you need the same utility class in both, copy it.

## No subproject build files

The version subfolders do not need `build.gradle.kts` files. Prism configures everything from the root project's build file. If you place a build file in a subfolder, it will be evaluated by Gradle and may conflict with Prism's configuration.

## Gradle project paths

Prism registers subprojects using Minecraft version as the parent:

```
:1.20.1:common
:1.20.1:fabric
:1.20.1:forge
:1.21.1:common
:1.21.1:fabric
:1.21.1:neoforge
```

Use these paths with Gradle commands:

```bash
./gradlew :1.20.1:fabric:build
./gradlew :1.21.1:neoforge:runClient
```
