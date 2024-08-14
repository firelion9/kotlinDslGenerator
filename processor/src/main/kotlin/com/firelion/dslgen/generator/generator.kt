/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.annotations.PropertyAccessor
import com.firelion.dslgen.annotations.UseAlternativeConstruction
import com.firelion.dslgen.annotations.UseDefaultConstructions
import com.firelion.dslgen.generator.generation.*
import com.firelion.dslgen.generator.util.*
import com.firelion.dslgen.logging
import com.firelion.dslgen.readGenerationParametersAnnotation
import com.firelion.dslgen.util.ExtensionKSValueParameter
import com.firelion.dslgen.util.processingException
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
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
    newTypeVariables: List<TypeVariableName>? = null,
    newTypeParameters: List<KSTypeParameter>? = null,
    returnTypeArguments: List<KSTypeArgument>? = null,
): ClassName = try {
    processFunction0(
        function,
        data,
        parentGenerationParameters,
        newTypeVariables,
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
    newTypeVariables: List<TypeVariableName>?,
    newTypeParameters: List<KSTypeParameter>?,
    returnTypeArguments: List<KSTypeArgument>?,
): ClassName {
    data.logger.logging(function) { "processing function $function..." }

    val identifier = function.getUniqueIdentifier(data)
    val pkg = function.packageName.asString()
        .let { if (it.startsWith("kotlin.") || it == "kotlin") "generated.$it" else it }

    data.logger.logging(function) { "calculated identifier is $identifier" }

    val functionReturnType: KSType by lazy { function.returnType!!.resolve() }

    val contextClassNameStr = data.namingStrategy.dslContextClassName(identifier, function.simpleName.asString())

    if (identifier in data.generatedDsls) {

        data.logger.logging(function) { "DSL with identifier $identifier is already generated in this session" }

        val generatedDsl = data.generatedDsls.getValue(identifier)

        generateSpecification(
            generatedDsl,
            function.containingFile,
            newTypeVariables,
            newTypeParameters,
            returnTypeArguments,
            parentGenerationParameters,
            data,
            function
        )

        return ClassName(pkg, generatedDsl.contextClassSimpleName)
    } else {
        val dec = data.resolver.getClassDeclarationByName("$pkg.$contextClassNameStr")

        if (dec != null) {
            data.logger.logging(function) { "find DSL $identifier" }

            val generatedDsl = GeneratedDslInfo(
                dec.packageName.asString(),
                dec.simpleName.asString(),
                dec.typeParameters,
                dec.declarations
                    .filterIsInstance<KSPropertyDeclaration>()
                    .filterNot {
                        data.namingStrategy.isInitializationInfoProperty(it.simpleName.asString())
                    }
                    .mapIndexed { idx, it ->
                        val name = data.namingStrategy.recoverParameterName(it.simpleName.asString())
                        name to GeneratedDslParameterInfo(
                            name,
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
                newTypeVariables,
                newTypeParameters,
                returnTypeArguments,
                parentGenerationParameters,
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
                contextClassName = contextClassNameStr
            )


    val typeParameters = function.typeParameters

    val typeParameterResolver = typeParameters
        .toTypeParameterResolver(sourceTypeHint = (function.qualifiedName ?: function.simpleName).asString())

    val typeVariables = typeParameters.toTypeVariableNames(typeParameterResolver)

    val functionParameters = buildList {
        addAll(ExtensionKSValueParameter.receiversOf(function, data).map { it to it.resolveActualType(data) })

        function.parameters.forEach { add(it to it.resolveActualType(data)) }
    }

    val fileBuilder = FileSpec.builder(
        pkg,
        data.namingStrategy.dslFileName(identifier, contextClassNameStr, function.simpleName.asString())
    )

    if (data.allowDefaultArguments) {
        fileBuilder
            .addAnnotation(INTERNAL_API_OPT_IN)
            .addAnnotation(GENERATED_DSL_MARKER_NAME)
    }

    val generatedDsl = GeneratedDslInfo(
        pkg,
        generationParameters.contextClassName,
        function.typeParameters,
        function.parameters
            .mapIndexed { idx, it ->
                val backingPropertyName = it.name!!.asString()
                val name = data.namingStrategy.recoverParameterName(backingPropertyName)
                name to GeneratedDslParameterInfo(
                    name,
                    backingPropertyName,
                    idx,
                    it.resolveActualType(data)
                )
            }
            .toMap(),
        functionReturnType
    )
    data.generatedDsls[identifier] = generatedDsl

    data.logger.logging(function) { "generating DSL $identifier" }

    if (function.extensionReceiver != null || !function.isConstructor() && function.parentDeclaration is KSClassDeclaration)
        data.logger.warn(
            "DSL generator may produce incomplete code for function $function with receiver parameters",
            function
        )

    generateSpecification(
        generatedDsl,
        function.containingFile,
        newTypeVariables,
        newTypeParameters,
        returnTypeArguments,
        parentGenerationParameters,
        data,
        function
    )

    val contextClassName = fileBuilder.generateDsl(
        generationParameters,
        typeParameters,
        typeVariables,
        functionParameters,
        functionReturnType,
        function,
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
    exitFunction: KSFunctionDeclaration,
    typeParameterResolver: TypeParameterResolver,
    data: Data,
): ClassName {
    val erasableTypeVariables = typeVariables.map { if (it.isReified) it.copy(reified = false) else it }

    val contextClassSpec = generateContextClass(
        generationParameters,
        erasableTypeVariables,
        functionParameters,
        typeParameterResolver,
        data
    )

    val contextClassName =
        ClassName(packageName, generationParameters.contextClassName)

    val erasableContextTypeName =
        (if (typeVariables.isEmpty()) contextClassName
        else contextClassName.parameterizedBy(erasableTypeVariables.map { it }))
            .let {
                it.copy(
                    annotations = it.annotations + generationParameters.dslMarker
                )
            }

    val reifiedTypeVariables = typeVariables.withReifiedTypeVariables(functionParameters, contextClassSpec, data)

    val reifiedContextTypeName =
        (if (typeVariables.isEmpty()) contextClassName
        else contextClassName.parameterizedBy(reifiedTypeVariables.map { it }))
            .let {
                it.copy(
                    annotations = it.annotations + generationParameters.dslMarker
                )
            }

    generateCreateFunction(
        generationParameters,
        reifiedTypeVariables,
        functionParameters,
        reifiedContextTypeName,
        returnType,
        exitFunction,
        typeParameterResolver,
        data
    )

    functionParameters.forEachIndexed { index, prop ->
        generatePropertySettersAndGetters(
            prop,
            data,
            index,
            generationParameters,
            typeParameters,
            erasableTypeVariables,
            erasableContextTypeName,
            typeParameterResolver,
        )
    }

    if (generationParameters.functionName != null)
        generateEntryFunction(
            generationParameters,
            reifiedTypeVariables,
            reifiedContextTypeName,
            returnType,
            typeParameterResolver,
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
    typeVariables: List<TypeVariableName>,
    contextTypeName: TypeName,
    typeParameterResolver: TypeParameterResolver,
) {
    data.logger.logging(property.first) { "generating setters and getters" }

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
            typeVariables,
            contextTypeName,
            typeParameterResolver,
            data,
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
            typeVariables,
            contextTypeName,
            typeParameterResolver,
            data,
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
                typeVariables,
                contextTypeName,
                typeParameterResolver,
                data,
            )
        }

    if (isArrayType) {
        if (defaultsAnnotationArgMap?.get(UseDefaultConstructions::useFunctionAdder) != false) {
            generateCollectionAdder(
                data.namingStrategy.elementAdderName(property.first.name!!.asString()),
                elementType,
                property.first.name!!.asString(),
                propertyIndex,
                typeVariables,
                contextTypeName,
                typeParameterResolver,
                generationParameters,
                data
            )
        }


        val generateDslAdder =
            defaultsAnnotationArgMap?.get(UseDefaultConstructions::useSubDslAdder) != false

        val generateSubFunctionAdder =
            defaultsAnnotationArgMap?.get(UseDefaultConstructions::useSubFunctionAdder) != false


        if (generateDslAdder || generateSubFunctionAdder) {
            elementClass?.findConstructionFunction(data)?.let { constructor ->

                if (generateDslAdder)
                    generateDslCollectionAdder(
                        data.namingStrategy.elementAdderName(property.first.name!!.asString()),
                        elementType,
                        property.first.name!!.asString(),
                        propertyIndex,
                        constructor,
                        generationParameters,
                        typeVariables,
                        typeParameters,
                        contextTypeName,
                        typeParameterResolver,
                        data
                    )

                if (generateSubFunctionAdder)
                    generateSubFunctionAdder(
                        data.namingStrategy.elementAdderName(property.first.name!!.asString()),
                        elementType,
                        property.first.name!!.asString(),
                        propertyIndex,
                        constructor,
                        generationParameters,
                        typeParameters,
                        typeVariables,
                        contextTypeName,
                        typeParameterResolver,
                        data
                    )

            }
        }
    }


    val propertyTypeIsPrimitive = property.second.isPrimitive()

    data.logger.logging(property.first) {
        "propertyTypeIsPrimitive=$propertyTypeIsPrimitive"
    }

    if (!propertyTypeIsPrimitive) {

        val generateDefaultSubDslSetter =
            defaultsAnnotationArgMap?.get(UseDefaultConstructions::useDefaultSubDslConstruction) != false

        val generateDefaultSubFunctionSetter =
            defaultsAnnotationArgMap?.get(UseDefaultConstructions::useSubFunctionSetter) != false

        data.logger.logging(property.first) {
            "generateDefaultSubDslSetter=$generateDefaultSubDslSetter, generateDefaultSubFunctionSetter=$generateDefaultSubFunctionSetter"
        }

        if (generateDefaultSubDslSetter || generateDefaultSubFunctionSetter) {

            val cls = property.second.getClassDeclaration()

            data.logger.logging(property.first) {
                "${property.second} resolved declaration is $cls"
            }

            cls?.findConstructionFunction(data)?.let { constructor ->

                if (generateDefaultSubDslSetter)
                    generateDslFunctionSetter(
                        property.first.name!!.asString(),
                        property.first.name!!.asString(),
                        property.second,
                        propertyIndex,
                        constructor,
                        true,
                        generationParameters,
                        typeVariables,
                        typeParameters,
                        contextTypeName,
                        typeParameterResolver,
                        data
                    )

                if (generateDefaultSubFunctionSetter)
                    generateSubFunctionSetter(
                        property.first.name!!.asString(),
                        property.first.name!!.asString(),
                        property.second,
                        propertyIndex,
                        constructor,
                        true,
                        generationParameters,
                        typeParameters,
                        typeVariables,
                        contextTypeName,
                        typeParameterResolver,
                        data
                    )
            }
        }
    }

    property.first.annotations
        .filterMatchingType(data.usefulTypes.ksUseAlternativeConstruction)
        .forEach { annotation ->

            val (isElementConstruction: Boolean, name: String, resolvedFunction) = resolveAlternativeConstructor(
                annotation,
                property,
                { elementType },
                isArrayType,
                data
            )

            if (isElementConstruction && isArrayType) {
                generateDslCollectionAdder(
                    name,
                    elementType,
                    property.first.name!!.asString(),
                    propertyIndex,
                    resolvedFunction,
                    generationParameters,
                    typeVariables,
                    typeParameters,
                    contextTypeName,
                    typeParameterResolver,
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
                    typeVariables,
                    typeParameters,
                    contextTypeName,
                    typeParameterResolver,
                    data
                )
            }
        }
}

private inline fun resolveAlternativeConstructor(
    annotation: KSAnnotation,
    property: Pair<KSValueParameter, KSType>,
    elementType: () -> KSType,
    isArrayType: Boolean,
    data: Data,
): Triple<Boolean, String, KSFunctionDeclaration> {
    val alternativeConstructionArgMap = annotation.arguments.associateBy { it.name?.asString() }

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

    val expectedType = if (isElementConstruction && isArrayType) elementType() else property.second

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

    return Triple(isElementConstruction, name, resolvedFunction)
}

@Suppress("UNCHECKED_CAST")
private fun <T> Map<String?, KSValueArgument>.getSpecial(key: KProperty<*>) = getValue(key.name).value as T

@Suppress("UNCHECKED_CAST")
private operator fun <T> Map<String?, KSValueArgument>.get(key: KProperty<T>) = getValue(key.name).value as T
