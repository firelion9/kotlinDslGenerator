/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.logging
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

/**
 * Generates context class property setter with syntax `name(value)`.
 */
internal fun FileSpec.Builder.generateFunctionSetter(
    name: String,
    parameterName: String,
    backingPropertyType: KSType,
    backingPropertyIndex: Int,
    requiresNoInitialization: Boolean,
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    data: Data,
) {
    data.logger.logging { "generating function setter $name" }

    val backingPropertyTypeName = backingPropertyType.toTypeNameFix(typeParameterResolver)
    val usedTypeVariables = backingPropertyTypeName.usedTypeVariables()

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.starProjectUnusedParameters(usedTypeVariables))
        .addParameterProxy(name, backingPropertyTypeName, backingPropertyType, data)
        .apply {
            if (requiresNoInitialization)
                addCode(checkNoInitialization(backingPropertyIndex, parameterName, data))

            addCode(initialize(backingPropertyIndex, data))
            addCode("this.%N = %N\n", data.namingStrategy.backingPropertyName(parameterName), name)
        }
        .build()
        .run(this::addFunction)

}

