/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.util.unreachableCode
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.TypeVariableName
import java.security.MessageDigest
import kotlin.experimental.and

/**
 * Half of identifiers length.
 */
private const val IDENTIFIER_SIZE_BYTES = 8

/**
 * Returns hash of a function signature.
 */
internal fun KSFunctionDeclaration.getUniqueIdentifier(): String = getSignature().getHash()

/**
 * Returns hash of a type parameter specification signature.
 */
internal fun getSpecificationUniqueIdentifier(
    newParameters: List<TypeVariableName>,
    returnTypeArgs: List<KSTypeArgument>,
    contextClassName: String,
) = ("$contextClassName with " + returnTypeArgs.getSignature(newParameters.asSequence().withIndex()
    .associate { it.value.name to it.index })).getHash()

/**
 * Returns signature of a function.
 */
private fun KSFunctionDeclaration.getSignature() =
    when (val parent = parentDeclaration) {
        null -> qualifiedName!!.asString()
        is KSClassDeclaration -> parent.qualifiedName!!.asString() + "/" + simpleName.asString()
        else -> unreachableCode()
    } + ":" + getTypeSignature()

/**
 * Returns type signature of a function.
 */
private fun KSFunctionDeclaration.getTypeSignature(): String {
    val typeParameters = typeParameters.asSequence().withIndex().associate { it.value.name.asString() to it.index }

    return this.typeParameters.joinToString(prefix = "<", separator = ";", postfix = ">") { parameter ->
        (if (parameter.isReified) "reified" else "") +
                parameter.bounds.joinToString(prefix = "<", separator = ";", postfix = ">") {
                    it.resolve().getSignature(typeParameters)
                }
    } +
            parameters.joinToString(prefix = "(", separator = ";", postfix = ")") {
                it.type.resolve().getSignature(typeParameters)
            } +
            returnType!!.resolve().getSignature(typeParameters)
}

/**
 * Returns signature of a type argument list.
 *
 * Uses [typeParameters] map of `parameter_name->parameter_index` to be irrespective to parameters' names.
 */
private fun List<KSTypeArgument>.getSignature(typeParameters: Map<String, Int>) =
    joinToString(prefix = "<", separator = ",", postfix = ">") {
        "${it.variance.label} ${it.type?.resolve()?.getSignature(typeParameters)}"
    }

/**
 * Returns signature of a type.
 *
 * Uses [typeParameters] map of `parameter_name->parameter_index` to be irrespective to parameters' names.
 */
private fun KSType.getSignature(typeParameters: Map<String, Int>): String =
    when (val declaration = declaration) {
        is KSClassDeclaration -> declaration.qualifiedName!!.asString()
        is KSTypeParameter -> "T${typeParameters[declaration.name.asString()]}"
        is KSTypeAlias -> declaration.type.resolve().getSignature(typeParameters)
        else -> unreachableCode()
    } + this.arguments.joinToString(prefix = "<", separator = ";", postfix = ">") {
        it.variance.toString() + " " + it.type?.resolve()!!.getSignature(typeParameters)
    }

/**
 * ThreadLocal SHA2-256 digest instance.
 */
private val hasherThreadLocal = ThreadLocal.withInitial {
    MessageDigest.getInstance("SHA-256")
}

/**
 * Returns hex hash of a string.
 */
private fun String.getHash(): String {
    val hasher = hasherThreadLocal.get()

    return hasher.digest(toByteArray()).toHexString()
}

/**
 * Cuts down and converts ByteArray to hex string
 */
private fun ByteArray.toHexString() = asList().subList(0, IDENTIFIER_SIZE_BYTES).joinToString(separator = "") {
    ((it.toInt() and 0xf0) ushr 4).toString(16) + (it and 0xf).toString(16)
}