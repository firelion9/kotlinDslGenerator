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
 * Generates context class array property adder with syntax `name(elementToAdd)`.
 */
internal fun FileSpec.Builder.generateCollectionAdder(
    name: String,
    elementType: KSType,
    parameterName: String,
    backingPropertyIndex: Int,
    typeVariables: List<TypeVariableName>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    generationParameters: GenerationParameters,
    data: Data,
) {
    data.logger.logging { "generating collection adder $name" }

    val elementTypeName = elementType.toTypeNameFix(typeParameterResolver)
    val usedTypeVariables = elementTypeName.usedTypeVariables()

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.starProjectUnusedParameters(usedTypeVariables))
        .addParameterProxy("element", elementTypeName, elementType, data)
        .apply {
            addCode(initialize(backingPropertyIndex, data))
            addCode(
                "this.%N.add(element)\n",
                data.namingStrategy.backingPropertyName(parameterName),
            )
        }
        .build()
        .run(this::addFunction)

}