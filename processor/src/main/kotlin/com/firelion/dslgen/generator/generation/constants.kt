/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.annotations.DslGeneratorInternalApi
import com.firelion.dslgen.annotations.GeneratedDsl
import com.firelion.dslgen.annotations.callDefaultImplMarker
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import kotlin.contracts.InvocationKind

/**
 * [Suppress] annotation [ClassName].
 */
internal val SUPPRESS = ClassName(Suppress::class.java.packageName, Suppress::class.java.simpleName)

/**
 * Annotation that suppress `nothing to inline` warning.
 */
internal val NOTHING_TO_INLINE = AnnotationSpec.builder(Suppress::class).addMember("\"NOTHING_TO_INLINE\"").build()

/**
 * Function marker for calls which should be replaced to `default` version calls.
 */
@OptIn(DslGeneratorInternalApi::class)
internal val POST_PROCESSOR_MARKER_NAME =
    MemberName("com.firelion.dslgen.annotations", ::callDefaultImplMarker.name)

/**
 * Annotation marker for generated files.
 */
@OptIn(DslGeneratorInternalApi::class)
internal val GENERATED_DSL_MARKER_NAME =
    ClassName(GeneratedDsl::class.java.packageName, GeneratedDsl::class.java.simpleName)

/**
 * Opt-in for internal markers (such as [POST_PROCESSOR_MARKER_NAME] and [GENERATED_DSL_MARKER_NAME])
 */
internal val INTERNAL_API_OPT_IN = AnnotationSpec
    .builder(ClassName("kotlin", "OptIn"))
    .addMember("%T::class", ClassName("com.firelion.dslgen.annotations", "DslGeneratorInternalApi")) // @hardlink#004
    .build()


/**
 * Contracts API contract builder function.
 */
internal val CONTRACT_NAME = MemberName("kotlin.contracts", "contract")

/**
 * Opt-in annotation for contracts API.
 */
internal val EXPERIMENTAL_CONTRACTS_OPT_IN = AnnotationSpec
    .builder(ClassName("kotlin", "OptIn"))
    .addMember("%T::class", ClassName("kotlin.contracts", "ExperimentalContracts"))
    .build()

/**
 * [InvocationKind.EXACTLY_ONCE] from contracts API.
 */
internal val EXACTLY_ONCE_NAME = MemberName(ClassName("kotlin.contracts", "InvocationKind"), "EXACTLY_ONCE")

/**
 * Annotation that suppress `unchecked cast` warning.
 */
internal val UNCHECKED_CAST = AnnotationSpec.builder(SUPPRESS).addMember("\"UNCHECKED_CAST\"").build()
