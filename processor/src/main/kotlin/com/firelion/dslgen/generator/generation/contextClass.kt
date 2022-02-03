/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.backingPropertyInitializer
import com.firelion.dslgen.generator.util.backingPropertyType
import com.firelion.dslgen.generator.util.isArrayType
import com.firelion.dslgen.util.divideAndRoundUp
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

/**
 * Generates context class that would store specified [functionParameters] and extra initialization info.
 */
internal fun FileSpec.Builder.generateContextClass(
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    functionParameters: List<Pair<KSValueParameter, KSType>>,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
) = TypeSpec.classBuilder(generationParameters.contextClassName)
    .addTypeVariables(typeVariables)
    .primaryConstructor(
        FunSpec.constructorBuilder()
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(PublishedApi::class)
            .build()
    )
    .apply {
        functionParameters.forEach { (it, type) ->
            if (type.isArrayType(data)) {
                val elementTypeName = type.arguments[0].type!!.resolve()
                    .toTypeNameFix(typeParameterResolver)

                addProperty(
                    PropertySpec.Companion.builder(
                        "\$\$${it.name!!.asString()}\$\$",
                        MUTABLE_LIST.parameterizedBy(elementTypeName),
                        KModifier.INTERNAL
                    )
                        .addAnnotation(PublishedApi::class)
                        .mutable()
                        .initializer("%T()", LINKED_LIST)
                        .build()
                )
            } else {
                addProperty(
                    PropertySpec.Companion.builder(
                        "\$\$${it.name!!.asString()}\$\$",
                        type.backingPropertyType().toTypeNameFix(typeParameterResolver),
                        KModifier.INTERNAL
                    )
                        .addAnnotation(PublishedApi::class)
                        .addAnnotation(JvmField::class)
                        .mutable()
                        .initializer(type.backingPropertyInitializer())
                        .build()
                )
            }
        }

        repeat(functionParameters.size divideAndRoundUp Int.SIZE_BITS) {
            addProperty(
                PropertySpec.builder(
                    INITIALIZATION_INFO_PREFIX + it,
                    INT,
                    KModifier.INTERNAL
                )
                    .addAnnotation(PublishedApi::class)
                    .addAnnotation(JvmField::class)
                    .mutable()
                    .initializer("%L", 0.inv())
                    .build()
            )
        }
    }
    .build()
    .also(this::addType)