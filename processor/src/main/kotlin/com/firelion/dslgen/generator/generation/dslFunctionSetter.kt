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
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver

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
    typeParameters: List<KSTypeParameter>,
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
            typeParameters,
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