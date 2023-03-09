/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

/**
 * Generates function that calls [exitFunction] using parameters from context class and returns its result.
 */
internal fun FileSpec.Builder.generateCreateFunction(
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    functionParameters: List<Pair<KSValueParameter, KSType>>,
    contextClassName: TypeName,
    returnType: KSType,
    exitFunction: KSFunctionDeclaration,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
) {
    val hasReifiedTypeParameters = typeVariables.any { it.isReified }

    FunSpec.builder(CREATE)
        .makeInlineIfRequested(
            generationParameters,
            hasSomethingToInline = hasReifiedTypeParameters
        )
        .addModifiers(KModifier.INTERNAL)
        .addAnnotation(PublishedApi::class)
        .addTypeVariables(typeVariables)
        .receiver(contextClassName)
        .returns(returnType.toTypeNameFix(typeParameterResolver))
        .apply {
            val requiresPostProcess = data.allowDefaultArguments && functionParameters.any { it.first.hasDefault }

            functionParameters.forEachIndexed { index, (param, _) ->
                if (!(param.hasDefault && data.allowDefaultArguments) && !param.resolveActualType(data)
                        .isArrayType(data)
                )
                    addCode(checkInitialization(index, param.name!!.asString()))
            }

            if (requiresPostProcess) addCode("%M()\n", POST_PROCESSOR_MARKER_NAME)

            addCode("return ")

            addCode(
                CodeBlock.Builder()
                    .addFunctionCall(
                        exitFunction,
                        functionParameters.asSequence().map { (param, type) ->
                            val name = "\$\$${param.name!!.asString()}\$\$"

                            fun addWithToArray(typeName: String) =
                                CodeBlock.of((if (param.isVararg) "*" else "") + "%N.to${typeName}Array()", name)
                                    .toString()

                            when {
                                data.usefulTypes.ksArray.isAssignableFrom(type) ->
                                    addWithToArray("Typed")

                                data.usefulTypes.ksBooleanArray == type ->
                                    addWithToArray("Boolean")

                                data.usefulTypes.ksByteArray == type ->
                                    addWithToArray("Byte")

                                data.usefulTypes.ksShortArray == type ->
                                    addWithToArray("Short")

                                data.usefulTypes.ksCharArray == type ->
                                    addWithToArray("Char")

                                data.usefulTypes.ksIntArray == type ->
                                    addWithToArray("Int")

                                data.usefulTypes.ksFloatArray == type ->
                                    addWithToArray("Float")

                                data.usefulTypes.ksLongArray == type ->
                                    addWithToArray("Long")

                                data.usefulTypes.ksDoubleArray == type ->
                                    addWithToArray("Double")

                                else -> {
                                    if (!type.isCastFromBackingFieldTypeSafe())
                                        addAnnotation(UNCHECKED_CAST)

                                    val cast = type.castFromBackingFieldType(type.toTypeNameFix(typeParameterResolver))
                                    CodeBlock.of("%N$cast", name).toString()
                                }
                            }
                        }
                    )
                    .build()
            )
        }
        .mergeAnnotations()
        .build()
        .run(this::addFunction)
}