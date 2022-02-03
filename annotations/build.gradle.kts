plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "com.firelion.dslgen"
version = "0.1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            description = "Annotations and markers for The Dsl Generator"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}