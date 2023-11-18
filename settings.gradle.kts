pluginManagement {
    // when using additional gradle plugins like shadow,
    // add their repositories to this list!
    repositories {
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://maven.minecraftforge.net/")
        gradlePluginPortal()
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version("0.5.0")
        kotlin("jvm") version "1.9.10"
        kotlin("plugin.serialization") version "1.9.10"
    }
}

rootProject.name = "DustyDataSync"
