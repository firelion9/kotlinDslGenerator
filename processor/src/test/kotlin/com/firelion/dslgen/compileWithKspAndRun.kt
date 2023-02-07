/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.intellij.lang.annotations.Language
import kotlin.test.assertEquals

/**
 * Compiles and runs specified [source] with the KSP processor ([DslSymbolProcessor]).
 *
 * Runs compilation twice with different parameters because
 * to address the issue with KSP support in testing library.
 *
 * Based on `vnermolaev`'s workaround from
 * `https://github.com/tschuchortdev/kotlin-compile-testing/issues/72#issuecomment-744475289`
 */
internal fun compileWithKspAndRun(@Language("kotlin") source: String): Any? {
    val kotlinSource = SourceFile.kotlin("Source.kt", source)

    val compilation = KotlinCompilation().apply {
        inheritClassPath = true

        sources = listOf(kotlinSource)

        symbolProcessorProviders = listOf(DslSymbolProcessorProvider())
        messageOutputStream = System.out
    }
    compilation.compile()

    val result = compilation.apply {
        sources += compilation.kspGeneratedSourceFiles

        symbolProcessorProviders = emptyList()
    }.compile()

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "Compilation failed")

    return result.classLoader.loadClass("SourceKt").getDeclaredMethod("main").invoke(null)
}

private val KotlinCompilation.kspGeneratedSourceFiles: List<SourceFile>
    get() = kspSourcesDir.resolve("kotlin")
        .walk()
        .filter { it.isFile }
        .map { SourceFile.fromPath(it.absoluteFile) }
        .toList()