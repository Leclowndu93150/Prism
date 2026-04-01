# FAQ

## Can I use Kotlin?

Prism does not apply the Kotlin plugin to subprojects. You can apply it manually by accessing the subproject from the root build file:

```kotlin
project(":1.21.1:fabric").apply {
    apply(plugin = "org.jetbrains.kotlin.jvm")
}
```

Native Kotlin support is planned for a future release.

## Can I share code between versions?

No. Each version is fully independent. If you need the same class in 1.20.1 and 1.21.1, copy it into both `common` folders. This is intentional: Minecraft APIs change significantly between versions, and shared code creates more problems than it solves.

## Can I add a subproject build file?

You can, but it may conflict with Prism's configuration. Prism applies plugins and configures tasks from the root project. If your subproject build file also applies plugins or configures the same tasks, you will get errors or unexpected behavior.

## How does the common project compile?

The common subproject uses ModDevGradle with `neoFormVersion` only (vanilla Minecraft, no loader). This gives you access to all Minecraft classes without any loader modifications.

Common source files are then compiled again as part of each loader's compilation, so they have access to loader APIs at compile time.

## NeoForm version resolution fails

If you see an error about NeoForm version resolution:

1. Check your internet connection. Prism fetches version metadata from `maven.neoforged.net`.
2. If building offline, set `neoFormVersion` manually in the version block.
3. The resolved version is cached for 24 hours in `~/.gradle/caches/prism/`. Delete this file to force a refresh.

## Run configurations are missing

Make sure you reload the Gradle project in IntelliJ after changing the Prism configuration. The run configurations are generated during Gradle sync.

For Fabric, run configs are generated when `ideConfigGenerated(true)` is set, which Prism does automatically.

For NeoForge and Forge, ModDevGradle generates run configs for all declared runs.

## How do I add access wideners or access transformers?

**Access wideners** (Fabric): Place `{modId}.accesswidener` in `versions/{mc}/common/src/main/resources/`.

**Access transformers** (NeoForge/Forge): Place `accesstransformer.cfg` in `versions/{mc}/common/src/main/resources/META-INF/`.

Prism detects these files automatically and configures the loader plugins.

## Can I publish to CurseForge and Modrinth?

Yes. See [Publishing](publishing.md). Prism wraps mod-publish-plugin and configures it for each loader subproject.

## What Gradle version do I need?

Gradle 8.8 or newer. Gradle 9.x is recommended. This is a requirement from ModDevGradle.

## What Java version do I need?

JDK 21. Prism auto-detects the target Java version per Minecraft version:

| Minecraft | Java |
|-----------|------|
| 1.16.x and older | 8 |
| 1.17.x | 16 |
| 1.18.x - 1.20.x | 17 |
| 1.21.x and newer | 21 |

You can override this with `javaVersion` in the version block.
