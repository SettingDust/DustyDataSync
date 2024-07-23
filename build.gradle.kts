import groovy.lang.Closure

plugins {
    idea
    java
    `maven-publish`
    alias(catalog.plugins.idea.ext)

    alias(catalog.plugins.kotlin.jvm)
    alias(catalog.plugins.kotlin.plugin.serialization)

    alias(catalog.plugins.git.version)

    alias(catalog.plugins.retro.gradle)

    alias(catalog.plugins.shadow)
}

apply(
    "https://github.com/SettingDust/MinecraftGradleScripts/raw/main/gradle_issue_15754.gradle.kts")

group = "settingdust"

val gitVersion: Closure<String> by extra

version = gitVersion()

val id: String by rootProject.properties
val name: String by rootProject.properties
val author: String by rootProject.properties
val description: String by rootProject.properties

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}

kotlin { jvmToolchain(8) }

base { archivesName = id }

minecraft {
    mcVersion = catalog.versions.minecraft

    mcpMappingChannel = "stable"
    mcpMappingVersion = "39"

    useDependencyAccessTransformers = true

    username = "Developer"

    extraRunJvmArguments.addAll(
        "-Dmixin.hotSwap=true", "-Dmixin.checks.interfaces=true", "-Dmixin.debug.export=true")

    injectedTags.set(mapOf("VERSION" to project.version, "ID" to id, "NAME" to name))
}

repositories {
    maven("https://cursemaven.com") { content { includeGroup("curse.maven") } }
    maven("https://maven.blamejared.com/") { content { includeGroup("CraftTweaker2") } }
}

dependencies {
    val mixin = modUtils.enableMixins(catalog.mixinbooter.get().toString(), "$id.refmap.json")

    implementation(mixin)
    shadow(catalog.mixinextras.common) { isTransitive = false }
    annotationProcessor(catalog.mixinextras.common) { isTransitive = false }

    implementation(catalog.bundles.exposed)
    shadow(catalog.bundles.exposed)

    implementation(catalog.kotlinx.coroutines)
    implementation(catalog.kotlinx.serialization.json)

    implementation(catalog.kotlin.forge)

    implementation(catalog.ftb.quests)
    implementation(catalog.ftb.library)
    runtimeOnly(catalog.item.filters)

    implementation(catalog.game.stages)
    implementation(catalog.item.stages)
    runtimeOnly(catalog.bookshelf)
    runtimeOnly(catalog.crafttweaker)

    implementation(catalog.flux.networks)
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

            relocate("com.llamalad7.mixinextras", "settingdust.mixinextras")
        }

        finalizedBy("reobfJar")
    }

    build { dependsOn(shadowJar) }

    artifacts { archives(shadowJar) }
}
