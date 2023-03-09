/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.squareup.kotlinpoet.*

/**
 * Removes unused (not presented in [usedParameters]) type parameters from list.
 *
 * This function is used to preserve source type parameter order.
 */
internal fun List<TypeVariableName>.filterUsed(usedParameters: Set<TypeVariableName>): List<TypeVariableName> =
    filter { it in usedParameters }

/**
 * Replaces unused (not presented in [usedParameters]) type parameters with stars.
 */
internal fun TypeName.starProjectUnusedParameters(usedParameters: Set<TypeVariableName>): TypeName =
    when (this) {
        is TypeVariableName -> if (this in usedParameters) this else STAR
        is ParameterizedTypeName -> this.copy(
            typeArguments = this.typeArguments.map {
                it.starProjectUnusedParameters(usedParameters)
            }
        )

        else -> this
    }

/**
 * Returns set of all type variables used in this [TypeName].
 */
internal fun TypeName.usedTypeVariables(): Set<TypeVariableName> =
    mutableSetOf<TypeVariableName>().also {
        fillUsedTypeVariablesRecursive(it)
    }

/**
 * Returns set of all type variables used in this list of [TypeName]s.
 */
internal fun List<TypeName>.usedTypeVariables(): Set<TypeVariableName> =
    mutableSetOf<TypeVariableName>().also {
        forEach { type ->
            type.fillUsedTypeVariablesRecursive(it)
        }
    }

private fun TypeName.fillUsedTypeVariablesRecursive(out: MutableSet<TypeVariableName>) {
    when (this) {
        is TypeVariableName -> out.add(this)

        is ParameterizedTypeName -> typeArguments.forEach { it.fillUsedTypeVariablesRecursive(out) }

        is LambdaTypeName -> {
            receiver?.fillUsedTypeVariablesRecursive(out)
            returnType.fillUsedTypeVariablesRecursive(out)

            parameters.forEach {
                it.type.fillUsedTypeVariablesRecursive(out)
            }
        }

        else -> Unit
    }
}
