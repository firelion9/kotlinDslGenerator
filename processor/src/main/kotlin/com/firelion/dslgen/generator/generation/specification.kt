/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.GeneratedDslInfo
import com.firelion.dslgen.generator.util.findConstructionFunction
import com.firelion.dslgen.generator.util.getClassDeclaration
import com.firelion.dslgen.generator.util.getSpecificationUniqueIdentifier
import com.firelion.dslgen.generator.util.isArrayType
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Generates extra subDSLs for specified type arguments.
 *
 * For example, if some function has argument of type Pair<List<A>, B>,
 * an extra subDSL for `Pair<List<A>, B>.first(builder: Context$List<A>.() -> Unit)`
 * would be generated (actual names would be different)
 *
 * @param [newTypeParameters] are new type parameters
 * @param [returnTypeArguments] are type arguments for function's return type which may use [newTypeParameters]
 *
 */
internal fun generateSpecification(
    generatedDslInfo: GeneratedDslInfo,
    file: KSFile?,
    newTypeParameters: List<TypeVariableName>?,
    returnTypeArguments: List<KSTypeArgument>?,
    generationParameters: GenerationParameters?,
    dslMarker: AnnotationSpec?,
    data: Data,
    nodeForLogging: KSNode,
) {
    if (
        newTypeParameters == null
        || returnTypeArguments == null
        || generationParameters == null
        || dslMarker == null
        || returnTypeArguments.isEmpty()
    ) return

    require(returnTypeArguments.size == generatedDslInfo.returnType.arguments.size)

    val specUid = getSpecificationUniqueIdentifier(
        newTypeParameters,
        returnTypeArguments,
        generatedDslInfo.contextClassName
    )

    if (specUid in data.generatedSpecifications) return
    data.generatedSpecifications.add(specUid)

    data.logger.logging("generating specification $specUid", nodeForLogging)

    val specFileBuilder =
        FileSpec.builder(generatedDslInfo.contextClassPackage, "\$Dsl\$Specification\$$specUid")

    val typeParameterResolver = object : TypeParameterResolver {
        override val parametersMap: Map<String, TypeVariableName> = newTypeParameters.associateBy { it.name }

        override fun get(index: String): TypeVariableName = parametersMap.getValue(index)
    }

    val contextTypeName =
        ClassName(generatedDslInfo.contextClassPackage, generatedDslInfo.contextClassName)
            .parameterizedBy(returnTypeArguments.map { it.toTypeNameFix(typeParameterResolver) })
            .copy(annotations = listOf(dslMarker))

    generatedDslInfo.parameters.values.forEach { param ->
        val type = param.type
        val dec = type.declaration

        val args =
            generatedDslInfo.typeParameters.asSequence().withIndex().associate { (idx, it) -> it.name to idx }

        if (dec is KSTypeParameter) {
            val arg =
                returnTypeArguments[args[dec.name]
                    ?: error("no such function type argument: ${dec.name}")]

            val argType = arg.type!!.resolve()

            if (argType is KSTypeParameter) return@forEach

            val isArrayType = argType.isArrayType(data)

            if (isArrayType) {
                val elementType = argType.arguments[0].type!!.resolve()
                val elementClass = elementType.getClassDeclaration()

                specFileBuilder.generateCollectionAdder(
                    "element",
                    elementType,
                    param.backingPropertyName,
                    param.index,
                    newTypeParameters,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data,
                    generationParameters
                )

                elementClass?.findConstructionFunction(data)?.let { constructor ->
                    specFileBuilder.generateDslCollectionAdder(
                        "element",
                        elementType,
                        param.backingPropertyName,
                        param.index,
                        constructor,
                        generationParameters,
                        newTypeParameters,
                        contextTypeName,
                        typeParameterResolver,
                        dslMarker,
                        data
                    )
                }
            } else if (!argType.isPrimitive()) {
                val cls = argType.getClassDeclaration()
                cls?.findConstructionFunction(data)?.let { constructor ->
                    specFileBuilder.generateDslFunctionSetter(
                        param.backingPropertyName,
                        param.backingPropertyName,
                        argType,
                        param.index,
                        constructor,
                        true,
                        generationParameters,
                        newTypeParameters,
                        contextTypeName,
                        typeParameterResolver,
                        dslMarker,
                        data
                    )
                }
            }
        }
    }

    with(specFileBuilder.build()) {
        if (this.members.isNotEmpty())
            writeTo(data.codeGenerator, true, file?.let { listOf(it) } ?: emptyList())
    }
}