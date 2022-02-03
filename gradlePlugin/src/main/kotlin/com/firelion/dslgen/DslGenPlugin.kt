/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private const val VERSION = "0.1.0"
private const val ALLOW_DEFAULT_ARGS_OPTION = "com.firelion.dslgen.allowDefaultArguments"

/**
 * *The Kotlin Dsl Generator* gradle plugin.
 */
class DslGenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(com.google.devtools.ksp.gradle.KspGradleSubplugin::class.java)

        with(target.dependencies) {
            // add compile annotations dependency
            add("compileOnly", "com.firelion.dslgen:annotations:$VERSION")

            // add KSP processor
            add("ksp", "com.firelion.dslgen:processor:$VERSION")
        }

        target.tasks.withType(KotlinCompile::class.java).forEach { kotlinCompile ->
            // post-process output directory of compileKotlin tasks
            kotlinCompile.doLast { task ->
                task.outputs.files.files.forEach {
                    postProcessDir(it)
                }
            }
            // add argument to allow OptIn usage
            kotlinCompile.kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        }

        // add generated DSL files to source sets
        target.extensions.configure(KotlinJvmProjectExtension::class.java) { ext ->
            ext.sourceSets.forEach {
                it.kotlin.srcDir("build/generated/ksp/${it.name}/kotlin")
            }
        }

        // enable default arguments
        target.extensions.findByType(KspExtension::class.java)!!.apply {
            arg(ALLOW_DEFAULT_ARGS_OPTION, "true")
        }
    }
}