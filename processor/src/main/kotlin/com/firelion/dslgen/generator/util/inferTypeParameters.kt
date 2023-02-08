/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.util.unreachableCode
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.*

private const val CURRENT_PREFIX = "Current\$"
private const val NEW_PREFIX = "New\$"

/**
 * Infers values of [newTypeParameters] or [currentTypeParameters]
 * (depending on [shouldInferNewParameters] value) based on assumption
 * that [actualReturnType] should be assigned to [expectedReturnType].
 *
 * [expectedReturnType] may only use type parameters from [currentTypeParameters],
 * while [actualReturnType] may only use type parameters from [newTypeParameters].
 *
 * Uses [actualValueParameterTypes] gather extra type parameter variance information.
 *
 * @return pair of inferred types and extra type parameters.
 */
// FIXME: current implementation cover only simple cases and may produce incorrect results if
//    type parameter has 2 or more non-trivial type bounds.
internal fun inferTypeParameters(
    currentTypeParameters: List<KSTypeParameter>,
    expectedReturnType: KSType,
    newTypeParameters: List<KSTypeParameter>,
    actualValueParameterTypes: List<KSType>,
    actualReturnType: KSType,
    data: Data,
    shouldInferNewParameters: Boolean = true,
): Pair<Map<KSTypeParameter, KSType>, List<KSTypeParameter>> {

    // We use names with prefixes instead of (1) just names or (2) `KSTypeParameter` instances themselves because:
    // when we use (1), we will mix bounds for old and new type parameters with equal names;
    // when we use (2), we have no guarantees about `KSTypeParameter` `equals` and `hashCode` overloads,
    //   on KSP 1.7.22-1.0.8 we receive unequal `KSTypeParameter` instances matching the same logical parameter
    //   (one from function arguments and another from resolved type arguments)
    val typeParameterById =
        (currentTypeParameters.asSequence().map { CURRENT_PREFIX + it.name.asString() to it }
                + newTypeParameters.asSequence().map { NEW_PREFIX + it.name.asString() to it })
            .toMap()

    val bounds: Map<String, Bounds> = typeParameterById
        .mapValues { Bounds(arrayListOf(), arrayListOf(), mutableSetOf()) }

    try {
        matchTypesRecursive(
            Variance.COVARIANT,
            expectedReturnType.expandTypeAliases(data),
            Variance.INVARIANT,
            actualReturnType.expandTypeAliases(data),
            isSupertypeExposedThroughNewTypeParameters = false,
            bounds,
            data
        )
    } catch (e: Exception) {
        throw IllegalStateException("can't project $actualReturnType to $expectedReturnType", e)
    }

    actualValueParameterTypes.forEach {
        collectVariancesRecursive(
            it,
            Variance.CONTRAVARIANT,
            isInNewParameterSet = true,
            bounds,
            data
        )
    }

    typeParameterById.forEach { (id, param) ->
        val bds = bounds.getValue(id)

        param.bounds.forEach { bound ->
            bds.add(bound.resolve(), true)
        }
    }

    val inferredTypes = mutableMapOf<KSTypeParameter, KSType>()
    val newParameters = mutableListOf<KSTypeParameter>()

    (if (shouldInferNewParameters) newTypeParameters else currentTypeParameters)
        .forEach { param ->
            val bds = bounds.getValue(param, isInNewParameterSet = shouldInferNewParameters)

            data.logger.logging("bounds for parameter $param are $bds")

            when (bds.computeVariance()) {
                Variance.INVARIANT -> {

                    val usefulUpperBounds = bds.upperBounds.filter { it != data.usefulTypes.ksNullableAny }

                    data.logger.logging("useful upper bound types for $param are $usefulUpperBounds")

                    if (usefulUpperBounds.size == 1) {
                        data.logger.logging("inferred $param = ${usefulUpperBounds.first()}")

                        inferredTypes[param] = usefulUpperBounds.first()
                        return@forEach
                    }

                    val finalUpperBounds = bds.upperBounds.filter {
                        val dec = it.declaration as? KSClassDeclaration ?: return@filter false
                        !dec.isOpen()
                    }

                    if (finalUpperBounds.size == 1) {
                        data.logger.logging("inferred $param = ${finalUpperBounds.first()}")

                        inferredTypes[param] = finalUpperBounds.first()
                        return@forEach
                    }

                    val intersectionTypes = bds.upperBounds.intersect(bds.lowerBounds.toSet())

                    if (intersectionTypes.size == 1) {
                        data.logger.logging("inferred $param = ${intersectionTypes.first()}")

                        inferredTypes[param] = intersectionTypes.first()
                    } else if (inferredTypes.size > 1) {

                        data.logger.warn("failed to infer $param")
                        error("failed to infer $param: candidate types are $inferredTypes")

                    } else {

                        data.logger.logging("$param can't be exposed through other parameters. Adding it to result parameter list")

                        newParameters.add(param)
                    }
                }

                Variance.COVARIANT -> {
                    // TODO not much better then naive

                    val upperBound = bds.upperBounds.find { it != data.usefulTypes.ksNullableAny }

                    if (upperBound != null) {
                        data.logger.logging("inferred $param = $upperBound")
                        inferredTypes[param] = upperBound
                    } else newParameters.add(param)
                }

                Variance.CONTRAVARIANT -> {
                    // TODO not much better then naive

                    val lowerBound = bds.lowerBounds.find { it != data.usefulTypes.ksVoid }

                    if (lowerBound != null) {
                        data.logger.logging("inferred $param = $lowerBound")
                        inferredTypes[param] = lowerBound
                    } else newParameters.add(param)
                }

                Variance.STAR -> {
                    // unused type parameter

                    data.logger.logging("inferred $param = ${data.usefulTypes.ksVoid}")
                    inferredTypes[param] = data.usefulTypes.ksVoid
                }
            }
        }

    return inferredTypes to newParameters
}

private data class Bounds(
    val lowerBounds: MutableList<KSType>,
    val upperBounds: MutableList<KSType>,
    val variances: MutableSet<Variance>,
) {
    fun add(bound: KSType, isUpper: Boolean) =
        (if (isUpper) upperBounds else lowerBounds)
            .add(bound)

    fun computeVariance() =
        when {
            Variance.INVARIANT in variances -> Variance.INVARIANT
            Variance.COVARIANT in variances && Variance.CONTRAVARIANT in variances -> Variance.INVARIANT

            Variance.COVARIANT in variances -> Variance.COVARIANT
            Variance.CONTRAVARIANT in variances -> Variance.CONTRAVARIANT

            else -> Variance.STAR // type parameter is unused
        }
}

/**
 * Assumes that [supertypeArgument] and [subtypeArgument] are type arguments in equal position of two generic types
 * and type with [supertypeArgument] is supertype of type with [subtypeArgument].
 * Tries to find bounds for type parameters in those types and adds them to [bounds].
 * Throws error if assumption is certainly can't be true.
 *
 * [supertypeNestedVarianceModifier] and [subtypeNestedVarianceModifier] are variances of parent type arguments
 */
private fun matchTypeArgs(
    supertypeNestedVarianceModifier: Variance,
    supertypeParameterSideVariance: Variance,
    supertypeArgument: KSTypeArgument,
    subtypeNestedVarianceModifier: Variance,
    subtypeParameterSideVariance: Variance,
    subtypeArgument: KSTypeArgument,
    isSupertypeExposedThroughNewTypeParameters: Boolean,
    bounds: Map<String, Bounds>,
    data: Data,
) {
    val supertypeVariance = combineMultiplyVariances(
        supertypeNestedVarianceModifier,
        combineSumVariances(supertypeParameterSideVariance, supertypeArgument.variance)
    )

    val subtypeVariance = combineMultiplyVariances(
        subtypeNestedVarianceModifier,
        combineSumVariances(subtypeParameterSideVariance, subtypeArgument.variance)
    )

    // [supertype] and [subtype] in comments below mean
    // `supertypeArgument.type!!.resolve()` and `subtypeArgument.type!!.resolve()` respectively
    when {
        supertypeVariance == Variance.STAR || subtypeVariance == Variance.STAR -> {
            return // No extra information
        }

        supertypeVariance == Variance.CONTRAVARIANT && subtypeVariance == Variance.COVARIANT -> {
            // [supertype] == Nothing <=> [supertype] is Nothing
            matchTypesRecursive(
                supertypeVariance,
                supertype = data.usefulTypes.ksVoid,
                subtypeVariance,
                subtype = supertypeArgument.type!!.resolve(),
                !isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.COVARIANT && subtypeVariance == Variance.CONTRAVARIANT -> {
            // [supertype] == Any? <=> Any? is [supertype]
            matchTypesRecursive(
                supertypeVariance,
                supertype = supertypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = data.usefulTypes.ksNullableAny,
                isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.COVARIANT && subtypeVariance == Variance.COVARIANT -> {
            // [supertype] __is supertype of__ [subtype] <=> [subtype] is [supertype]
            matchTypesRecursive(
                supertypeVariance,
                supertype = supertypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = subtypeArgument.type!!.resolve(),
                isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.CONTRAVARIANT && subtypeVariance == Variance.CONTRAVARIANT -> {
            // [supertype] __is subtype of__ [subtype] <=> [supertype] is [subtype]
            matchTypesRecursive(
                supertypeVariance,
                supertype = subtypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = supertypeArgument.type!!.resolve(),
                !isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.INVARIANT && subtypeVariance == Variance.INVARIANT -> {
            // [supertype] == [subtype] <=> [supertype] is subtype && [subtype] is [supertype]

            matchTypesRecursive(
                supertypeVariance,
                supertype = supertypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = subtypeArgument.type!!.resolve(),
                isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )

            matchTypesRecursive(
                supertypeVariance,
                supertype = subtypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = supertypeArgument.type!!.resolve(),
                !isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.INVARIANT && subtypeVariance == Variance.COVARIANT -> {
            // [supertype] == [subtype] == Nothing <=> [supertype] is Nothing && [subtype] is Nothing
            val nothing = data.usefulTypes.ksVoid

            matchTypesRecursive(
                supertypeVariance,
                supertype = nothing,
                subtypeVariance,
                subtype = supertypeArgument.type!!.resolve(),
                !isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )

            matchTypesRecursive(
                supertypeVariance,
                supertype = nothing,
                subtypeVariance,
                subtype = subtypeArgument.type!!.resolve(),
                isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.INVARIANT && subtypeVariance == Variance.CONTRAVARIANT -> {
            // [supertype] == [subtype] == Any? <=> Any? is [supertype] && Any? is [subtype]
            val nullableAny = data.usefulTypes.ksNullableAny

            matchTypesRecursive(
                supertypeVariance,
                supertype = supertypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = nullableAny,
                isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )

            matchTypesRecursive(
                supertypeVariance,
                supertype = subtypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = nullableAny,
                !isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.COVARIANT && subtypeVariance == Variance.INVARIANT -> {
            // [subtype] is [supertype]
            matchTypesRecursive(
                supertypeVariance,
                supertype = supertypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = subtypeArgument.type!!.resolve(),
                isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        supertypeVariance == Variance.CONTRAVARIANT && subtypeVariance == Variance.INVARIANT -> {
            // [supertype] is [subtype]
            matchTypesRecursive(
                supertypeVariance,
                supertype = subtypeArgument.type!!.resolve(),
                subtypeVariance,
                subtype = supertypeArgument.type!!.resolve(),
                !isSupertypeExposedThroughNewTypeParameters,
                bounds,
                data,
            )
        }

        else -> unreachableCode()
    }
}

/**
 * Combines declaration-site and use-site variances.
 *
 * For example:
 *
 *   declaration-site: `interface Set<out E> : Collection<E> {...}`, E variance modifier is `out` (covariant)
 *
 *   use-site: `Set<Int>`, E variance modifier is `<empty>` (invariant)
 *
 *   result: E variance modifier `out` (covariant)
 */
private fun combineSumVariances(v1: Variance, v2: Variance) = when {
    v1 == Variance.STAR || v2 == Variance.STAR -> Variance.STAR

    v1 == Variance.INVARIANT -> v2
    v2 == Variance.INVARIANT -> v1

    v1 == v2 -> v1 /* or v2 */

    // covariant + contravariant
    v1 != v2 -> error("variances $v1 and $v2 can't be combined")

    else -> unreachableCode()
}

/**
 * Combines two variance modifiers from nested generic type parameters.
 *
 * For example, `Comparator<Comparator<E>>`:
 *
 *   outer `Comparator` first generic parameter variance modifier is `in` (contravariant)
 *
 *   inner `Comparator` first generic parameter variance modifier is `in` (contravariant) too
 *
 *   result modifier is `out` (covariant)
 */
private fun combineMultiplyVariances(v1: Variance, v2: Variance) = when {
    v1 == Variance.STAR || v2 == Variance.STAR -> Variance.STAR

    v1 == Variance.INVARIANT -> v2
    v2 == Variance.INVARIANT -> v1

    // invariant * invariant or contravariant * contravariant
    v1 == v2 -> Variance.COVARIANT

    // invariant * contravariant
    v1 != v2 -> Variance.CONTRAVARIANT

    else -> unreachableCode()
}

private fun matchTypesRecursive(
    supertypeNestedVarianceModifier: Variance,
    supertype: KSType,
    subtypeNestedVarianceModifier: Variance,
    subtype: KSType,
    isSupertypeExposedThroughNewTypeParameters: Boolean,
    bounds: Map<String, Bounds>,
    data: Data,
) {
    val subtypeDeclaration = subtype.getClassOrTypeParameterDeclaration()
    val supertypeDeclaration = supertype.getClassOrTypeParameterDeclaration()

    when (subtypeDeclaration) {
        is KSTypeParameter -> {
            bounds
                .getValue(
                    subtypeDeclaration,
                    isInNewParameterSet = !isSupertypeExposedThroughNewTypeParameters
                )
                .run {
                    variances.add(combineSumVariances(subtypeNestedVarianceModifier, supertypeNestedVarianceModifier))
                    add(supertype, true)
                }
        }

        is KSClassDeclaration -> {
            when (supertypeDeclaration) {

                is KSTypeParameter -> {
                    bounds
                        .getValue(
                            supertypeDeclaration,
                            isInNewParameterSet = isSupertypeExposedThroughNewTypeParameters
                        )
                        .run {
                            variances.add(
                                combineSumVariances(
                                    subtypeNestedVarianceModifier,
                                    supertypeNestedVarianceModifier
                                )
                            )
                            add(subtype, false)
                        }
                }

                is KSClassDeclaration -> {
                    val staredSubtype = supertypeDeclaration.asStarProjectedType()
                    val staredSupertype = subtypeDeclaration.asStarProjectedType()

                    if (!staredSupertype.isAssignableFrom(staredSubtype))
                        error("$supertypeDeclaration is not supertype of $subtypeDeclaration. This check may also be performed if the types should be equal")

                    val subtypeArgs =
                        if (subtypeDeclaration == supertypeDeclaration)
                            subtype.expandTypeAliases(data).arguments
                        else
                            (subtype.declaration as KSClassDeclaration)
                                .getAllSuperTypes() // TODO: optimize
                                .find { it.declaration == supertypeDeclaration }!!
                                .arguments

                    val supertypeArgs = supertype.expandTypeAliases(data).arguments

                    supertypeArgs.indices.forEach { idx ->
                        val supertypeArg = supertypeArgs[idx]
                        val subtypeArg = subtypeArgs[idx]

                        matchTypeArgs(
                            supertypeNestedVarianceModifier,
                            supertypeDeclaration.typeParameters[idx].variance,
                            supertypeArg,
                            subtypeNestedVarianceModifier,
                            subtypeDeclaration.typeParameters[idx].variance,
                            subtypeArg,
                            isSupertypeExposedThroughNewTypeParameters,
                            bounds,
                            data
                        )
                    }
                }

                else -> unreachableCode()
            }
        }

        else -> unreachableCode()
    }
}

private fun collectVariancesRecursive(
    typeToCollect: KSType,
    nestedVariance: Variance,
    isInNewParameterSet: Boolean,
    bounds: Map<String, Bounds>,
    data: Data,
) = collectVariancesRecursive0(
    typeToCollect.expandTypeAliases(data),
    nestedVariance,
    isInNewParameterSet,
    bounds,
    data
)

private fun collectVariancesRecursive0(
    typeToCollect: KSType,
    nestedVariance: Variance,
    isInNewParameterSet: Boolean,
    bounds: Map<String, Bounds>,
    data: Data,
) {
    when (val declaration = typeToCollect.declaration) {
        is KSClassDeclaration -> {
            typeToCollect.arguments.forEachIndexed { idx, it ->
                if (it.variance != Variance.STAR)
                    collectVariancesRecursive(
                        it.type!!.resolve(),
                        combineMultiplyVariances(
                            nestedVariance,
                            combineSumVariances(it.variance, declaration.typeParameters[idx].variance)
                        ),
                        isInNewParameterSet,
                        bounds,
                        data,
                    )
            }
        }

        is KSTypeParameter -> {
            bounds.getValue(declaration, isInNewParameterSet).variances.add(nestedVariance)
        }

        is KSTypeAlias -> unreachableCode() // we have already expanded all aliases

        else -> unreachableCode()
    }
}

private fun <V> Map<String, V>.getValue(parameter: KSTypeParameter, isInNewParameterSet: Boolean) =
    getValue((if (isInNewParameterSet) NEW_PREFIX else CURRENT_PREFIX) + parameter.name.asString())