/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*

/**
 * Reuses already resolved function parameter types to find array type parameters and mark them as reified.
 */
internal fun List<TypeVariableName>.withReifiedTypeVariables(
    functionParameters: List<Pair<KSValueParameter, KSType>>,
    contextClassSpec: TypeSpec,
    data: Data,
): List<TypeVariableName> {
    val toReify = mutableSetOf<TypeVariableName>()

    functionParameters.forEachIndexed { index, (_, type) ->
        if (data.usefulTypes.ksArray.isAssignableFrom(type)) {
            val elementType = (contextClassSpec.propertySpecs[index].type as ParameterizedTypeName)
                .typeArguments.first().getArrayParameter()

            if (elementType is TypeVariableName) toReify.add(elementType)
        }
    }

    return if (toReify.isEmpty()) {
        this
    } else this.map {
        if (it in toReify && !it.isReified) it.copy(reified = true)
        else it
    }
}


/**
 * Finds first non-array type parameter.
 */
private tailrec fun TypeName.getArrayParameter(): TypeName? =
    if (this is ParameterizedTypeName && this.rawType == ARRAY) this.typeArguments.first().getArrayParameter()
    else this