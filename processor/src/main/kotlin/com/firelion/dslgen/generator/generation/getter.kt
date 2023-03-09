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
 * Generates context class property getter with syntax `name()`
 */
internal fun FileSpec.Builder.generateFunctionGetter(
    name: String,
    backingPropertyName: String,
    backingPropertyType: KSType,
    backingPropertyIndex: Int,
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    data: Data,
) {
    data.logger.logging { "generating function getter $name" }

    val returnType = backingPropertyType.toTypeNameFix(typeParameterResolver)
    val usedTypeVariables = returnType.usedTypeVariables()

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.starProjectUnusedParameters(usedTypeVariables))
        .returns(returnType)
        .apply {
            addCode(checkInitialization(backingPropertyIndex, backingPropertyName))

            if (!backingPropertyType.isCastFromBackingFieldTypeSafe())
                addAnnotation(UNCHECKED_CAST)

            val cast = backingPropertyType.castFromBackingFieldType(returnType)

            addCode("return %N$cast\n", "\$\$$backingPropertyName\$\$")
        }
        .mergeAnnotations()
        .build()
        .run(this::addFunction)

}