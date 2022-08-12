plugins {
    kotlin("jvm")
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

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.ow2.asm:asm:9.2")
}