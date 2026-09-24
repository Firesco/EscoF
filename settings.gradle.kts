import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!file(".git").exists()) {
    // Leaf start - project setup
    val errorText = """
        
        =====================[ ERROR ]=====================
         The EscoF project directory has no Git metadata.
         
         Clone your EscoF repository with Git, or use
         python3 build-escof.py --java-home /path/to/jdk25
         to prepare and build an extracted source archive.
         
         Build output: dist/escof-26.2-0.7.0.jar
         
         See https://github.com/PaperMC/Paper/blob/main/CONTRIBUTING.md
         for further information on building and modifying Paper forks.
        ===================================================
    """.trimIndent()
    // Leaf end - project setup
    error(errorText)
}

rootProject.name = "escof"

for (name in listOf("leaf-api", "leaf-server")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
}

gradle.lifecycle.beforeProject {
    val mcVersion = providers.gradleProperty("mcVersion").get().trim()
    val paperVersionChannel = providers.gradleProperty("channel").get().trim()
    val paperBuildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.trim()?.toInt()
    val versionString = if (paperBuildNumber == null) {
        "$mcVersion.local-SNAPSHOT"
    } else {
        "$mcVersion.build.$paperBuildNumber-${paperVersionChannel.lowercase()}"
    }
    version = versionString
}
