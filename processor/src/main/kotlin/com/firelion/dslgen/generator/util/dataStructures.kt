/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.annotations.GenerateDsl
import com.firelion.dslgen.annotations.UseAlternativeConstruction
import com.firelion.dslgen.annotations.UseDefaultConstructions
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter

/**
 * Stores references to some useful utilities and generated DSL data.
 * Passed to most internal processor functions.
 */
internal class Data(
    val resolver: Resolver,
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger,
    val usefulTypes: UsefulTypes,
    val allowDefaultArguments: Boolean,
    val generatedDsls: MutableMap<String, GeneratedDslInfo> = mutableMapOf(),
    val generatedSpecifications: MutableSet<String> = mutableSetOf(),
)

/**
 * Stores information about generated DSL context class
 * required for generator to speedup type specifications generation.
 */
internal class GeneratedDslInfo(
    val contextClassPackage: String,
    val contextClassName: String,
    val typeParameters: List<KSTypeParameter>,
    val parameters: Map<String, GeneratedDslParameterInfo>,
    val returnType: KSType,
)

/**
 * Stores information about generated DSL context class's property.
 */
internal class GeneratedDslParameterInfo(
    val backingPropertyName: String,
    val index: Int,
    val type: KSType,
)

/**
 * Collection of well-known useful [KSType]s
 */
internal class UsefulTypes(resolver: Resolver) {
    val ksArray: KSType
    val ksBooleanArray: KSType
    val ksByteArray: KSType
    val ksCharArray: KSType
    val ksShortArray: KSType
    val ksIntArray: KSType
    val ksFloatArray: KSType
    val ksLongArray: KSType
    val ksDoubleArray: KSType

    val ksIterable: KSType
    val ksMutableIterable: KSType
    val ksCollection: KSType
    val ksMutableCollection: KSType
    val ksList: KSType
    val ksMutableList: KSType
    val ksArrayList: KSType
    val ksSet: KSType
    val ksMutableSet: KSType
    val ksHashSet: KSType
    val ksSequence: KSType
    val ksMap: KSType
    val ksMutableMap: KSType
    val ksHashMap: KSType

    val ksGeneratedDsl: KSType
    val ksUseAlternativeConstruction: KSType
    val ksUseDefaultConstructions: KSType

    val ksVoid: KSType

    init {
        ksArray = resolver.builtIns.arrayType
        ksBooleanArray = resolver.ksTypeOf<BooleanArray>()
        ksByteArray = resolver.ksTypeOf<ByteArray>()
        ksCharArray = resolver.ksTypeOf<CharArray>()
        ksShortArray = resolver.ksTypeOf<ShortArray>()
        ksIntArray = resolver.ksTypeOf<IntArray>()
        ksFloatArray = resolver.ksTypeOf<FloatArray>()
        ksLongArray = resolver.ksTypeOf<LongArray>()
        ksDoubleArray = resolver.ksTypeOf<DoubleArray>()

        ksIterable = resolver.builtIns.iterableType
        ksMutableIterable = resolver.ksTypeOf<MutableIterable<*>>()
        ksCollection = resolver.ksTypeOf<Collection<*>>()
        ksMutableCollection = resolver.ksTypeOf<MutableCollection<*>>()
        ksList = resolver.ksTypeOf<List<*>>()
        ksMutableList = resolver.ksTypeOf<MutableList<*>>()
        ksArrayList = resolver.ksTypeOf<ArrayList<*>>()
        ksSet = resolver.ksTypeOf<Set<*>>()
        ksMutableSet = resolver.ksTypeOf<MutableSet<*>>()
        ksHashSet = resolver.ksTypeOf<HashSet<*>>()
        ksSequence = resolver.ksTypeOf<Sequence<*>>()
        ksMap = resolver.ksTypeOf<Map<*, *>>()
        ksMutableMap = resolver.ksTypeOf<MutableMap<*, *>>()
        ksHashMap = resolver.ksTypeOf<HashMap<*, *>>()

        ksGeneratedDsl = resolver.ksTypeOf<GenerateDsl>()
        ksUseDefaultConstructions = resolver.ksTypeOf<UseDefaultConstructions>()
        ksUseAlternativeConstruction = resolver.ksTypeOf<UseAlternativeConstruction>()

        ksVoid = resolver.ksTypeOf<Void>()
    }

    private inline fun <reified T> Resolver.ksTypeOf() =
        getClassDeclarationByName<T>()!!.asStarProjectedType()

}