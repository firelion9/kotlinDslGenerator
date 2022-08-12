/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock

internal fun CodeBlock.Builder.addFunctionCall(
    function: KSFunctionDeclaration,
    parameters: Sequence<String>,
): CodeBlock.Builder = apply {
    val receiverCount = function.receiverCount()

    val parametersWithImplicitParameters = sequence {
        if (function.isObjectFunction()) yield(function.parentDeclaration!!.memberName().toString())
        yieldAll(parameters)
    }

    val iterator = parametersWithImplicitParameters.iterator()

    if (receiverCount >= 2) addImplicitReceivers(receiverCount - 1, iterator)

    if (receiverCount >= 1) add("${iterator.next()}.")

    add("%N(⇥\n", function.memberName())

    add(iterator.asSequence().joinToString(separator = ",\n"))
    add("\n⇤)")

    if (receiverCount >= 2)
        repeat(receiverCount - 1) {
            add("⇤}\n")
        }
}

private fun CodeBlock.Builder.addImplicitReceivers(count: Int, parameters: Iterator<String>): CodeBlock.Builder =
    apply {
        repeat(count) {
            add("with(%N) {\n⇥", parameters.next())
        }
    }

private fun KSFunctionDeclaration.receiverCount(): Int =
    (if (hasDispatchReceiver()) 1 else 0) + (if (extensionReceiver != null) 1 else 0)

private fun KSFunctionDeclaration.hasDispatchReceiver(): Boolean = !isConstructor() && parentDeclaration is KSClassDeclaration

private fun KSFunctionDeclaration.isObjectFunction(): Boolean =
    (parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.OBJECT