/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter


/**
 * Replaces occurrences of type parameters using [mapping].
 */
internal fun KSType.replaceTypeParameters(
    mapping: Map<KSTypeParameter, KSType>,
    data: Data
): KSType = replaceTypeParameters0(mapping, data).first

/**
 * Replaces occurrences of type parameters using [mapping].
 * Returns a pair of result type and boolean indicating if anything was changed.
 */
private fun KSType.replaceTypeParameters0(
    mapping: Map<KSTypeParameter, KSType>,
    data: Data
): Pair<KSType, Boolean> =
    when (val dec = declaration) {

        is KSTypeParameter -> mapping[dec]?.to(true) ?: (this to false)

        else -> {
            var anythingChanged = false

            val newArguments = arguments.map { argument ->

                data.resolver.getTypeArgument(

                    argument.type!!.resolve().replaceTypeParameters0(mapping, data)
                        .let { (type, changed) ->
                            anythingChanged = anythingChanged or changed
                            type
                        }
                        .let { data.resolver.createKSTypeReferenceFromKSType(it) },

                    argument.variance
                )
            }

            if (anythingChanged) {
                replace(newArguments) to true
            } else {
                this to false
            }
        }
    }