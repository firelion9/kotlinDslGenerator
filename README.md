# The Kotlin DSL generator

## How to use

### Apply plugin

Clone this repo to your computer and run `./gradlew publishToMavenLocal`.
After this, add buildscript dependency and apply plugin
to your build.gradle.kts file 
(for build.gradle change `apply(plugin = "com.firelion.dslgen")`
to `apply(plugin: "com.firelion.dslgen")`):

```kotlin
buildscript {
    repositories {
//         ...
        mavenLocal()
    }
    dependencies {
//        ...         
        classpath("com.firelion.dslgen:gradlePlugin:0.1.0")
    }
}

apply(plugin = "com.firelion.dslgen")
//...
```

### Annotate a function
Apply `@GenerateDsl(MyDslMarker::class)` to a top-level function or constructor to generate a DSL for it.
`MyDslMarker` should be an annotation class with at least binary retention 
applicable to functions, types and classes and marked with `@DslMarker`:
```kotlin
@DslMarker
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class MyDslMarker
```