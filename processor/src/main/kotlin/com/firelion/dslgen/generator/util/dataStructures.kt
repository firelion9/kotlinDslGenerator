/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.annotations.GenerateDsl
import com.firelion.dslgen.annotations.UseAlternativeConstruction
import com.firelion.dslgen.annotations.UseDefaultConstructions
import com.firelion.dslgen.strategy.ArrayBackingPropertyInitializers
import com.firelion.dslgen.strategy.NamingStrategy
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

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
    val namingStrategy: NamingStrategy = NamingStrategy.Legacy,
    val arrayBackingPropertyInitializer: CodeBlock = ArrayBackingPropertyInitializers.LINKED_LIST_INITIALIZER,
)

/**
 * Stores information about generated DSL context class
 * required for generator to speedup type specifications generation.
 */
internal class GeneratedDslInfo(
    val contextClassPackage: String,
    val contextClassSimpleName: String,
    val typeParameters: List<KSTypeParameter>,
    val parameters: Map<String, GeneratedDslParameterInfo>,
    val returnType: KSType,
) {
    fun contextClassName() = ClassName(contextClassPackage, contextClassSimpleName)
}

/**
 * Stores information about generated DSL context class's property.
 */
internal class GeneratedDslParameterInfo(
    val name: String,
    val backingPropertyName: String,
    val index: Int,
    val type: KSType,
)

/**
 * Collection of well-known useful [KSType]s
 */
internal class UsefulTypes(resolver: Resolver) {
    val ksArray: KSType = resolver.builtIns.arrayType
    val ksBooleanArray: KSType = resolver.ksTypeOf<BooleanArray>()
    val ksByteArray: KSType = resolver.ksTypeOf<ByteArray>()
    val ksCharArray: KSType = resolver.ksTypeOf<CharArray>()
    val ksShortArray: KSType = resolver.ksTypeOf<ShortArray>()
    val ksIntArray: KSType = resolver.ksTypeOf<IntArray>()
    val ksFloatArray: KSType = resolver.ksTypeOf<FloatArray>()
    val ksLongArray: KSType = resolver.ksTypeOf<LongArray>()
    val ksDoubleArray: KSType = resolver.ksTypeOf<DoubleArray>()

    val ksIterable: KSType = resolver.builtIns.iterableType
    val ksMutableIterable: KSType = resolver.ksTypeOf<MutableIterable<*>>()
    val ksCollection: KSType = resolver.ksTypeOf<Collection<*>>()
    val ksMutableCollection: KSType = resolver.ksTypeOf<MutableCollection<*>>()
    val ksList: KSType = resolver.ksTypeOf<List<*>>()
    val ksMutableList: KSType = resolver.ksTypeOf<MutableList<*>>()
    val ksArrayList: KSType = resolver.ksTypeOf<ArrayList<*>>()
    val ksSet: KSType = resolver.ksTypeOf<Set<*>>()
    val ksMutableSet: KSType = resolver.ksTypeOf<MutableSet<*>>()
    val ksHashSet: KSType = resolver.ksTypeOf<HashSet<*>>()
    val ksSequence: KSType = resolver.ksTypeOf<Sequence<*>>()
    val ksMap: KSType = resolver.ksTypeOf<Map<*, *>>()
    val ksMutableMap: KSType = resolver.ksTypeOf<MutableMap<*, *>>()
    val ksHashMap: KSType = resolver.ksTypeOf<HashMap<*, *>>()

    val ksGeneratedDsl: KSType = resolver.ksTypeOf<GenerateDsl>()
    val ksUseDefaultConstructions: KSType = resolver.ksTypeOf<UseDefaultConstructions>()
    val ksUseAlternativeConstruction: KSType = resolver.ksTypeOf<UseAlternativeConstruction>()

    val ksVoid: KSType = resolver.ksTypeOf<Void>()

    val ksNullableAny: KSType = resolver.ksTypeOf<Any>().makeNullable()

    private inline fun <reified T> Resolver.ksTypeOf() =
        getClassDeclarationByName<T>()!!.asStarProjectedType()

}