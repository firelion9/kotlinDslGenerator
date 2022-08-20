/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.logging
import com.google.devtools.ksp.symbol.*

internal fun KSType.expandTypeAliases(data: Data): KSType =
    when (val decl = declaration) {
        is KSTypeParameter -> this

        is KSClassDeclaration -> {
            val newArguments = arguments.map {
                data.resolver.getTypeArgument(
                    data.resolver.createKSTypeReferenceFromKSType(it.type!!.resolve().expandTypeAliases(data)),
                    it.variance
                )
            }

            replace(newArguments)
        }

        is KSTypeAlias -> {
            val newArguments = arguments.map {
                data.resolver.getTypeArgument(
                    data.resolver.createKSTypeReferenceFromKSType(it.type!!.resolve().expandTypeAliases(data)),
                    it.variance
                )
            }

            val argMap = decl.typeParameters.asSequence().mapIndexed { index, ksTypeParameter ->
                ksTypeParameter to newArguments[index].type!!.resolve()
            }.toMap()

            decl.type.resolve().replaceTypeParameters(argMap, data).expandTypeAliases(data)
        }

        else -> error("unknown type ${decl::class}")
    }

internal tailrec fun KSType.resolveEndTypeArguments(data: Data): List<KSTypeArgument> =
    when (val decl = declaration) {
        is KSClassDeclaration -> arguments

        is KSTypeParameter -> emptyList()

        is KSTypeAlias -> {
            val typeArgs = decl.typeParameters.asSequence().mapIndexed { index, ksTypeParameter ->
                ksTypeParameter to arguments[index].type!!.resolve()
            }.toMap()

            data.logger.logging { "type alias type parameter map is $typeArgs" }
            data.logger.logging { ("type alias type is ${decl.type.resolve()}") }

            decl.type.resolve().replaceTypeParameters(typeArgs, data).resolveEndTypeArguments(data)
        }

        else -> error("unknown type ${decl::class}")
    }