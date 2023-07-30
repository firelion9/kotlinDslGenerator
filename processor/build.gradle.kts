/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

val kspVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("kapt")
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

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.squareup:kotlinpoet:1.14.2")
    implementation("com.squareup:kotlinpoet-ksp:1.14.2")

    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")

    implementation(project(":annotations"))

    implementation("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")

    testImplementation(kotlin("test"))
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.5.0")
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}
