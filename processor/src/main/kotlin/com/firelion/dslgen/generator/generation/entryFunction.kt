/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.Data
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
    contextClassName: TypeName,
    returnType: KSType,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    @Suppress("UNUSED_PARAMETER") data: Data, // may be used in future for some reasons
) {
    val paramName = "builderAction"
    FunSpec.builder(generationParameters.functionName!!)
        .apply {
            if (generationParameters.makeInline) {
                addModifiers(KModifier.INLINE)
            }
        }
        .addAnnotation(EXPERIMENTAL_CONTRACTS_OPT_IN)
        .addAnnotation(dslMarker)
        .addTypeVariables(typeVariables)
        .addParameter(paramName, LambdaTypeName.get(receiver = contextClassName, returnType = UNIT))
        .returns(returnType.toTypeNameFix(typeParameterResolver))
        .apply {
            addCode(
                """%M {
                        |   callsInPlace(builderAction, %M)
                        |}
                        |""".trimMargin(),
                CONTRACT_NAME,
                EXACTLY_ONCE_NAME
            )

            addCode(
                "return %T().apply { builderAction() }.%N()\n",
                contextClassName.copy(annotations = emptyList()),
                CREATE
            )
        }
        .build()
        .run(this::addFunction)
}