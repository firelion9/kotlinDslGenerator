/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

val kspVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-gradle-plugin")
    id("maven-publish")
}

apply(from = "../version.gradle.kts")

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
    compileOnly(libs.kotlin.gradle)
    implementation(libs.kotlin.gradle.api)

    implementation(gradleApi())
    implementation(libs.ksp.gradle)

    implementation(project(":postProcessor"))
}
