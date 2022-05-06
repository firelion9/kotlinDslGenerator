/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.castFromBackingFieldType
import com.firelion.dslgen.generator.util.makeInlineIfRequested
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSName
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
    exitFunction: KSName,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
) {
    FunSpec.builder(CREATE)
        .makeInlineIfRequested(generationParameters)
        .addModifiers(KModifier.INTERNAL)
        .addAnnotation(PublishedApi::class)
        .addTypeVariables(typeVariables)
        .receiver(contextClassName)
        .returns(returnType.toTypeNameFix(typeParameterResolver))
        .apply {
            val requiresPostProcess = data.allowDefaultArguments && functionParameters.any { it.first.hasDefault }

            functionParameters.forEachIndexed { index, (param, _) ->
                if (data.allowDefaultArguments && !param.hasDefault)
                    addCode(checkInitialization(index, param.name!!.asString()))
            }

            if (requiresPostProcess) addCode("%M()\n", POST_PROCESSOR_MARKER_NAME)

            addCode("return %N(\n",
                if (exitFunction.getShortName() == "<init>") exitFunction.getQualifier().let {
                    MemberName(it.substringBeforeLast("."),
                        it.substringAfterLast("."))
                } else MemberName(exitFunction.getQualifier(), exitFunction.getShortName()))

            addCode("⇥")
            functionParameters.asSequence().forEach { (param, type) ->
                val name = "\$\$${param.name!!.asString()}\$\$"

                fun addWithToArray(typeName: String) =
                    addCode("*%N.to${typeName}Array(),\n", name)

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
                        val cast = type.castFromBackingFieldType(type.toTypeNameFix(typeParameterResolver))
                        addCode("%N$cast,\n", name)
                    }
                }
            }
            addCode("⇤")

            addCode(")")
        }
        .build()
        .run(this::addFunction)
}