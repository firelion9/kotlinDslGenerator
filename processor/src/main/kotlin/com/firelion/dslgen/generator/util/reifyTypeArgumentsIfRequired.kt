/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.squareup.kotlinpoet.*

/**
 * Adds `reified` modifier to all type parameters used as array parameters.
 */
internal fun TypeSpec.reifyTypeArgumentsIfRequired(): List<TypeVariableName> {
    val toRefine = mutableSetOf<TypeVariableName>()

    propertySpecs.forEach { prop ->
        prop.type.getArrayParameter()?.let { typeToRefine ->
            if (typeToRefine is TypeVariableName) toRefine.add(typeToRefine)
        }
    }

    return if (toRefine.isEmpty()) {
        typeVariables
    } else typeVariables.map {
        if (it in toRefine) it.copy(reified = true)
        else it
    }
}

/**
 * Returns strong type parameter (that wouldn't be erased) of this type or null.
 */
private tailrec fun TypeName.getArrayParameter(first: Boolean = true): TypeName? =
    if (this is ParameterizedTypeName && this.rawType == ARRAY) this.typeArguments.first().getArrayParameter(false)
    else if (first) null
    else this