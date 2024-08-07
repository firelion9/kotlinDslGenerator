/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:OptIn(ExperimentalCompilerApi::class)

package com.firelion.dslgen

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGenerator
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.hasAnnotation

@AutoService(CompilerPluginRegistrar::class)
class DslGenPluginRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (!configuration.getBoolean(DslGenConstants.keyEnabled)) return

        ClassGeneratorExtension.registerExtension(object : ClassGeneratorExtension {
            override fun generateClass(generator: ClassGenerator, declaration: IrClass?): ClassGenerator {
                return when {
                    declaration?.hasAnnotation(DslGenConstants.GENERATE_DSL_FQ_NAME) == true -> {
                        DslGenClassGenerator(generator)
                    }

                    else -> {
                        generator
                    }
                }
            }
        })
    }
}
