/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.annotations.UseAlternativeConstruction
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val CONSTRUCTOR_NAME = "<init>"

/**
 * @see [UseAlternativeConstruction]
 */
internal fun resolveFunction(
    data: Data,
    expectedType: KSType,
    functionPackageName: String,
    functionClass: KSType,
    functionName: String,
    functionParameterTypes: List<KSType>,
    functionReturnType: KSType,
): KSFunctionDeclaration {
    val voidType = data.usefulTypes.ksVoid // Kotlin compiler replaces Nothing with Void during compilation

    val allFunctions =
        if (functionClass == voidType) {
            val fullName = if (functionPackageName.isEmpty()) functionName else "$functionPackageName.$functionName"

            data.resolver.getFunctionDeclarationsByName(
                data.resolver.getKSNameFromString(fullName),
                includeTopLevel = true
            )

        } else {
            (functionClass.declaration as KSClassDeclaration)
                .let {
                    if (functionName == CONSTRUCTOR_NAME) it.getConstructors()
                    else it.getAllFunctions() // TODO: use getDeclaredFunctions?
                }
                .filter {
                    it.simpleName.getShortName() == functionName
                }
        }

    val filteredReturnTypeFunctions =
        if (functionReturnType == voidType) {
            allFunctions.filter {
                expectedType.isAssignableFrom(it.returnType!!.resolve())
            }
        } else {
            allFunctions.filter {
                functionReturnType == it.returnType!!.resolve().starProjection()
            }
        }

    val filterSignatureFunctions =
        if (functionParameterTypes.isEmpty()) {
            filteredReturnTypeFunctions
        } else {
            filteredReturnTypeFunctions.filterParameterTypes(functionParameterTypes, data)
        }

    val resFunctions = filterSignatureFunctions.toList()

    when (resFunctions.size) {
        0 -> error("no matching function")

        1 -> return resFunctions.first()

        else -> {
            error("there are more then one matching function:\n" + resFunctions.joinToString { it.toString() })
        }
    }
}

private fun Sequence<KSFunctionDeclaration>.filterParameterTypes(
    functionParameterTypes: List<KSType>,
    data: Data,
) = filter { candidateFunction ->

    val extensionReceiverCount = if (candidateFunction.extensionReceiver != null) 1 else 0
    val extensionReceivers = buildList(extensionReceiverCount) {
        candidateFunction.extensionReceiver?.let { add(it) }
    }

    val candidateArgCount = extensionReceiverCount + candidateFunction.parameters.size

    when {
        candidateArgCount != functionParameterTypes.size
        -> false

        extensionReceivers.indices.any { idx -> extensionReceivers[idx].resolve() != functionParameterTypes[idx] }
        -> false

        candidateFunction.parameters.indices.any { idx ->
            candidateFunction.parameters[idx].resolveActualType(data) != functionParameterTypes[extensionReceiverCount + idx]
        } -> false

        else -> true
    }
}

