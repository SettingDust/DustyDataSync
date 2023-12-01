import net.minecraftforge.gradle.common.util.MinecraftExtension
import net.minecraftforge.gradle.userdev.DependencyManagementExtension

buildscript {
    dependencies {
        classpath(group = "net.minecraftforge.gradle", name = "ForgeGradle", version = "4.+")
        classpath("org.spongepowered:mixingradle:0.7-SNAPSHOT")
    }
}

plugins {
    java
    idea
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("com.github.jmongard.git-semver-plugin") version "0.11.0"
}

apply(plugin = "net.minecraftforge.gradle")
apply(plugin = "org.spongepowered.mixin")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}

kotlin {
    jvmToolchain(8)
}

group = "settingdust"
version = semver.semVersion

configure<MinecraftExtension> {
    mappings("stable", "39-1.12")

    runs {
        create("client") {
            workingDirectory(project.file("run"))

            property("forge.logging.markers", "SCAN,LOADING,CORE")
            property("forge.logging.console.level", "debug")

            jvmArgs(
                "-Dmixin.hotSwap=true",
                "-Dmixin.checks.interfaces=true",
                "-Dmixin.debug.export=true",
                "-Dmixin.debug.verbose=true"
            )
        }

        create("server") {
            workingDirectory(project.file("run/server"))

            property("forge.logging.markers", "SCAN,LOADING,CORE")
            property("forge.logging.console.level", "debug")
        }
    }
}

repositories {
    maven("https://cursemaven.com") {
        content {
            includeGroup("curse.maven")
        }
    }
    maven("https://maven.cleanroommc.com") {
        content {
            includeGroup("zone.rong")
        }
    }
    maven("https://maven.blamejared.com/") {
        content {
            includeGroup("CraftTweaker2")
        }
    }
    maven("https://repo.spongepowered.org/repository/maven-public/")
    mavenCentral()
    mavenLocal()
}

val mcVersion: String by project
val forgeVersion: String by project

val exposedVersion: String by project

dependencies {
    "minecraft"("net.minecraftforge:forge:$mcVersion-$forgeVersion")

    implementation("zone.rong:mixinbooter:8.9")

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor") { isTransitive = false }
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.2.1-beta.2") { isTransitive = false }

    val fg = project.extensions.getByName<DependencyManagementExtension>("fg")
    implementation(fg.deobf("net.shadowfacts:Forgelin:1.9.0"))

    implementation(fg.deobf("curse.maven:ftb-quests-forge-289412:3015063"))
    implementation(fg.deobf("curse.maven:ftb-library-legacy-forge-237167:2985811"))
    runtimeOnly(fg.deobf("curse.maven:item-filters-309674:3003364"))

    implementation(fg.deobf("curse.maven:game-stages-268655:2716924"))
    implementation(fg.deobf("curse.maven:item-stages-280316:2810185"))
    runtimeOnly(fg.deobf("curse.maven:bookshelf-228525:2717168"))
    runtimeOnly("CraftTweaker2:CraftTweaker2-MC1120-Main:1.12-4.1.20.648")

    implementation(fg.deobf("curse.maven:flux-networks-248020:3178199"))

    shadow(implementation("org.jetbrains.exposed", "exposed-core", exposedVersion))
    shadow(implementation("org.jetbrains.exposed", "exposed-dao", exposedVersion))
    shadow(implementation("org.jetbrains.exposed", "exposed-jdbc", exposedVersion))
    shadow(implementation("org.jetbrains.exposed", "exposed-json", exposedVersion))

    shadow(implementation("mysql", "mysql-connector-java", "8.0.33"))

    shadow(implementation("com.zaxxer", "HikariCP", "4.0.3"))

//    shadow(implementation("com.h2database", "h2", "2.2.224"))
}


tasks {
    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
        archiveClassifier.set("")
        mergeServiceFiles()

        dependencies {
            exclude(dependency("org.jetbrains:annotations"))
            exclude(dependency("org.intellij.lang:annotations"))
            exclude(dependency("org.jetbrains.kotlin::"))
            exclude(dependency("org.jetbrains.kotlinx::"))
        }

        finalizedBy("reobfJar")
    }

    build {
        dependsOn(shadowJar)
    }

    artifacts {
        archives(shadowJar)
    }
}
