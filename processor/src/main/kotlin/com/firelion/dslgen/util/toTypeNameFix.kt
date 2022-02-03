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
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver

/**
 * Fixed version of [KSType.toTypeName].
 *
 * KSP doesn't actually provide source type arguments for [KSTypeAlias]
 * ([KSTypeAlias.typeParameters] are type arguments of aliased type, not alis itself),
 * and the original version expects them to be provided through an extra argument,
 * but it seems to be impossible to find correct arguments for resolved types.
 *
 * This version hasn't such extra argument and instead expands type aliases to aliased types.
 */
internal fun KSType.toTypeNameFix(
    typeParamResolver: TypeParameterResolver,
): TypeName {
    val type = when (val decl = declaration) {
        is KSClassDeclaration -> {
            decl.toClassName().withTypeArguments(arguments.map { it.toTypeNameFix(typeParamResolver) })
        }
        is KSTypeParameter -> typeParamResolver[decl.name.getShortName()]
        is KSTypeAlias -> {
            val extraResolver = if (decl.typeParameters.isEmpty()) {
                typeParamResolver
            } else {
                decl.typeParameters.toTypeParameterResolver(typeParamResolver, decl.name.asString())
            }
            val mappedArgs = arguments.map { it.toTypeNameFix(extraResolver) }

            val abbreviatedType = decl.type.resolve()
                .toTypeNameFix(extraResolver)
                .copy(nullable = isMarkedNullable)
                .rawType()
                .withTypeArguments(mappedArgs)

            /*return@when*/ abbreviatedType
        }
        else -> error("Unsupported type: $declaration")
    }

    return type.copy(nullable = isMarkedNullable)
}

/**
 * Copy of function from kotlinpoet-ksp
 * with [toTypeName] calls replaced with [toTypeNameFix] and no default argument value.
 */
internal fun KSTypeArgument.toTypeNameFix(
    typeParamResolver: TypeParameterResolver
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
private fun TypeName.rawType(): ClassName {
    return findRawType() ?: throw IllegalArgumentException("Cannot get raw type from $this")
}

/**
 * Copy of function from kotlinpoet-ksp.
 */
private fun TypeName.findRawType(): ClassName? {
    return when (this) {
        is ClassName -> this
        is ParameterizedTypeName -> rawType
        is LambdaTypeName -> {
            var count = parameters.size
            if (receiver != null) {
                count++
            }
            val functionSimpleName = if (count >= 23) {
                "FunctionN"
            } else {
                "Function$count"
            }
            ClassName("kotlin.jvm.functions", functionSimpleName)
        }
        else -> null
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

