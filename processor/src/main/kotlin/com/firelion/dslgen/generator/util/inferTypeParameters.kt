/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.util.unreachableCode
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.*

/**
 * TODO: docs are not valid more
 *
 * Infers values of [newTypeParameters] based on assumption
 * that [actualReturnType] should be assigned to [expectedReturnType].
 *
 * @return pair of inferred types and extra type parameters.
 */
internal fun inferTypeParameters(
    currentTypeParameters: List<KSTypeParameter>,
    expectedReturnType: KSType,
    newTypeParameters: List<KSTypeParameter>,
    actualValueParameterTypes: List<KSType>,
    actualReturnType: KSType,
    data: Data,
): Pair<Map<KSTypeParameter, KSType>, List<KSTypeParameter>> {
    class Bounds(val lowerBounds: MutableList<KSType>, val upperBounds: MutableList<KSType>) {
        fun add(bound: KSType, isUpper: Boolean) =
            (if (isUpper) upperBounds else lowerBounds)
                .add(bound)
    }

    val bounds = Array(
        currentTypeParameters.size + newTypeParameters.size
    ) { Bounds(arrayListOf(), arrayListOf()) }

    val typeParameterIndexByTypeParameter = (currentTypeParameters.asSequence() + newTypeParameters.asSequence())
        .mapIndexed { idx, it -> it to idx }.toMap()

    fun matchRecursive(expectedType: KSType, actualType: KSType) {
        val actualDeclaration = actualType.getClassOrTypeParameterDeclaration()
        val expectedDeclaration = expectedType.getClassOrTypeParameterDeclaration()


        when (actualDeclaration) {
            is KSTypeParameter -> {
                bounds[typeParameterIndexByTypeParameter.getValue(actualDeclaration)]
                    .add(expectedType, true)
            }

            is KSClassDeclaration -> {
                when (expectedDeclaration) {

                    is KSTypeParameter -> {
                        bounds[typeParameterIndexByTypeParameter.getValue(expectedDeclaration)]
                            .add(actualType, false)
                    }

                    is KSClassDeclaration -> {
                        val eType = expectedDeclaration.asStarProjectedType()
                        val aType = actualDeclaration.asStarProjectedType()

                        if (!aType.isAssignableFrom(eType))
                            error("can't project $actualReturnType to $expectedReturnType: $expectedDeclaration can't be assigned to $actualDeclaration")

                        val actualArgs =
                            if (actualDeclaration == expectedDeclaration)
                                actualType.arguments
                            else
                                (actualType.declaration as KSClassDeclaration)
                                    .getAllSuperTypes() // TODO: optimize
                                    .find { it.declaration == expectedDeclaration }!!
                                    .arguments

                        val expectedArgs = expectedType.arguments

                        expectedType.arguments.indices.forEach { idx ->
                            matchRecursive(expectedArgs[idx].type!!.resolve(), actualArgs[idx].type!!.resolve())
                            // TODO: Do we loose important information when ignore variance modifiers?
                        }
                    }

                    else -> unreachableCode()
                }
            }

            else -> unreachableCode()
        }
    }

    matchRecursive(expectedReturnType.expandTypeAliases(data), actualReturnType.expandTypeAliases(data))

    // TODO this solution works only for simple causes

//    TODO: those bounds should be used in the algorithm, but they are harmful for current naive realization
//    typeParameterIndexByTypeParameter.forEach { (param, idx) ->
//        param.bounds.forEach { bound ->
//            bounds[idx].add(bound.resolve(), true)
//        }
//    }

    val inferredTypes = mutableMapOf<KSTypeParameter, KSType>()
    val newParameters = mutableListOf<KSTypeParameter>()

    newTypeParameters.forEach { param ->
        val bds = bounds[typeParameterIndexByTypeParameter.getValue(param)]

        if (bds.upperBounds.isEmpty()) {
            newParameters.add(param)
        } else {
            inferredTypes[param] = bds.upperBounds.first()
        }
    }

    return inferredTypes to newParameters
}
