val kspVersion: String by project

plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    id("maven-publish")
}

apply(from = "../version.gradle.kts")

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("dslgen") {
            id = "com.firelion.dslgen"
            implementationClass = "com.firelion.dslgen.DslGenPlugin"
            description = "plugin for generating DSL for kotlin functions"
        }
    }
}
dependencies {
    implementation(kotlin("stdlib"))

    compileOnly(kotlin("gradle-plugin"))
    implementation(gradleApi())

    implementation(project(":postProcessor"))
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspVersion")
}
