/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.processFunction
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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver

/**
 * Generates context class array property adder with syntax `name(elementToAdd)`.
 */
internal fun FileSpec.Builder.generateCollectionAdder(
    name: String,
    elementType: KSType,
    backingPropertyName: String,
    backingPropertyIndex: Int,
    typeVariables: List<TypeVariableName>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    @Suppress("UNUSED_PARAMETER") data: Data, // may be used in future for some reasons
    generationParameters: GenerationParameters,
) {
    val elementTypeName = elementType.toTypeNameFix(typeParameterResolver)
    val usedTypeVariables = elementTypeName.usedTypeVariables()

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.startProjectUnusedParameters(usedTypeVariables))
        .addParameter("element", elementTypeName)
        .apply {
            addCode(initialize(backingPropertyIndex))
            addCode(
                "this.%N.add(element)\n",
                "\$\$$backingPropertyName\$\$",
            )
        }
        .build()
        .run(this::addFunction)

}

/**
 * Generates context class array property adder with syntax `name { /* element to add subDSL */ }`.
 */
internal fun FileSpec.Builder.generateDslCollectionAdder(
    name: String,
    elementType: KSType,
    backingPropertyName: String,
    backingPropertyIndex: Int,
    itemFunction: KSFunctionDeclaration,
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    typeParameters: List<KSTypeParameter>,
    contextClassName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
    data: Data,
) {
    val elementContextClassName = processFunction(
        itemFunction,
        data,
        generationParameters,
        dslMarker,
        typeVariables,
        typeParameters,
        elementType.arguments
    )

    val elementContextTypeName =
        elementType.arguments
            .map { it.toTypeNameFix(typeParameterResolver) }
            .let {
                if (it.isEmpty()) elementContextClassName
                else elementContextClassName.parameterizedBy(it)
            }
            .let { it.copy(annotations = it.annotations + listOf(dslMarker)) }

    val usedTypeVariables = elementContextTypeName.usedTypeVariables()

    addImport(elementContextClassName.packageName, CREATE)

    FunSpec.builder(name)
        .addAnnotation(dslMarker)
        .makeInlineIfRequested(generationParameters, suppressWarning = false)
        .addTypeVariables(typeVariables.filterUsed(usedTypeVariables))
        .receiver(contextClassName.startProjectUnusedParameters(usedTypeVariables))
        .addParameter("\$builder\$",
            LambdaTypeName.get(
                receiver = elementContextTypeName,
                returnType = UNIT
            )
        )
        .apply {
            addCode(initialize(backingPropertyIndex))
            addCode(
                "this.%N.add(%T().apply {`\$builder\$`() }.%N())\n",
                "\$\$$backingPropertyName\$\$",
                elementContextTypeName.copy(annotations = emptyList()),
                CREATE
            )
        }
        .build()
        .run(this::addFunction)

}


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
                    exitFunction.parameters.joinToString(prefix = "\n",
                        separator = ",\n",
                        postfix = "\n") { if (it.isVararg) "*%N" else "%N" }
                }))",
                "\$\$$backingPropertyName\$\$",
                *parameters.map { it.name }.toTypedArray()
            )
        }
        .build()
        .run(this::addFunction)

}