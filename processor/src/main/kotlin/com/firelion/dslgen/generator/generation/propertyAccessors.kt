/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.annotations.PropertyAccessor
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.logging
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.TypeParameterResolver


/**
 * Generates context class property getter/setter (depending on [propertyAccessor]) with syntax `name`/`name = value`.
 */
internal fun FileSpec.Builder.generatePropertyAccessors(
    propertyAccessor: PropertyAccessor,
    name: String,
    backingPropertyName: String,
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
    data.logger.logging { "generating property accessor $name, propertyAccessor=$propertyAccessor" }

    require(propertyAccessor != PropertyAccessor.NO)

    val propertyType = backingPropertyType.toTypeNameFix(typeParameterResolver)
    val usedTypeVariables = propertyType.usedTypeVariables()

    PropertySpec.builder(name, propertyType)
        .addAnnotation(dslMarker)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.starProjectUnusedParameters(usedTypeVariables))
        .apply {
            if (propertyAccessor == PropertyAccessor.GETTER_AND_SETTER) {
                mutable()
                setter(
                    FunSpec.setterBuilder()
                        .makeInlineIfRequested(generationParameters)
                        .addParameterProxy(
                            "value",
                            backingPropertyType.toTypeNameFix(typeParameterResolver),
                            backingPropertyType,
                            data
                        )
                        .apply {
                            if (requiresNoInitialization)
                                addCode(checkNoInitialization(backingPropertyIndex, backingPropertyName))

                            addCode(initialize(backingPropertyIndex))
                            addCode("this.%N = value\n", "\$\$$backingPropertyName\$\$")
                        }
                        .build()
                )
            }
        }
        .getter(
            FunSpec.getterBuilder()
                .makeInlineIfRequested(generationParameters)
                .apply {
                    addCode(checkInitialization(backingPropertyIndex, backingPropertyName))

                    if (!backingPropertyType.isCastFromBackingFieldTypeSafe())
                        addAnnotation(UNCHECKED_CAST)

                    val cast = backingPropertyType.castFromBackingFieldType(propertyType)

                    addCode("return this.%N$cast\n", "\$\$$backingPropertyName\$\$")
                }
                .mergeAnnotations()
                .build()
        )
        .build()
        .run(this::addProperty)
}
