/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

plugins {
    kotlin("jvm")
    id("maven-publish")
}

apply(from = "../version.gradle.kts")

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            description = "Annotations and markers for The Dsl Generator"
        }
    }
}
