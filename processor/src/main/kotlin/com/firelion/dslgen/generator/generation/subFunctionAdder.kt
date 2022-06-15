/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.makeInlineIfRequested
import com.firelion.dslgen.generator.util.startProjectUnusedParameters
import com.firelion.dslgen.generator.util.usedTypeVariables
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver

/**
 * Generates context class array property adder with syntax `name(/* args */)`.
 */
internal fun FileSpec.Builder.generateSubFunctionAdder(
    name: String,
    elementType: KSType,
    backingPropertyName: String,
    backingPropertyIndex: Int,
    exitFunction: KSFunctionDeclaration,
    generationParameters: GenerationParameters,
    typeParameters: List<KSTypeParameter>,
    typeVariables: List<TypeVariableName>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    data: Data,
) {
    val (inferredTypes, extraTypeParameters) = inferTypeParameters(
        typeParameters,
        elementType,
        exitFunction.typeParameters,
        exitFunction.parameters.map { it.type.resolve() }, // TODO: should we modify type of vararg parameters?
        exitFunction.returnType!!.resolve(),
        data
    )

    val resolver = exitFunction.typeParameters.toTypeParameterResolver(typeParameterResolver)
    val paramsWithType = exitFunction.parameters.map { it to it.type.resolve() }

    val usedTypeVariables = elementType.toTypeNameFix(resolver).usedTypeVariables()

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .addTypeVariables(extraTypeParameters.toTypeVariableNames(resolver))
        .receiver(contextClassName.startProjectUnusedParameters(usedTypeVariables))
        .apply {
            paramsWithType.forEach { (param, type) ->
                val kpType = type.replaceTypeParameters(inferredTypes, data).toTypeNameFix(resolver)

                addParameter(param.name!!.asString(), kpType)
            }
        }
        .apply {
            val functionName =
                if (exitFunction.isConstructor())
                    (exitFunction.parentDeclaration as KSClassDeclaration).qualifiedName
                else exitFunction.qualifiedName

            addCode(initialize(backingPropertyIndex))
            addCode(
                "this.%N.add(${functionName!!.asString()}(${
                    exitFunction.parameters.joinToString(
                        prefix = "\n",
                        separator = ",\n",
                        postfix = "\n"
                    ) { if (it.isVararg) "*%N" else "%N" }
                }))",
                "\$\$$backingPropertyName\$\$",
                *parameters.map { it.name }.toTypedArray()
            )
        }
        .build()
        .run(this::addFunction)
}
