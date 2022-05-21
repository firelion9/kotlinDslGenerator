/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.processFunction
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.inferTypeParameters
import com.firelion.dslgen.generator.util.makeInlineIfRequested
import com.firelion.dslgen.generator.util.toTypeVariableNames
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver

/**
 * Generates context class property setter with syntax `name(value)`.
 */
internal fun FileSpec.Builder.generateFunctionSetter(
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
) {
    val backingPropertyTypeName = backingPropertyType.toTypeNameFix(typeParameterResolver)
    val usedTypeVariables = backingPropertyTypeName.usedTypeVariables()

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.startProjectUnusedParameters(usedTypeVariables))
        .addParameter(name, backingPropertyTypeName)
        .apply {
            if (requiresNoInitialization)
                addCode(checkNoInitialization(backingPropertyIndex, backingPropertyName))

            addCode(initialize(backingPropertyIndex))
            addCode("this.%N = %N\n", "\$\$$backingPropertyName\$\$", name)
        }
        .build()
        .run(this::addFunction)

}

/**
 * Generates context class property setter with syntax `name { /* subDSL */ }`.
 */
internal fun FileSpec.Builder.generateDslFunctionSetter(
    name: String,
    backingPropertyName: String,
    backingPropertyType: KSType,
    backingPropertyIndex: Int,
    exitFunction: KSFunctionDeclaration,
    requiresNoInitialization: Boolean,
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    data: Data,
) {
    val innerContextClassName =
        processFunction(
            exitFunction,
            data,
            generationParameters,
            dslMarker,
            typeVariables,
            backingPropertyType.arguments
        )

    val innerContextTypeName =
        backingPropertyType.arguments
            .map { it.toTypeNameFix(typeParameterResolver) }
            .let {
                if (it.isEmpty()) innerContextClassName
                else innerContextClassName.parameterizedBy(it)
            }
            .let { it.copy(annotations = it.annotations + listOf(dslMarker)) }

    val usedTypeVariables = innerContextTypeName.usedTypeVariables()

    addImport(innerContextClassName.packageName, CREATE)

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters, suppressWarning = false)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.startProjectUnusedParameters(usedTypeVariables))
        .addParameter("\$builder\$",
            LambdaTypeName.get(
                receiver = innerContextTypeName,
                returnType = UNIT
            )
        )
        .apply {
            if (requiresNoInitialization)
                addCode(checkNoInitialization(backingPropertyIndex, backingPropertyName))

            addCode(initialize(backingPropertyIndex))
            addCode(
                "this.%N = %T()\n.apply {`\$builder\$`() }\n.%N()\n",
                "\$\$$backingPropertyName\$\$",
                innerContextTypeName.copy(annotations = emptyList()),
                CREATE
            )
        }
        .build()
        .run(this::addFunction)

}

/**
 * Generates context class property setter with syntax `name(/* args */)`.
 */
internal fun FileSpec.Builder.generateSubFunctionSetter(
    name: String,
    backingPropertyName: String,
    backingPropertyType: KSType,
    backingPropertyIndex: Int,
    exitFunction: KSFunctionDeclaration,
    requiresNoInitialization: Boolean,
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
        backingPropertyType,
        exitFunction.typeParameters,
        exitFunction.parameters.map { it.type.resolve() }, // TODO: should we modify type of vararg parameters?
        exitFunction.returnType!!.resolve(),
        data
    )

    val resolver = exitFunction.typeParameters.toTypeParameterResolver(typeParameterResolver)
    val paramsWithType = exitFunction.parameters.map { it to it.type.resolve() }

    val usedTypeVariables = backingPropertyType.toTypeNameFix(resolver).usedTypeVariables()

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
            if (requiresNoInitialization)
                addCode(checkNoInitialization(backingPropertyIndex, backingPropertyName))

            val functionName =
                if (exitFunction.isConstructor())
                    (exitFunction.parentDeclaration as KSClassDeclaration).qualifiedName
                else exitFunction.qualifiedName

            addCode(initialize(backingPropertyIndex))
            addCode(
                "this.%N = ${functionName!!.asString()}(${
                    exitFunction.parameters.joinToString(prefix = "\n",
                        separator = ",\n",
                        postfix = "\n") { if (it.isVararg) "*%N" else "%N" }
                })",
                "\$\$$backingPropertyName\$\$",
                *parameters.map { it.name }.toTypedArray()
            )
        }
        .build()
        .run(this::addFunction)

}
