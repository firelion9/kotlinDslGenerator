/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/**
 * Finds function to construct DSL function's parameter.
 *
 * By default, returns class's public primary constructor with at least 1 parameter (if present).
 * For some collections (lists, sets, maps and sequences) returns functions like arrayListOf or sequenceOf.
 */
internal fun KSClassDeclaration.findConstructionFunction(resolver: Resolver, data: Data): KSFunctionDeclaration? {
    fun resolveFunction(name: String) =
        resolver.getFunctionDeclarationsByName(resolver.getKSNameFromString(name), includeTopLevel = true)
            .find {
                it.parameters.firstOrNull()?.type?.resolve()
                    ?.let { it1 -> data.usefulTypes.ksArray.isAssignableFrom(it1) } ?: false
            }

    return when (qualifiedName?.asString()) {
        "kotlin.collections.Map" -> resolveFunction("kotlin.collections.mapOf")
        "kotlin.collections.MutableMap" -> resolveFunction("kotlin.collections.mutableMapOf")
        "kotlin.collections.HashMap" -> resolveFunction("kotlin.collections.hashMapOf")

        "kotlin.collections.Set" -> resolveFunction("kotlin.collections.setOf")
        "kotlin.collections.MutableSet" -> resolveFunction("kotlin.collections.mutableSetOf")
        "kotlin.collections.HashSet" -> resolveFunction("kotlin.collections.hashSetOf")

        "kotlin.collections.List" -> resolveFunction("kotlin.collections.listOf")
        "kotlin.collections.MutableList" -> resolveFunction("kotlin.collections.mutableListOf")
        "kotlin.collections.ArrayList" -> resolveFunction("kotlin.collections.arrayListOf")

        "kotlin.sequences.Sequence" -> resolveFunction("kotlin.sequences.sequenceOf")

        else -> primaryConstructor?.takeIf { it.isPublic() && it.parameters.isNotEmpty() }
    }
}