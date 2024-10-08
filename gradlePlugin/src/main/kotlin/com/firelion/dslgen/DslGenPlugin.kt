/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * *The Kotlin Dsl Generator* gradle plugin.
 */
class DslGenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(DslGenSubplugin::class.java)
        target.pluginManager.apply(com.google.devtools.ksp.gradle.KspGradleSubplugin::class.java)

        with(target.dependencies) {
            // add compile annotations dependency
            add("compileOnly", "com.firelion.dslgen:annotations:$VERSION")

            // add KSP processor
            add("ksp", "com.firelion.dslgen:processor:$VERSION")
        }

        val kspExt = target.extensions.findByType(KspExtension::class.java)!!

        val dslGenExt = target.extensions.create(
            "dslGenerator",
            DslGenExt::class.java,
            kspExt
        ).apply {
            allowDefaultArgs = true
        }

        val kotlinVersionNumbers = getKotlinPluginVersion(target.logger).split(".")

        val kotlinVersionGraterThenOneSix =
            (kotlinVersionNumbers[0].toIntOrNull() ?: 0)
                .let { it == 1 && (kotlinVersionNumbers[1].toIntOrNull() ?: 0) > 6 || it > 1 }


        target.tasks.withType(KotlinCompile::class.java).forEach { kotlinCompile ->
            // post-process output directory of compileKotlin tasks
            kotlinCompile.doLast { task ->
                if (dslGenExt.dslPostProcessorMode == DslPostProcessorMode.LEGACY) {
                    task.outputs.files.files.forEach {
                        postProcessDir(it)
                    }
                }
            }
            // before Kotlin 1.7 kotlin.RequiresOptIn required to opt-in itself using -Xopt-in compiler option
            // to use kotlin.OptIn in source files
            // after Kotlin 1.7 kotlin.RequiresOptIn doesn't require to be opted-in
            if (!kotlinVersionGraterThenOneSix) {
                // add argument to allow OptIn usage
                kotlinCompile.compilerOptions.freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
            }
        }

        // add generated DSL files to source sets
        target.extensions.configure(KotlinJvmProjectExtension::class.java) { ext ->
            ext.sourceSets.forEach {
                it.kotlin.srcDir("build/generated/ksp/${it.name}/kotlin")
            }
        }
    }

    companion object {
        const val VERSION = "0.5.0" // @hardlink#001
    }
}
