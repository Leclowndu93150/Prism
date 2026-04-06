package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.ModuleConfiguration
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.PrismExtension
import dev.prism.gradle.dsl.VersionConfiguration

object Validation {

    fun validate(extension: PrismExtension) {
        val hasModules = extension.modules.isNotEmpty()
        val hasVersions = extension.versions.isNotEmpty()

        if (!hasModules && !hasVersions) {
            throw IllegalStateException(
                "Prism: No versions or modules configured. Add at least one version or module block."
            )
        }

        if (hasVersions) {
            validateMetadata(extension.metadata)
            for ((mcVersion, versionConfig) in extension.versions) {
                validateVersion(mcVersion, versionConfig)
            }
        }

        for ((moduleName, moduleConfig) in extension.modules) {
            validateModule(moduleName, moduleConfig)
        }
    }

    fun validateModule(moduleName: String, module: ModuleConfiguration) {
        if (module.metadata.modId.isEmpty()) {
            throw IllegalStateException(
                "Prism: metadata.modId is required for module '$moduleName'.\n" +
                "  mod(\"$moduleName\") {\n" +
                "      metadata {\n" +
                "          modId = \"my_mod\"\n" +
                "      }\n" +
                "  }"
            )
        }

        validateMetadata(module.metadata)

        if (module.versions.isEmpty()) {
            throw IllegalStateException(
                "Prism: Module '$moduleName' has no versions configured."
            )
        }

        for ((mcVersion, versionConfig) in module.versions) {
            validateVersion(mcVersion, versionConfig)
        }
    }

    private fun validateMetadata(metadata: MetadataExtension) {
        if (metadata.modId.isEmpty()) {
            throw IllegalStateException(
                "Prism: metadata.modId is required.\n" +
                "  prism {\n" +
                "      metadata {\n" +
                "          modId = \"mymod\"\n" +
                "      }\n" +
                "  }"
            )
        }

        if (metadata.modId.contains(" ") || metadata.modId.contains("-") || metadata.modId != metadata.modId.lowercase()) {
            throw IllegalStateException(
                "Prism: modId '${metadata.modId}' is invalid. " +
                "Use only lowercase letters, numbers, and underscores (e.g. 'my_mod')."
            )
        }
    }

    private fun validateVersion(mcVersion: String, config: VersionConfiguration) {
        for (loader in config.loaders) {
            validateLoader(mcVersion, loader)
        }
    }

    private fun validateLoader(mcVersion: String, loader: LoaderConfiguration) {
        val loaderVersion = when (loader) {
            is FabricConfiguration -> loader.loaderVersion
            is ForgeConfiguration -> loader.loaderVersion
            is NeoForgeConfiguration -> loader.loaderVersion
            else -> return
        }

        if (loaderVersion.isEmpty()) {
            throw IllegalStateException(
                "Prism: ${loader.loaderDisplayName} loaderVersion is required for version '$mcVersion'.\n" +
                "  ${loader.loaderName} {\n" +
                "      loaderVersion = \"...\"\n" +
                "  }"
            )
        }

        if (loader is FabricConfiguration && loader.enableDatagen && loader.apiVersion == null) {
            throw IllegalStateException(
                "Prism: Fabric datagen requires Fabric API for version '$mcVersion'.\n" +
                "  fabric {\n" +
                "      fabricApi(\"...\")\n" +
                "      datagen()\n" +
                "  }"
            )
        }
    }
}
