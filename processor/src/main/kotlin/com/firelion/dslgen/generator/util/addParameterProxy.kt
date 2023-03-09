/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName

/**
 * Adds parameter to a [FunSpec.Builder] applying some extra modifiers to the parameter if required.
 */
internal fun FunSpec.Builder.addParameterProxy(name: String, typeName: TypeName, type: KSType, data: Data) = apply {
    ParameterSpec.builder(name, typeName)
        .apply {
            val isInline = this@addParameterProxy.modifiers.contains(KModifier.INLINE)
            val shouldAddNoInline = isInline && (type.isFunctionType || type.isSuspendFunctionType)

            if (shouldAddNoInline) {
                addModifiers(KModifier.NOINLINE)
            }
        }
        .build()
        .let(this::addParameter)
}