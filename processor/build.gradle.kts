/*
 * Copyright (c) 2023-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

val kspVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    id("maven-publish")
}

apply(from = "../version.gradle.kts")

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            description = "The Dsl Generator KSP processor"
        }
    }
}

kotlin {
    compilerOptions {
        optIn = listOf("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

dependencies {
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.ksp.api)
    implementation(project(":annotations"))

    implementation(libs.google.auto.service)
    kapt(libs.google.auto.service)

    implementation(libs.kotlin.test)
    implementation(libs.test.ksp)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.test.ksp)
}

tasks.test {
    useJUnitPlatform()
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}
