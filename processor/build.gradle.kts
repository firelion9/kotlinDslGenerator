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

    implementation("com.squareup:kotlinpoet:1.10.2")
    implementation("com.squareup:kotlinpoet-ksp:1.10.2")

    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")

    implementation(project(":annotations"))

    implementation ("com.google.auto.service:auto-service:1.0.1")
    kapt("com.google.auto.service:auto-service:1.0.1")
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs +
                listOf("-opt-in=kotlin.RequiresOptIn", "-opt-in=com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview")
    }
}