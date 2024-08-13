/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.callsExactlyOnceInPlace
import com.firelion.dslgen.generator.util.makeInlineIfRequested
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

/**
 * Generates DSL entry function that can be actually called by user.
 */
internal fun FileSpec.Builder.generateEntryFunction(
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    contextTypeName: TypeName,
    returnType: KSType,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
) {
    val lambdaName = data.namingStrategy.builderLambdaName(null)

    FunSpec.builder(generationParameters.functionName!!)
        .makeInlineIfRequested(generationParameters, hasSomethingToInline = true)
        .addAnnotation(generationParameters.dslMarker)
        .addTypeVariables(typeVariables)
        .addParameter(lambdaName, LambdaTypeName.get(receiver = contextTypeName, returnType = UNIT))
        .returns(returnType.toTypeNameFix(typeParameterResolver))
        .callsExactlyOnceInPlace(lambdaName)
        .apply {
            addCode(
                "return %T().apply { %N() }.%N()\n",
                contextTypeName.copy(annotations = emptyList()),
                lambdaName,
                data.namingStrategy.createFunctionName
            )
        }
        .build()
        .run(this::addFunction)
}
