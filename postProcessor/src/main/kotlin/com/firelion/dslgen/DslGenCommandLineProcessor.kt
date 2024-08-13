/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:OptIn(ExperimentalCompilerApi::class)

package com.firelion.dslgen

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration



@AutoService(CommandLineProcessor::class)
class DslGenCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String get() = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = OPTION_ENABLED,
            valueDescription = "<enabled>",
            description = "enable DSL context classes post-processing to use default argument in exit functions",
            required = false,
            allowMultipleOccurrences = false
        )
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) =
        when (option.optionName) {
            OPTION_ENABLED -> {
                configuration.put(DslGenConstants.keyEnabled, value.toBoolean())
            }

            else -> error("unknown option ${option.optionName}")
        }

    companion object {
        const val PLUGIN_ID = "com.firelion.dslgen"

        const val OPTION_ENABLED = "allowDslDefaultArgs"
    }
}