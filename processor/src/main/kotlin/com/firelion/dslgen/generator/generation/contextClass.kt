/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.*
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
    .addTypeVariables(typeVariables.map { it.copy(reified = false) })
    .primaryConstructor(
        FunSpec.constructorBuilder()
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(PublishedApi::class)
            .build()
    )
    .apply {
        functionParameters.forEach { (it, type) ->
            if (type.isArrayType(data)) {
                val elementTypeName = type.arrayElementTypeOrNull(data)!!
                    .toTypeNameFix(typeParameterResolver)

                addProperty(
                    PropertySpec.Companion.builder(
                        data.namingStrategy.backingPropertyName(it.name!!.asString()),
                        MUTABLE_LIST.parameterizedBy(elementTypeName),
                        KModifier.INTERNAL
                    )
                        .addAnnotation(PublishedApi::class)
                        .mutable()
                        .initializer(data.arrayBackingPropertyInitializer)
                        .build()
                )
            } else {
                addProperty(
                    PropertySpec.Companion.builder(
                        data.namingStrategy.backingPropertyName(it.name!!.asString()),
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
                    data.namingStrategy.initializationInfoName(it),
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