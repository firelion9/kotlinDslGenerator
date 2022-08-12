/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.util

/*
 * If not specified, "copy of function from kotlinpoet-ksp" means
 * a copy of internal util function from com.squareup:kotlinpoet-ksp:1.10.2
 * https://github.com/square/kotlinpoet/tree/da9c33b45c4f8fb6853c92bbfe78fc38481b0a31/interop/ksp
 * with no semantics changes and (maybe) code reformat
 */

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Fixed version of [KSType.toTypeName].
 *
 * In older versions KSP didn't actually provide source type arguments for [KSTypeAlias]
 * ([KSType.arguments] were type arguments of aliased type, not alias itself).
 * The original version of [KSType.toTypeName] expects them to be provided through an extra argument,
 * but it was impossible to find correct arguments for resolved types.
 *
 * In KSP 1.0.6 (or maybe even earlier) [KSTypeAlias.typeParameters] become correct arguments,
 * but original version of [KSType.toTypeName] expects them to be arguments of abbreviated type,
 * so it still works incorrectly.
 *
 * This version hasn't extra argument and uses [KSType.arguments] instead.
 */
internal fun KSType.toTypeNameFix(
    typeParamResolver: TypeParameterResolver,
): TypeName {
    val type = when (val decl = declaration) {

        is KSClassDeclaration -> {
            decl.toClassName().withTypeArguments(arguments.map { it.toTypeNameFix(typeParamResolver) })
        }

        is KSTypeParameter -> typeParamResolver[decl.name.getShortName()]

        is KSTypeAlias -> ClassName(decl.packageName.asString(), decl.simpleName.asString())
            .withTypeArguments(arguments.map { it.toTypeNameFix(typeParamResolver) })

        else -> error("Unsupported type: $declaration")
    }

    return type.copy(nullable = isMarkedNullable)
}

/**
 * Copy of function from kotlinpoet-ksp
 * with [toTypeName] calls replaced with [toTypeNameFix] and no default argument value.
 */
internal fun KSTypeArgument.toTypeNameFix(
    typeParamResolver: TypeParameterResolver,
): TypeName {
    val typeName = type?.resolve()?.toTypeNameFix(typeParamResolver) ?: return STAR
    return when (variance) {
        Variance.COVARIANT -> WildcardTypeName.producerOf(typeName)
        Variance.CONTRAVARIANT -> WildcardTypeName.consumerOf(typeName)
        Variance.STAR -> STAR
        Variance.INVARIANT -> typeName
    }
}

/**
 * Copy of function from kotlinpoet-ksp.
 */
private fun ClassName.withTypeArguments(arguments: List<TypeName>): TypeName {
    return if (arguments.isEmpty()) {
        this
    } else {
        this.parameterizedBy(arguments)
    }
}

