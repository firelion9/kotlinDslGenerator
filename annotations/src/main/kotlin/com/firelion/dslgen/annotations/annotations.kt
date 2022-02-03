/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.annotations

import kotlin.reflect.KClass

/**
 * This annotation indicates that *The Kotlin Dsl Generator*
 * should generate dsl entry point for annotated function.
 *
 * @param markerClass is an annotation class with @[DslMarker] annotation present.
 *        The generator would check passed parameter against those requirements at compile time.
 *        The annotation also should be applicable to functions, types and classes
 *        and have at least binary retention, but generator __currently__ doesn't check
 *        the annotation against it.
 *
 * @param functionName is result DSL entry function name.
 *        If empty or not set, generator replaces it with annotated function name.
 *
 *
 * @param contextClassName is result DSL entry function receiver class name.
 *        If empty or not set, generator replaces it with `$Context$<function signature hash>`.
 *
 * @param monoParameter __(CURRENTLY UNSUPPORTED)__
 *        specifies if annotated one-parameter function's DSL
 *        should be replaced with its parameter's DSL.
 *
 * @param makeInline __(CURRENTLY TREATED AS true)__
 *        if `true`, all associated functions would be generated with `inline` modifier.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateDsl(
    val markerClass: KClass<*>,
    val functionName: String = "",
    val contextClassName: String = "",
    val monoParameter: Boolean = false,
    val makeInline: Boolean = true,
)