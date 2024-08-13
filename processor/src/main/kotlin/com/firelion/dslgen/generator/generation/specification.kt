/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.processFunction
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.logging
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Generates extra subDSLs for specified type arguments.
 *
 * For example, if some function has argument of type Pair<List<A>, B>,
 * an extra subDSL for `Context$Pair<List<A>, B>.first(builder: Context$List<A>.() -> Unit)`
 * would be generated (actual names would be different).
 *
 * @param [newTypeParameters] are new type parameters
 * @param [returnTypeArguments] are type arguments for function's return type which may use [newTypeParameters]
 *
 */
internal fun generateSpecification(
    generatedDslInfo: GeneratedDslInfo,
    file: KSFile?,
    newTypeVariables: List<TypeVariableName>?,
    newTypeParameters: List<KSTypeParameter>?,
    returnTypeArguments: List<KSTypeArgument>?,
    generationParameters: GenerationParameters?,
    data: Data,
    nodeForLogging: KSNode,
) {
    if (
        newTypeVariables == null
        || newTypeParameters == null
        || returnTypeArguments == null
        || generationParameters == null
        || returnTypeArguments.isEmpty()
    ) return

    data.logger.logging {
        "generating specification for ${generatedDslInfo.contextClassSimpleName} with returnTypeArguments=$returnTypeArguments, generatedDslInfo.returnType.arguments=${generatedDslInfo.returnType.arguments}"
    }

    require(returnTypeArguments.size == generatedDslInfo.returnType.arguments.size)

    val (specIdentifier, specUid) = getSpecificationUniqueIdentifier(
        newTypeVariables,
        returnTypeArguments,
        generatedDslInfo.contextClassSimpleName
    )

    if (specUid in data.generatedSpecifications) return
    data.generatedSpecifications.add(specUid)

    data.logger.logging(nodeForLogging) { "generating specification ```$specIdentifier``` aka $specUid" }

    val fileSpecBuilder =
        FileSpec.builder(
            generatedDslInfo.contextClassPackage,
            data.namingStrategy.specificationFileName(specUid, generatedDslInfo.contextClassSimpleName)
        )

    val typeParameterResolver = object : TypeParameterResolver {
        override val parametersMap: Map<String, TypeVariableName> = newTypeVariables.associateBy { it.name }

        override fun get(index: String): TypeVariableName = parametersMap.getValue(index)
    }

    val contextTypeName =
        generatedDslInfo.contextClassName()
            .parameterizedBy(returnTypeArguments.map { it.toTypeNameFix(typeParameterResolver) })
            .copy(annotations = listOf(generationParameters.dslMarker))

    val (typeMapping, extraParameters) = inferTypeParameters(
        newTypeParameters,
        generatedDslInfo.returnType.replace(returnTypeArguments),
        generatedDslInfo.typeParameters,
        listOf(),
        generatedDslInfo.returnType,
        data,
    )

    require(extraParameters.isEmpty()) { "some old parameters can't be exposed through new ones" }

    generatedDslInfo.parameters.values.forEach { param ->
        fileSpecBuilder.generateSpecificationFor(
            newTypeVariables,
            newTypeParameters,
            generatedDslInfo.contextClassSimpleName,
            typeMapping,
            contextTypeName,
            param,
            typeParameterResolver,
            nodeForLogging,
            generationParameters,
            data
        )
    }

    with(fileSpecBuilder.build()) {
        if (this.members.isNotEmpty())
            writeTo(data.codeGenerator, true, file?.let { listOf(it) } ?: emptyList())
    }
}

internal fun FileSpec.Builder.generateSpecificationFor(
    typeVariables: List<TypeVariableName>,
    typeParameters: List<KSTypeParameter>,
    baseDslContextClassName: String,
    typeMapping: Map<KSTypeParameter, KSType>,
    contextTypeName: TypeName,
    dslParameter: GeneratedDslParameterInfo,
    typeParameterResolver: TypeParameterResolver,
    nodeForLogging: KSNode,
    generationParameters: GenerationParameters,
    data: Data,
) {
    val type = dslParameter.type
    val dec = type.declaration

    val mappedType = type.replaceTypeParameters(typeMapping, data)

    data.logger.logging(nodeForLogging) {
        "generating specification for $baseDslContextClassName#${dslParameter.backingPropertyName}"
    }

    if (dec is KSTypeParameter || type.arrayElementTypeOrNull(data)?.declaration is KSTypeParameter) run whenTypeParameter@{
        if (mappedType is KSTypeParameter) {
            data.logger.logging(nodeForLogging) {
                "$baseDslContextClassName#${dslParameter.backingPropertyName} type  is not a TypeParameter, skipping"
            }

            return@whenTypeParameter
        }

        val isArrayType = mappedType.isArrayType(data)

        data.logger.logging(nodeForLogging) {
            "$baseDslContextClassName#${dslParameter.backingPropertyName} type is $mappedType, isArrayType = $isArrayType"
        }

        if (isArrayType) {
            val elementType = mappedType.arguments[0].type!!.resolve()
            val elementClass = elementType.getClassDeclaration()

            elementClass?.findConstructionFunction(data)?.let { constructor ->

                generateDslCollectionAdder(
                    data.namingStrategy.elementAdderName(dslParameter.name),
                    elementType,
                    dslParameter.name,
                    dslParameter.index,
                    constructor,
                    generationParameters,
                    typeVariables,
                    typeParameters,
                    contextTypeName,
                    typeParameterResolver,
                    data
                )

                generateSubFunctionAdder(
                    data.namingStrategy.elementAdderName(dslParameter.name),
                    elementType,
                    dslParameter.name,
                    dslParameter.index,
                    constructor,
                    generationParameters,
                    listOf(),
                    typeVariables,
                    contextTypeName,
                    typeParameterResolver,
                    data
                )
            }
        } else if (!mappedType.isPrimitive()) {

            val cls = mappedType.getClassDeclaration()

            data.logger.logging(nodeForLogging) {
                "$mappedType class declaration is $cls"
            }

            cls?.findConstructionFunction(data)?.let { constructor ->

                generateDslFunctionSetter(
                    dslParameter.name,
                    dslParameter.name,
                    mappedType,
                    dslParameter.index,
                    constructor,
                    true,
                    generationParameters,
                    typeVariables,
                    typeParameters,
                    contextTypeName,
                    typeParameterResolver,
                    data
                )

                generateSubFunctionSetter(
                    dslParameter.name,
                    dslParameter.name,
                    mappedType,
                    dslParameter.index,
                    constructor,
                    true,
                    generationParameters,
                    listOf(),
                    typeVariables,
                    contextTypeName,
                    typeParameterResolver,
                    data
                )
            }
        }
    }
    else
        data.logger.logging(nodeForLogging) {
            "$baseDslContextClassName#${dslParameter.backingPropertyName} type wasn't a type parameter, no need to generate specification, skipping"
        }

    val parameterClassDec = mappedType.getClassDeclaration() ?: run {
        data.logger.logging(nodeForLogging) { "missing class declaration for $mappedType, ignoring it" }
        return
    }
    val constructionFunction = parameterClassDec.findConstructionFunction(data) ?: run {
        data.logger.logging(nodeForLogging) { "missing construction function for $mappedType, ignoring it" }
        return
    }

    processFunction(
        constructionFunction,
        data,
        generationParameters,
        typeVariables,
        typeParameters,
        type.resolveEndTypeArguments(data).map {
            data.resolver.getTypeArgument(
                data.resolver.createKSTypeReferenceFromKSType(
                    it.type!!.resolve().replaceTypeParameters(typeMapping, data)
                ),
                it.variance
            )
        }
    )
}