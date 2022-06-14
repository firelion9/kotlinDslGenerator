/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.generation

import com.firelion.dslgen.annotations.callDefaultImplMarker
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import java.util.*
import kotlin.contracts.InvocationKind

/**
 * Name prefix for initialization info fields in DSL context classes.
 */
internal const val INITIALIZATION_INFO_PREFIX = "\$initializationInfo\$"

/**
 * Name of `create` function.
 */
internal const val CREATE = "\$create\$"

/**
 * Annotation that suppress `nothing to inline` warning.
 */
internal val NOTHING_TO_INLINE = AnnotationSpec.builder(Suppress::class).addMember("\"NOTHING_TO_INLINE\"").build()

/**
 * Function marker for calls witch should be replaced to `default` version calls.
 */
internal val POST_PROCESSOR_MARKER_NAME =
    MemberName("com.firelion.dslgen.annotations", ::callDefaultImplMarker.name)

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
 * [LinkedList], used as temporary storage for array DSL parameters.
 */
internal val LINKED_LIST = ClassName(LinkedList::class.java.packageName, LinkedList::class.java.simpleName)

/**
 * Annotation that suppress `unchecked cast` warning.
 */
internal val UNCHECKED_CAST = AnnotationSpec.builder(Suppress::class).addMember("\"UNCHECKED_CAST\"").build()
