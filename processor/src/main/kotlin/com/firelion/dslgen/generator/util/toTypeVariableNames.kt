/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

internal fun List<KSTypeParameter>.toTypeVariableNames(
    typeParameterResolver: TypeParameterResolver,
) = map { ksTypeParameter ->
    TypeVariableName(
        name = ksTypeParameter.name.getShortName(),
        bounds = ksTypeParameter.bounds.map { it.resolve().toTypeNameFix(typeParameterResolver) }.toList(),
        variance = null, // Couldn't be OUT
    )
}
