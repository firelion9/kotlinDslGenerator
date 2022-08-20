/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.filterUsed
import com.firelion.dslgen.generator.util.makeInlineIfRequested
import com.firelion.dslgen.generator.util.usedTypeVariables
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
    backingPropertyName: String,
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
        .receiver(contextClassName.startProjectUnusedParameters(usedTypeVariables))
        .addParameter("element", elementTypeName)
        .apply {
            addCode(initialize(backingPropertyIndex))
            addCode(
                "this.%N.add(element)\n",
                "\$\$$backingPropertyName\$\$",
            )
        }
        .build()
        .run(this::addFunction)

}