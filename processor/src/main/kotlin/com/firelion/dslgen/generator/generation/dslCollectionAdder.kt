/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.processFunction
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.logging
import com.firelion.dslgen.util.shouldNotBeReached
import com.firelion.dslgen.util.toTypeNameFix
import com.firelion.dslgen.util.withTypeArguments
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

/**
 * Generates context class array property adder with syntax `name { /* element to add subDSL */ }`.
 */
internal fun FileSpec.Builder.generateDslCollectionAdder(
    name: String,
    elementType: KSType,
    parameterName: String,
    backingPropertyIndex: Int,
    itemFunction: KSFunctionDeclaration,
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    typeParameters: List<KSTypeParameter>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
) {
    data.logger.logging { "generating DSL collection adder $name" }

    val endTypeArgs = elementType.resolveEndTypeArguments(data)

    val elementContextClassName = processFunction(
        itemFunction,
        data,
        generationParameters,
        typeVariables,
        typeParameters,
        endTypeArgs
    )

    val (typeMapping, extraParameters) = inferTypeParameters(
        typeParameters,
        elementType,
        itemFunction.typeParameters,
        emptyList(),
        itemFunction.returnType!!.resolve(),
        data,
        shouldInferNewParameters = true
    )

    if (extraParameters.isNotEmpty()) shouldNotBeReached()

    val elementContextTypeName =
        itemFunction.typeParameters
            .asSequence()
            .map { typeMapping.getValue(it) }
            .map { it.toTypeNameFix(typeParameterResolver) }
            .toList()
            .let { elementContextClassName.withTypeArguments(it) }
            .let { it.copy(annotations = it.annotations + listOf(generationParameters.dslMarker)) }

    val usedTypeVariables = elementContextTypeName.usedTypeVariables()

    addImport(elementContextClassName.packageName, data.namingStrategy.createFunctionName)

    val lambdaName = data.namingStrategy.builderLambdaName(name)

    FunSpec.builder(name)
        .addAnnotation(generationParameters.dslMarker)
        .makeInlineIfRequested(generationParameters, hasSomethingToInline = true)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.starProjectUnusedParameters(usedTypeVariables))
        .addParameter(
            lambdaName,
            LambdaTypeName.get(
                receiver = elementContextTypeName,
                returnType = UNIT
            )
        )
        .callsExactlyOnceInPlace(lambdaName)
        .apply {
            addCode(initialize(backingPropertyIndex, data))
            addCode(
                "this.%N.add(%T().apply {%N() }.%N())\n",
                data.namingStrategy.backingPropertyName(parameterName),
                elementContextTypeName.copy(annotations = emptyList()),
                lambdaName,
                data.namingStrategy.createFunctionName
            )
        }
        .build()
        .run(this::addFunction)

}