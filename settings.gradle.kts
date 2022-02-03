pluginManagement {
    val kotlinVersion: String by settings
    val kspVersion: String by settings
    plugins {
        id("com.google.devtools.ksp") version kspVersion
        kotlin("jvm") version kotlinVersion
    }
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "kotlinDslGenerator"

include(":processor", ":annotations", ":postProcessor")
//include("workload")
include("gradlePlugin")
