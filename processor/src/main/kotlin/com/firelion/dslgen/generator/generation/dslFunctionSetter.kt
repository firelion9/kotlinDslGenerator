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
 * Generates context class property setter with syntax `name { /* subDSL */ }`.
 */
internal fun FileSpec.Builder.generateDslFunctionSetter(
    name: String,
    parameterName: String,
    backingPropertyType: KSType,
    backingPropertyIndex: Int,
    exitFunction: KSFunctionDeclaration,
    requiresNoInitialization: Boolean,
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    typeParameters: List<KSTypeParameter>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
) {
    data.logger.logging { "generating DSL function setter $name" }

    val endTypeArgs = backingPropertyType.resolveEndTypeArguments(data)

    val innerContextClassName =
        processFunction(
            exitFunction,
            data,
            generationParameters,
            typeVariables,
            typeParameters,
            endTypeArgs,
        )

    val (typeMapping, extraParameters) = inferTypeParameters(
        typeParameters,
        backingPropertyType,
        exitFunction.typeParameters,
        emptyList(),
        exitFunction.returnType!!.resolve(),
        data,
        shouldInferNewParameters = true
    )

    if (extraParameters.isNotEmpty()) shouldNotBeReached()

    val innerContextTypeName =
        exitFunction.typeParameters
            .asSequence()
            .map { typeMapping.getValue(it) }
            .map { it.toTypeNameFix(typeParameterResolver) }
            .toList()
            .let { innerContextClassName.withTypeArguments(it) }
            .let { it.copy(annotations = it.annotations + listOf(generationParameters.dslMarker)) }

    val usedTypeVariables = innerContextTypeName.usedTypeVariables()

    addImport(innerContextClassName.packageName, data.namingStrategy.createFunctionName)

    val lambdaName = data.namingStrategy.builderLambdaName(name)
    FunSpec.builder(name)
        .addAnnotation(generationParameters.dslMarker)
        .makeInlineIfRequested(generationParameters, hasSomethingToInline = true)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.starProjectUnusedParameters(usedTypeVariables))
        .addParameter(
            lambdaName,
            LambdaTypeName.get(
                receiver = innerContextTypeName,
                returnType = UNIT
            )
        )
        .callsExactlyOnceInPlace(lambdaName)
        .apply {
            if (requiresNoInitialization)
                addCode(checkNoInitialization(backingPropertyIndex, parameterName, data))

            addCode(initialize(backingPropertyIndex, data))
            addCode(
                "this.%N = %T()\n.apply {%N() }\n.%N()\n",
                data.namingStrategy.backingPropertyName(parameterName),
                innerContextTypeName.copy(annotations = emptyList()),
                lambdaName,
                data.namingStrategy.createFunctionName
            )
        }
        .build()
        .run(this::addFunction)

}