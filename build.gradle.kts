plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "dev.prism"
version = "0.6.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        name = "MinecraftForge"
        url = uri("https://maven.minecraftforge.net/")
    }
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "NeoForge"
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        name = "Sponge"
        url = uri("https://repo.spongepowered.org/repository/maven-public/")
    }
    maven {
        name = "Minecraft"
        url = uri("https://libraries.minecraft.net/")
    }
    maven {
        name = "GTNH"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(gradleApi())
    implementation("net.fabricmc:fabric-loom:1.18.2")
    implementation("net.neoforged:moddev-gradle:2.0.147")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.gtnewhorizons:retrofuturagradle:2.0.4")
    implementation("net.minecraftforge:forgegradle:7.0.40")
    implementation("net.minecraftforge.gradle:ForgeGradle:6.0.54")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}

gradlePlugin {
    plugins {
        create("prismSettings") {
            id = "dev.prism.settings"
            displayName = "Prism Settings Plugin"
            description = "Settings plugin for multi-version Minecraft mod development"
            implementationClass = "dev.prism.gradle.PrismSettingsPlugin"
        }
        create("prism") {
            id = "dev.prism"
            displayName = "Prism Project Plugin"
            description = "Multi-version multi-loader Minecraft mod development plugin"
            implementationClass = "dev.prism.gradle.PrismProjectPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "Leclown"
            url = uri("https://maven.leclowndu93150.dev/releases")
            credentials {
                username = System.getenv("MAVEN_USER") ?: ""
                password = System.getenv("MAVEN_PASS") ?: ""
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
