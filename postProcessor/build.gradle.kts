/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

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
            description = "post processor for The Dsl Generator"
        }
    }
}

dependencies {
    implementation(libs.kotlin.compiler)

    compileOnly(libs.google.auto.service)
    kapt(libs.google.auto.service)

    implementation(project(":annotations"))
}
