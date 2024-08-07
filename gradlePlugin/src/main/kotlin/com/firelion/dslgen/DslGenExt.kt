/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.google.devtools.ksp.gradle.KspExtension

open class DslGenExt(private val kspExtension: KspExtension) {

    var dslPostProcessorMode: DslPostProcessorMode = DslPostProcessorMode.COMPILER_PLUGIN

    var allowDefaultArgs: Boolean
        get() = kspExtension.arguments[ALLOW_DEFAULT_ARGS_OPTION] == "true"
        set(value) {
            kspExtension.arg(ALLOW_DEFAULT_ARGS_OPTION, value.toString())
        }

    companion object {
        private const val ALLOW_DEFAULT_ARGS_OPTION = "com.firelion.dslgen.allowDefaultArguments"
    }
}

enum class DslPostProcessorMode {
    LEGACY, COMPILER_PLUGIN;
}
