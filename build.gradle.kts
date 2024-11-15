import groovy.lang.Closure
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers
import org.jetbrains.gradle.ext.Gradle

plugins {
    idea
    java
    `java-library`
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

    implementation(catalog.mixinbooter)
    annotationProcessor("org.ow2.asm:asm-debug-all:5.2")
    annotationProcessor("com.google.guava:guava:24.1.1-jre")
    annotationProcessor("com.google.code.gson:gson:2.8.6")
    annotationProcessor(mixin)
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

    shadow("org.apache.logging.log4j:log4j-slf4j-impl:2.24.1")
}

tasks {
    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
        archiveClassifier = "deobf"
        mergeServiceFiles()

        dependencies {
            exclude(dependency("org.jetbrains:annotations"))
            exclude(dependency("org.intellij.lang:annotations"))
            exclude(dependency("org.jetbrains.kotlin::"))
            exclude(dependency("org.jetbrains.kotlinx::"))
        }
    }

    reobfJar {
        dependsOn(shadowJar)

        inputJar.set(shadowJar.map { it.archiveFile }.get())
    }

    injectTags {
        outputClassName = "${project.group}.dustydatasync.Tags"
    }

    processResources {
        val properties =
            mapOf(
                "version" to project.version,
                "id" to id,
                "name" to project.name,
                "author" to author,
                "description" to project.description)
        inputs.properties(properties)
        filesMatching("mcmod.info") { expand(properties) }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
        inheritOutputDirs = true // Fix resources in IJ-Native runs
    }

    project {
        settings {
            runConfigurations {
                create<Gradle>("1. Run Client") { taskNames = listOf("runClient") }
                create<Gradle>("2. Run Server") { taskNames = listOf("runServer") }
                create<Gradle>("3. Run Obfuscated Client") { taskNames = listOf("runObfClient") }
                create<Gradle>("4. Run Obfuscated Server") { taskNames = listOf("runObfServer") }
            }

            taskTriggers { afterSync(tasks.injectTags) }
        }
    }
}
