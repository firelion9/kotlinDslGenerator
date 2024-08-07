/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.firelion.dslgen.annotations.DslGeneratorInternalApi
import com.firelion.dslgen.annotations.GeneratedDsl
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.name.FqName

object DslGenConstants {
    @OptIn(DslGeneratorInternalApi::class)
    val GENERATE_DSL_FQ_NAME = FqName(GeneratedDsl::class.qualifiedName!!)
    val keyEnabled = CompilerConfigurationKey<Boolean>("Post-processing enabled")

    const val INITIALIZATION_INFO_NAME_PREFIX = "\$initializationInfo\$" // @hardlink#002
    const val CREATE_FUNCTION_NAME = "\$create\$" // @hardlink#003
}
