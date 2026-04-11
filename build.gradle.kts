plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "2.1.20"
}

group = "dev.prism"
version = "0.4.2"

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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(gradleApi())
    implementation("net.fabricmc:fabric-loom:1.15.5")
    implementation("net.neoforged:moddev-gradle:2.0.141")
    implementation("me.modmuss50:mod-publish-plugin:1.1.0")
    implementation("com.gtnewhorizons:retrofuturagradle:2.0.2")
    implementation("net.minecraftforge:forgegradle:7.0.20")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
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
