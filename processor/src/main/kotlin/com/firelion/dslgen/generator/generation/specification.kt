/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.processFunction
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.GeneratedDslInfo
import com.firelion.dslgen.generator.util.findConstructionFunction
import com.firelion.dslgen.generator.util.getClassDeclaration
import com.firelion.dslgen.generator.util.getSpecificationUniqueIdentifier
import com.firelion.dslgen.generator.util.isArrayType
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
    newTypeVariables: List<TypeVariableName>?,
    newTypeParameters: List<KSTypeParameter>?,
    returnTypeArguments: List<KSTypeArgument>?,
    generationParameters: GenerationParameters?,
    dslMarker: AnnotationSpec?,
    data: Data,
    nodeForLogging: KSNode,
) {
    if (
        newTypeVariables == null
        || newTypeParameters == null
        || returnTypeArguments == null
        || generationParameters == null
        || dslMarker == null
        || returnTypeArguments.isEmpty()
    ) return

    require(returnTypeArguments.size == generatedDslInfo.returnType.arguments.size)

    val (specIdentifier, specUid) = getSpecificationUniqueIdentifier(
        newTypeVariables,
        returnTypeArguments,
        generatedDslInfo.contextClassName
    )

    if (specUid in data.generatedSpecifications) return
    data.generatedSpecifications.add(specUid)

    data.logger.logging("generating specification ```$specIdentifier``` aka $specUid", nodeForLogging)

    val fileSpecBuilder =
        FileSpec.builder(generatedDslInfo.contextClassPackage, "\$Dsl\$Specification\$$specUid")

    val typeParameterResolver = object : TypeParameterResolver {
        override val parametersMap: Map<String, TypeVariableName> = newTypeVariables.associateBy { it.name }

        override fun get(index: String): TypeVariableName = parametersMap.getValue(index)
    }

    val contextTypeName =
        ClassName(generatedDslInfo.contextClassPackage, generatedDslInfo.contextClassName)
            .parameterizedBy(returnTypeArguments.map { it.toTypeNameFix(typeParameterResolver) })
            .copy(annotations = listOf(dslMarker))


    val typeMapping =
        generatedDslInfo.typeParameters.asSequence().zip(returnTypeArguments.asSequence().map { it.type!!.resolve() })
            .toMap()

    generatedDslInfo.parameters.values.forEach { param ->
        fileSpecBuilder.generateSpecificationFor(
            dslMarker,
            newTypeVariables,
            newTypeParameters,
            generatedDslInfo,
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
    dslMarker: AnnotationSpec,
    typeVariables: List<TypeVariableName>,
    typeParameters: List<KSTypeParameter>,
    baseDsl: GeneratedDslInfo,
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

    data.logger.logging(
        "generating specification for ${baseDsl.contextClassName}#${dslParameter.backingPropertyName}",
        nodeForLogging
    )

    if (dec is KSTypeParameter || type.arrayElementTypeOrNull(data)?.declaration is KSTypeParameter) run whenTypeParameter@{
        if (mappedType is KSTypeParameter) {
            data.logger.logging(
                "${baseDsl.contextClassName}#${dslParameter.backingPropertyName} type  is not a TypeParameter, skipping",
                nodeForLogging
            )

            return@whenTypeParameter
        }

        val isArrayType = mappedType.isArrayType(data)

        data.logger.logging(
            "${baseDsl.contextClassName}#${dslParameter.backingPropertyName} type is $mappedType, isArrayType = $isArrayType",
            nodeForLogging
        )

        if (isArrayType) {
            val elementType = mappedType.arguments[0].type!!.resolve()
            val elementClass = elementType.getClassDeclaration()

            elementClass?.findConstructionFunction(data)?.let { constructor ->

                generateDslCollectionAdder(
                    "element",
                    elementType,
                    dslParameter.backingPropertyName,
                    dslParameter.index,
                    constructor,
                    generationParameters,
                    typeVariables,
                    typeParameters,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data
                )

                generateSubFunctionAdder(
                    "element",
                    elementType,
                    dslParameter.backingPropertyName,
                    dslParameter.index,
                    constructor,
                    generationParameters,
                    listOf(),
                    typeVariables,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data
                )
            }
        } else if (!mappedType.isPrimitive()) {

            val cls = mappedType.getClassDeclaration()

            data.logger.logging(
                "$mappedType class declaration is $cls",
                nodeForLogging
            )

            cls?.findConstructionFunction(data)?.let { constructor ->

                generateDslFunctionSetter(
                    dslParameter.backingPropertyName,
                    dslParameter.backingPropertyName,
                    mappedType,
                    dslParameter.index,
                    constructor,
                    true,
                    generationParameters,
                    typeVariables,
                    typeParameters,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data
                )

                generateSubFunctionSetter(
                    dslParameter.backingPropertyName,
                    dslParameter.backingPropertyName,
                    mappedType,
                    dslParameter.index,
                    constructor,
                    true,
                    generationParameters,
                    listOf(),
                    typeVariables,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data
                )
            }
        }
    }
    else
        data.logger.logging(
            "${baseDsl.contextClassName}#${dslParameter.backingPropertyName} type wasn't a type parameter, no need to generate specification, skipping",
            nodeForLogging
        )

    val parameterClassDec = mappedType.getClassDeclaration() ?: run {
        data.logger.logging("missing class declaration for $mappedType, ignoring it", nodeForLogging)
        return
    }
    val constructionFunction = parameterClassDec.findConstructionFunction(data) ?: run {
        data.logger.logging("missing construction function for $mappedType, ignoring it", nodeForLogging)
        return
    }

    processFunction(
        constructionFunction,
        data,
        generationParameters,
        dslMarker,
        typeVariables,
        typeParameters,
        type.arguments.map {
            data.resolver.getTypeArgument(
                data.resolver.createKSTypeReferenceFromKSType(
                    it.type!!.resolve().replaceTypeParameters(typeMapping, data)
                ),
                it.variance
            )
        }
    )
}