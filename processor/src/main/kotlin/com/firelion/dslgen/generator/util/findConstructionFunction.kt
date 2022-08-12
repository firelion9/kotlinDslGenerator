/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/**
 * Finds function to construct DSL function's parameter.
 *
 * By default, returns class's public primary constructor with at least 1 parameter (if present).
 * For some collections (lists, sets, maps and sequences) returns functions like arrayListOf or sequenceOf.
 */
internal fun KSClassDeclaration.findConstructionFunction(data: Data): KSFunctionDeclaration? {
    fun resolveVarargFunction(name: String) =
        data.resolver.getFunctionDeclarationsByName(data.resolver.getKSNameFromString(name), includeTopLevel = true)
            .find {
                it.parameters.size == 1 && it.parameters.first().isVararg
            }

    return when (qualifiedName?.asString()) {
        "kotlin.collections.Map" -> resolveVarargFunction("kotlin.collections.mapOf")
        "kotlin.collections.MutableMap" -> resolveVarargFunction("kotlin.collections.mutableMapOf")
        "kotlin.collections.HashMap" -> resolveVarargFunction("kotlin.collections.hashMapOf")

        "kotlin.collections.Set" -> resolveVarargFunction("kotlin.collections.setOf")
        "kotlin.collections.MutableSet" -> resolveVarargFunction("kotlin.collections.mutableSetOf")
        "kotlin.collections.HashSet" -> resolveVarargFunction("kotlin.collections.hashSetOf")

        "kotlin.collections.List" -> resolveVarargFunction("kotlin.collections.listOf")
        "kotlin.collections.MutableList" -> resolveVarargFunction("kotlin.collections.mutableListOf")
        "kotlin.collections.ArrayList" -> resolveVarargFunction("kotlin.collections.arrayListOf")

        "kotlin.sequences.Sequence" -> resolveVarargFunction("kotlin.sequences.sequenceOf")

        else -> primaryConstructor?.takeIf { it.isPublic() && it.parameters.isNotEmpty() }
    }.also {
        data.logger.logging(
            "${qualifiedName?.asString()} resolved construction function is $it"
        )
    }
}