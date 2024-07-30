extra["minecraft"] = "1.12.2"

apply("https://github.com/SettingDust/MinecraftGradleScripts/raw/main/common.gradle.kts")

apply("https://github.com/SettingDust/MinecraftGradleScripts/raw/main/kotlin.gradle.kts")

apply("https://github.com/SettingDust/MinecraftGradleScripts/raw/main/forge.gradle.kts")

apply("https://github.com/SettingDust/MinecraftGradleScripts/raw/main/mixin.gradle.kts")

dependencyResolutionManagement.versionCatalogs.named("catalog") {
    val exposed = "0.52.0"
    library("exposed-core", "org.jetbrains.exposed", "exposed-core").version(exposed)
    library("exposed-dao", "org.jetbrains.exposed", "exposed-dao").version(exposed)
    library("exposed-jdbc", "org.jetbrains.exposed", "exposed-jdbc").version(exposed)
    library("exposed-json", "org.jetbrains.exposed", "exposed-json").version(exposed)

    library("mysql", "org.mariadb.jdbc", "mariadb-java-client").version("3.4.1")
    library("hikaricp", "com.zaxxer", "HikariCP").version("4.0.3")

    bundle("exposed", listOf("exposed-core", "exposed-dao", "exposed-jdbc", "exposed-json", "mysql", "hikaricp"))

    fun curseMaven(alias: String, slug: String, id: Int, version: Int) {
        library(alias, "curse.maven", "$slug-$id").version(version.toString())
    }

    fun curseMaven(slug: String, id: Int, version: Int) = curseMaven(slug, slug, id, version)

    curseMaven("ftb-quests", "ftb-quests-forge", 289412, 3015063)
    curseMaven("ftb-library", "ftb-library-legacy-forge", 237167, 2985811)
    curseMaven("item-filters", 309674, 3003364)

    curseMaven("game-stages", 268655, 2716924)
    curseMaven("item-stages", 280316, 2810185)
    curseMaven("bookshelf", 228525, 2717168)
    library("crafttweaker", "CraftTweaker2", "CraftTweaker2-MC1120-Main").version("1.12-4.1.20.648")

    curseMaven("flux-networks", 248020, 3178199)
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

val name: String by settings

rootProject.name = name
