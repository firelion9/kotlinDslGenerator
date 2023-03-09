/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
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
 * @param makeInline
 *        if `false`, `inline` modifier will be added only to generated functions
 *        with `reified` type parameters or builder lambdas,
 *        otherwise it will be added to all generated functions.
 *        Default is `true`.
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

/**
 * Specifies built-in DSL accessors those should be generated for annotated parameter.
 *
 * If function have some parameters annotated with [UseDefaultConstructions],
 * but it is neither annotated with [GenerateDsl] nor used for generating a sub-DSl for another function,
 * [UseDefaultConstructions] will have no effect.
 *
 * [useSubDslAdder], [useSubFunctionAdder] and [useFunctionAdder] are ignored for non-array type parameters.
 *
 * @see [UseAlternativeConstruction]
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class UseDefaultConstructions(
    val useDefaultSubDslConstruction: Boolean = true,
    val useSubFunctionSetter: Boolean = true,
    val useFunctionLikeSetter: Boolean = true,
    val useFunctionLikeGetter: Boolean = true,
    val usePropertyLikeAccessor: PropertyAccessor = PropertyAccessor.NO,
    val useFunctionAdder: Boolean = true,
    val useSubFunctionAdder: Boolean = true,
    val useSubDslAdder: Boolean = true,
)

enum class PropertyAccessor {
    NO, GETTER, GETTER_AND_SETTER;
}

/**
 * Provides information to generate sub-DSL construction for annotated parameter using resolved function.
 * This annotation could be used multiple time on the same parameter
 * to generate multiple alternative ways to construct it,
 * but each annotation should have **unique [name]** to ensure compiler could choose correct one.
 *
 * If function have some parameters annotated with [UseAlternativeConstruction],
 * but it is neither annotated with [GenerateDsl] nor used for generating a sub-DSl for another function,
 * [UseAlternativeConstruction] will have no effect.
 *
 * Resolving algorithm:
 *
 * * If [functionClass] is [Nothing] (default value),
 *   we will look for a top-level function in package [functionPackageName] with name [functionName].
 *   Otherwise, we ignore [functionPackageName] and look for function in [functionClass]
 *   (where "<init>" means constructor).
 *
 * * If [functionReturnType] is [Nothing] (default value),
 *   we will look for functions those result is assignable to expected type (derived from parameter type).
 *   Otherwise, we will look for functions with return type [functionReturnType]
 *   (witch should be assignable to expected type).
 *   If [functionReturnType] is not assignable to expected type, we will throw an exception.
 *
 * * If [functionParameterTypes] is empty, we will look for the only function matching previous requirements.
 *   Otherwise, we will look for function with matching parameter types.
 *
 * * If there are more than one or zero function matching previous requirements, we will throw an exception.
 *   Otherwise, the only one matching function is resolving result.
 *
 * @param [isElementConstruction] is also used to determine if *The Kotlin Dsl Generator* should generate
 *      setter or array element adder using resolved function.
 *
 * @see [UseDefaultConstructions]
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class UseAlternativeConstruction(
    val isElementConstruction: Boolean = false,
    val functionPackageName: String = "",
    val functionClass: KClass<*> = Nothing::class,
    val functionName: String,
    val functionParameterTypes: Array<KClass<*>> = [],
    val functionReturnType: KClass<*> = Nothing::class,
    val name: String = ""
)

