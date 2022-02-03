/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

private const val ALLOW_DEFAULT_ARGS_OPTION = "com.firelion.dslgen.allowDefaultArguments"

/**
 * KSP processor provider for *The Kotlin DSL Generator* processor.
 */
@AutoService(SymbolProcessorProvider::class)
class DslSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        DslSymbolProcessor(environment.codeGenerator, environment.logger, environment.options[ALLOW_DEFAULT_ARGS_OPTION] == "true")
}