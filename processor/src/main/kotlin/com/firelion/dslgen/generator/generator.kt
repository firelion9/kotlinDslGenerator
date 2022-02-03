/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.annotations.GenerateDsl
import com.firelion.dslgen.generator.generation.*
import com.firelion.dslgen.generator.generation.generateCollectionAdder
import com.firelion.dslgen.generator.generation.generateDslCollectionAdder
import com.firelion.dslgen.generator.generation.generateDslFunctionSetter
import com.firelion.dslgen.generator.generation.generateFunctionSetter
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.GeneratedDslInfo
import com.firelion.dslgen.generator.util.GeneratedDslParameterInfo
import com.firelion.dslgen.generator.util.getClassDeclaration
import com.firelion.dslgen.readGenerationParametersAnnotation
import com.firelion.dslgen.util.*
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.writeTo

internal fun processFunction(
    function: KSFunctionDeclaration,
    data: Data,
    parentGenerationParameters: GenerationParameters? = null,
    parentDslMarker: AnnotationSpec? = null,
    newTypeParameters: List<TypeVariableName>? = null,
    returnTypeArguments: List<KSTypeArgument>? = null,
): ClassName = try {
    processFunction0(
        function,
        data,
        parentGenerationParameters,
        parentDslMarker,
        newTypeParameters,
        returnTypeArguments
    )
} catch (e: Exception) {
    processingException(function.location, e)
}

private fun processFunction0(
    function: KSFunctionDeclaration,
    data: Data,
    parentGenerationParameters: GenerationParameters?,
    parentDslMarker: AnnotationSpec?,
    newTypeParameters: List<TypeVariableName>?,
    returnTypeArguments: List<KSTypeArgument>?,
): ClassName {
    val identifier = function.getUniqueIdentifier()
    val pkg = function.packageName.asString()
        .let { if (it.startsWith("kotlin.") || it == "kotlin") "generated.$it" else it }

    val functionReturnType: KSType by lazy { function.returnType!!.resolve() }

    if (identifier in data.generatedDsls) {
        val generatedDsl = data.generatedDsls.getValue(identifier)

        generateSpecification(
            generatedDsl,
            function.containingFile,
            newTypeParameters,
            returnTypeArguments,
            parentGenerationParameters,
            parentDslMarker,
            data,
            function
        )

        return ClassName(pkg, generatedDsl.contextClassName)
    } else {
        val dec = data.resolver.getClassDeclarationByName("$pkg.\$Context\$$identifier")

        if (dec != null) {
            data.logger.logging("find DSL $identifier", function)

            val generatedDsl = GeneratedDslInfo(
                dec.packageName.asString(),
                dec.simpleName.asString(),
                dec.typeParameters,
                dec.declarations
                    .filterIsInstance<KSPropertyDeclaration>()
                    .filterNot {
                        it.simpleName.asString().startsWith(INITIALIZATION_INFO_PREFIX)
                    }
                    .mapIndexed { idx, it ->
                        it.simpleName.asString().removeSurrounding("\$\$") to
                                GeneratedDslParameterInfo(
                                    it.simpleName.asString(),
                                    idx,
                                    it.type.resolve()
                                )
                    }
                    .toMap(),
                functionReturnType
            )

            data.generatedDsls[identifier] = generatedDsl

            generateSpecification(
                generatedDsl,
                function.containingFile,
                newTypeParameters,
                returnTypeArguments,
                parentGenerationParameters,
                parentDslMarker,
                data,
                function
            )

            return dec.toClassName()
        }
    }

    val generationParametersAnnotation = function.annotations.find {
        it.shortName.getShortName() == GenerateDsl::class.simpleName &&
                data.generateDslType.isAssignableFrom(it.annotationType.resolve())
    }

    val generationParameters =
        generationParametersAnnotation?.let {
            readGenerationParametersAnnotation(
                it,
                function,
                identifier,
                data.logger
            )
        }
            ?: parentGenerationParameters!!.copy(
                functionName = null,
                contextClassName = "\$Context\$$identifier"
            )


    val typeParameters = function.typeParameters

    val typeParameterResolver = typeParameters.toTypeParameterResolver(sourceTypeHint = (function.qualifiedName
        ?: function.simpleName).asString())
    val typeVariables = typeParameters.map { ksTypeParameter ->
        TypeVariableName(
            name = ksTypeParameter.name.getShortName(),
            bounds = ksTypeParameter.bounds.map { it.resolve().toTypeNameFix(typeParameterResolver) }.toList(),
            variance = null, // Couldn't be OUT
        )
    }

    val functionParameters = function.parameters.map { it to it.type.resolve() }

    val fileBuilder = FileSpec.builder(
        pkg,
        "\$Dsl\$$identifier"
    )

    val generatedDsl = GeneratedDslInfo(
        pkg,
        generationParameters.contextClassName,
        function.typeParameters,
        function.parameters
            .mapIndexed { idx, it ->
                it.name!!.asString() to
                        GeneratedDslParameterInfo(
                            "\$\$" + it.name!!.asString() + "\$\$",
                            idx,
                            it.type.resolve()
                        )
            }
            .toMap(),
        functionReturnType
    )
    data.generatedDsls[identifier] = generatedDsl

    generateSpecification(
        generatedDsl,
        function.containingFile,
        newTypeParameters,
        returnTypeArguments,
        parentGenerationParameters,
        parentDslMarker,
        data,
        function
    )

    data.logger.logging("generating dsl $identifier", function)

    val contextClassName = fileBuilder.generateDsl(
        generationParameters,
        typeVariables,
        functionParameters,
        functionReturnType,
        function.qualifiedName ?: object : KSName {
            val qualifierField = function.parentDeclaration!!.qualifiedName!!.asString()
            val nameField = function.simpleName.asString()

            override fun asString(): String = "$qualifierField.$nameField"

            override fun getQualifier(): String = qualifierField

            override fun getShortName(): String = nameField
        },
        typeParameterResolver,
        data
    )

    with(fileBuilder.build()) {
        writeTo(data.codeGenerator, true, function.containingFile?.let { listOf(it) } ?: emptyList())
    }

    return contextClassName
}

private fun FileSpec.Builder.generateDsl(
    generationParameters: GenerationParameters,
    typeVariables: List<TypeVariableName>,
    functionParameters: List<Pair<KSValueParameter, KSType>>,
    returnType: KSType,
    exitFunction: KSName,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
): ClassName {
    val dslMarker = AnnotationSpec.Companion.builder(generationParameters.markerClass.toClassName()).build()

    val contextClassSpec = generateContextClass(
        generationParameters,
        typeVariables,
        functionParameters,
        typeParameterResolver,
        data
    )

    val possiblyReifiedTypeVariables =
        if (generationParameters.makeInline)
            typeVariables.map { it.copy(reified = true) }
        else typeVariables

    val contextClassName =
        ClassName(packageName, contextClassSpec.name!!)

    val contextTypeName =
        (if (possiblyReifiedTypeVariables.isEmpty()) contextClassName
        else contextClassName.parameterizedBy(possiblyReifiedTypeVariables.map { it }))
            .let {
                it.copy(
                    annotations = it.annotations + dslMarker
                )
            }

    functionParameters.forEachIndexed { index, prop ->
        val isArrayType = prop.second.isArrayType(data)
        if (!isArrayType) {
            generateFunctionGetter(
                prop.first.name!!.asString(),
                prop.first.name!!.asString(),
                prop.second,
                index,
                generationParameters,
                possiblyReifiedTypeVariables,
                contextTypeName,
                typeParameterResolver,
                dslMarker,
            )

            generateFunctionSetter(
                prop.first.name!!.asString(),
                prop.first.name!!.asString(),
                prop.second,
                index,
                requiresNoInitialization = true,
                generationParameters,
                possiblyReifiedTypeVariables,
                contextTypeName,
                typeParameterResolver,
                dslMarker,
            )
        }

        if (isArrayType) {
            val elementType = prop.second.arguments[0].type!!.resolve()
            val elementClass = elementType.getClassDeclaration()

            generateCollectionAdder(
                "element",
                elementType,
                prop.first.name!!.asString(),
                index,
                possiblyReifiedTypeVariables,
                contextTypeName,
                typeParameterResolver,
                dslMarker,
                data
            )

            elementClass?.findConstructionFunction(data.resolver, data)?.let { constructor ->
                generateDslCollectionAdder(
                    "element",
                    elementType,
                    prop.first.name!!.asString(),
                    index,
                    constructor,
                    generationParameters,
                    possiblyReifiedTypeVariables,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data
                )
            }
        } else if (!prop.second.isPrimitive()) {
            val cls = prop.second.getClassDeclaration()
            cls?.findConstructionFunction(data.resolver, data)?.let { constructor ->
                generateDslFunctionSetter(
                    prop.first.name!!.asString(),
                    prop.first.name!!.asString(),
                    prop.second,
                    index,
                    constructor,
                    true,
                    generationParameters,
                    possiblyReifiedTypeVariables,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data
                )
            }
        }
    }

    generateCreateFunction(
        generationParameters,
        possiblyReifiedTypeVariables,
        functionParameters,
        contextTypeName,
        returnType,
        exitFunction,
        typeParameterResolver,
        data
    )

    if (generationParameters.functionName != null)
        generateEntryFunction(
            generationParameters,
            possiblyReifiedTypeVariables,
            contextTypeName,
            returnType,
            typeParameterResolver,
            dslMarker,
            data
        )

    return contextClassName
}

