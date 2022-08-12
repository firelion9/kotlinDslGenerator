/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.processFunction
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.filterUsed
import com.firelion.dslgen.generator.util.makeInlineIfRequested
import com.firelion.dslgen.generator.util.usedTypeVariables
import com.firelion.dslgen.generator.util.resolveEndTypeArguments
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

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
    data.logger.logging("generating DSL collection adder $name")

    val endTypeArgs = elementType.resolveEndTypeArguments(data)

    val elementContextClassName = processFunction(
        itemFunction,
        data,
        generationParameters,
        dslMarker,
        typeVariables,
        typeParameters,
        endTypeArgs
    )

    val elementContextTypeName =
        endTypeArgs
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
        .addParameter(
            "\$builder\$",
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