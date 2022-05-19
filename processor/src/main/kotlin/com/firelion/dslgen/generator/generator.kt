/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.annotations.PropertyAccessor
import com.firelion.dslgen.annotations.UseAlternativeConstruction
import com.firelion.dslgen.annotations.UseDefaultConstructions
import com.firelion.dslgen.generator.generation.*
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.readGenerationParametersAnnotation
import com.firelion.dslgen.util.processingException
import com.firelion.dslgen.util.toTypeNameFix
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KProperty

/**
 * Wrapper around [processFunction0].
 * Catches all exceptions and adds location to them.
 */
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

/**
 * Generates DSL for specified [function]
 * and recursively calls itself for all parameters' construction functions.
 */
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

    val generationParametersAnnotation =
        function.annotations.findMatchingTypeOrNull(data.usefulTypes.ksGeneratedDsl)

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
                contextClassName = "\$Context\$$identifier",
                monoParameter = parentGenerationParameters.monoParameter && (function.parameters.size == 1)
            )


    val typeParameters = function.typeParameters

    val typeParameterResolver = typeParameters
        .toTypeParameterResolver(sourceTypeHint = (function.qualifiedName ?: function.simpleName).asString())

    val typeVariables = typeParameters.toTypeVariableNames(typeParameterResolver)

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
                            it.name!!.asString(),
                            idx,
                            it.type.resolve()
                        )
            }
            .toMap(),
        functionReturnType
    )
    data.generatedDsls[identifier] = generatedDsl

    if (!generationParameters.monoParameter)
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
        typeParameters,
        typeVariables,
        functionParameters,
        functionReturnType,
        function.qualifiedName ?: data.resolver.getKSNameFromString(
            "${function.parentDeclaration!!.qualifiedName!!.asString()}.${function.simpleName.asString()}"
        ),
        typeParameterResolver,
        data
    )

    with(fileBuilder.build()) {
        writeTo(data.codeGenerator, true, function.containingFile?.let { listOf(it) } ?: emptyList())
    }

    return contextClassName
}

/**
 * Generates DSL using data prepared by [processFunction0].
 */
private fun FileSpec.Builder.generateDsl(
    generationParameters: GenerationParameters,
    typeParameters: List<KSTypeParameter>,
    typeVariables: List<TypeVariableName>,
    functionParameters: List<Pair<KSValueParameter, KSType>>,
    returnType: KSType,
    exitFunction: KSName,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
): ClassName {
    val dslMarker = AnnotationSpec.builder(generationParameters.markerClass.toClassName()).build()

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
        ClassName(packageName, generationParameters.contextClassName)

    val contextTypeName =
        (if (possiblyReifiedTypeVariables.isEmpty()) contextClassName
        else contextClassName.parameterizedBy(possiblyReifiedTypeVariables.map { it }))
            .let {
                it.copy(
                    annotations = it.annotations + dslMarker
                )
            }

    functionParameters.forEachIndexed { index, prop ->
        generatePropertySettersAndGetters(
            prop,
            data,
            index,
            generationParameters,
            typeParameters,
            possiblyReifiedTypeVariables,
            contextTypeName,
            typeParameterResolver,
            dslMarker
        )
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

private fun FileSpec.Builder.generatePropertySettersAndGetters(
    property: Pair<KSValueParameter, KSType>,
    data: Data,
    propertyIndex: Int,
    generationParameters: GenerationParameters,
    typeParameters: List<KSTypeParameter>,
    possiblyReifiedTypeVariables: List<TypeVariableName>,
    contextTypeName: TypeName,
    typeParameterResolver: TypeParameterResolver,
    dslMarker: AnnotationSpec,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> Map<String?, KSValueArgument>.getSpecial(key: KProperty<*>) = getValue(key.name).value as T

    @Suppress("UNCHECKED_CAST")
    operator fun <T> Map<String?, KSValueArgument>.get(key: KProperty<T>) = getValue(key.name).value as T

    val defaultsAnnotationArgMap =
        property.first.annotations
            .findMatchingTypeOrNull(data.usefulTypes.ksUseDefaultConstructions)
            ?.let { annotation ->
                annotation.arguments.associateBy { it.name?.asString() }
            }

    val isArrayType = property.second.isArrayType(data)

    val elementType by lazy { property.second.arguments[0].type!!.resolve() }
    val elementClass by lazy { elementType.getClassDeclaration() }

    if (
        defaultsAnnotationArgMap
            ?.get(UseDefaultConstructions::useFunctionLikeGetter)
            .let { useFunctionLikeGetter ->
                useFunctionLikeGetter == true || (useFunctionLikeGetter != false && !isArrayType)
            }
    ) {
        generateFunctionGetter(
            property.first.name!!.asString(),
            property.first.name!!.asString(),
            property.second,
            propertyIndex,
            generationParameters,
            possiblyReifiedTypeVariables,
            contextTypeName,
            typeParameterResolver,
            dslMarker,
        )
    }

    if (
        defaultsAnnotationArgMap
            ?.get(UseDefaultConstructions::useFunctionLikeSetter)
            .let { useFunctionLikeSetter ->
                useFunctionLikeSetter == true || (useFunctionLikeSetter != false && !isArrayType)
            }
    ) {
        generateFunctionSetter(
            property.first.name!!.asString(),
            property.first.name!!.asString(),
            property.second,
            propertyIndex,
            requiresNoInitialization = true,
            generationParameters,
            possiblyReifiedTypeVariables,
            contextTypeName,
            typeParameterResolver,
            dslMarker,
        )
    }

    defaultsAnnotationArgMap
        ?.getSpecial<KSType>(UseDefaultConstructions::usePropertyLikeAccessor)
        ?.let {
            PropertyAccessor.valueOf(it.declaration.simpleName.getShortName())
        }
        ?.takeUnless { it == PropertyAccessor.NO }
        ?.let { propertyAccessor ->
            generatePropertyAccessors(
                propertyAccessor,
                property.first.name!!.asString(),
                property.first.name!!.asString(),
                property.second,
                propertyIndex,
                requiresNoInitialization = true,
                generationParameters,
                possiblyReifiedTypeVariables,
                contextTypeName,
                typeParameterResolver,
                dslMarker,
            )
        }

    if (isArrayType) {
        if (defaultsAnnotationArgMap?.get(UseDefaultConstructions::useFunctionAdder) != false) {
            generateCollectionAdder(
                "element",
                elementType,
                property.first.name!!.asString(),
                propertyIndex,
                possiblyReifiedTypeVariables,
                contextTypeName,
                typeParameterResolver,
                dslMarker,
                data,
                generationParameters
            )
        }

        if (defaultsAnnotationArgMap?.get(UseDefaultConstructions::useFunctionAdder) != false) {
            elementClass?.findConstructionFunction(data)?.let { constructor ->
                generateDslCollectionAdder(
                    "element",
                    elementType,
                    property.first.name!!.asString(),
                    propertyIndex,
                    constructor,
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

    if (
        !property.second.isPrimitive()
        && defaultsAnnotationArgMap?.get(UseDefaultConstructions::useDefaultSubDslConstruction) != false
    ) {
        val cls = property.second.getClassDeclaration()
        cls?.findConstructionFunction(data)?.let { constructor ->
            generateDslFunctionSetter(
                property.first.name!!.asString().removeSurrounding("\$\$"),
                property.first.name!!.asString(),
                property.second,
                propertyIndex,
                constructor,
                true,
                generationParameters,
                possiblyReifiedTypeVariables,
                contextTypeName,
                typeParameterResolver,
                dslMarker,
                data
            )
            generateSubFunctionSetter(
                property.first.name!!.asString().removeSurrounding("\$\$"),
                property.first.name!!.asString(),
                property.second,
                propertyIndex,
                constructor,
                true,
                generationParameters,
                typeParameters,
                possiblyReifiedTypeVariables,
                contextTypeName,
                typeParameterResolver,
                dslMarker,
                data
            )
        }
    }

    property.first.annotations
        .filterMatchingType(data.usefulTypes.ksUseAlternativeConstruction)
        .map { annotation ->
            annotation to annotation.arguments.associateBy { it.name?.asString() }
        }
        .forEach { (annotation, alternativeConstructionArgMap) ->

            val isElementConstruction: Boolean =
                alternativeConstructionArgMap[UseAlternativeConstruction::isElementConstruction]

            val functionPackageName: String =
                alternativeConstructionArgMap[UseAlternativeConstruction::functionPackageName]

            val functionClass: KSType =
                alternativeConstructionArgMap.getSpecial(UseAlternativeConstruction::functionClass)

            val functionName: String =
                alternativeConstructionArgMap[UseAlternativeConstruction::functionName]

            val functionParameterTypes: List<KSType> =
                alternativeConstructionArgMap.getSpecial(UseAlternativeConstruction::functionParameterTypes)

            val functionReturnType: KSType =
                alternativeConstructionArgMap.getSpecial(UseAlternativeConstruction::functionReturnType)

            val name: String =
                alternativeConstructionArgMap[UseAlternativeConstruction::name]

            if (isElementConstruction && !isArrayType)
                data.logger.warn(
                    "`${UseAlternativeConstruction::isElementConstruction.name} = true` would be ignored for non-array types",
                    annotation.arguments.find { it.name?.asString() == UseAlternativeConstruction::isElementConstruction.name }
                )

            val expectedType = if (isElementConstruction && isArrayType) elementType else property.second

            val resolvedFunction = runCatching {
                resolveFunction(
                    data,
                    expectedType,
                    functionPackageName,
                    functionClass,
                    functionName,
                    functionParameterTypes,
                    functionReturnType
                )
            }.getOrElse {
                processingException(
                    annotation.location,
                    it as Exception,
                    "failed to find function matching ${alternativeConstructionArgMap.values}"
                )
            }
            if (isElementConstruction && isArrayType) {
                generateDslCollectionAdder(
                    name,
                    elementType,
                    property.first.name!!.asString(),
                    propertyIndex,
                    resolvedFunction,
                    generationParameters,
                    possiblyReifiedTypeVariables,
                    contextTypeName,
                    typeParameterResolver,
                    dslMarker,
                    data
                )
            } else {
                generateDslFunctionSetter(
                    name,
                    property.first.name!!.asString(),
                    property.second,
                    propertyIndex,
                    resolvedFunction,
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

