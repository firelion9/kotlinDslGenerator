/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.TypeName

/**
 * Returns `true` for jvm primitive types and `false` otherwise.
 */
internal fun KSType.isPrimitive() = nullability == Nullability.NOT_NULL &&
        when (this.declaration.qualifiedName?.asString()) {
            "kotlin.Boolean", "kotlin.Byte", "kotlin.UByte", "kotlin.Short", "kotlin.USHort", "kotlin.Char",
            "kotlin.Int", "kotlin.UInt", "kotlin.Float", "kotlin.Long", "kotlin.ULong", "kotlin.Double",
            -> true
            else -> false
        }

/**
 * Returns `true` for Array<T> and jvm primitive array types, otherwise returns `false`.
 */
internal fun KSType.isArrayType(data: Data) = when {
    data.usefulTypes.ksArray.isAssignableFrom(this) -> true
    this == data.usefulTypes.ksBooleanArray -> true
    this == data.usefulTypes.ksByteArray -> true
    this == data.usefulTypes.ksCharArray -> true
    this == data.usefulTypes.ksShortArray -> true
    this == data.usefulTypes.ksIntArray -> true
    this == data.usefulTypes.ksFloatArray -> true
    this == data.usefulTypes.ksLongArray -> true
    this == data.usefulTypes.ksDoubleArray -> true

    else -> false
}

/**
 * Returns array element type for array types and `null` for others.
 */
internal fun KSType.arrayElementTypeOrNull(data: Data) = when {
    data.usefulTypes.ksArray.isAssignableFrom(this) -> arguments.first().type!!.resolve()
    this == data.usefulTypes.ksBooleanArray -> data.resolver.builtIns.booleanType
    this == data.usefulTypes.ksByteArray -> data.resolver.builtIns.byteType
    this == data.usefulTypes.ksCharArray -> data.resolver.builtIns.charType
    this == data.usefulTypes.ksShortArray -> data.resolver.builtIns.shortType
    this == data.usefulTypes.ksIntArray -> data.resolver.builtIns.intType
    this == data.usefulTypes.ksFloatArray -> data.resolver.builtIns.floatType
    this == data.usefulTypes.ksLongArray -> data.resolver.builtIns.longType
    this == data.usefulTypes.ksDoubleArray -> data.resolver.builtIns.doubleType

    else -> null
}

/**
 * Returns nullable copy of non-primitive types or passed primitive type.
 */
internal fun KSType.backingPropertyType(): KSType =
    if (isPrimitive()) this
    else makeNullable()

/**
 * Returns initialization expression for DSL context class backing field for passed type.
 *
 * @see [backingPropertyType]
 */
internal fun KSType.backingPropertyInitializer() =
    if (nullability == Nullability.NULLABLE) "null"
    else when (this.declaration.qualifiedName!!.asString()) {
        "kotlin.Boolean" -> "false"
        "kotlin.Byte" -> "0.toByte()"
        "kotlin.UByte" -> "0.toUByte()"
        "kotlin.Short" -> "0.toShort()"
        "kotlin.UShort" -> "0.toUShort()"
        "kotlin.Char" -> "0.toChar()"
        "kotlin.Int" -> "0"
        "kotlin.UInt" -> "0u"
        "kotlin.Float" -> "0f"
        "kotlin.Long" -> "0L"
        "kotlin.ULong" -> "0uL"
        "kotlin.Double" -> "0.0"
        else -> "null"
    }

/**
 * Returns postfix expression which would cast passed type to required [selfTypeName].
 */
internal fun KSType.castFromBackingFieldType(selfTypeName: TypeName) =
    when {
        declaration is KSTypeParameter ->
            "as $selfTypeName"

        nullability == Nullability.NOT_NULL && !isPrimitive() ->
            "!!"

        else ->
            ""
    }

/**
 * Returns `true` is expression returned by [castFromBackingFieldType] is safe (checked).
 */
internal fun KSType.isCastFromBackingFieldTypeSafe() =
    declaration !is KSTypeParameter

internal fun Sequence<KSAnnotation>.filterMatchingType(type: KSType): Sequence<KSAnnotation> = filter {
    it.shortName.getShortName() == type.declaration.simpleName.getShortName()
            && type.isAssignableFrom(it.annotationType.resolve())
}

internal fun Sequence<KSAnnotation>.findMatchingTypeOrNull(type: KSType): KSAnnotation? =
    filterMatchingType(type).firstOrNull()
