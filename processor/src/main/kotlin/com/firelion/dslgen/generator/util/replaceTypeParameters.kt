/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.logging
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter


/**
 * Replaces occurrences of type parameters using [mapping].
 */
internal fun KSType.replaceTypeParameters(
    mapping: Map<KSTypeParameter, KSType>,
    data: Data
): KSType {
    data.logger.logging { "replacing type parameters of $this with mapping $mapping" }

    return replaceTypeParameters0(mapping.mapKeys { it.key.name }, data).first
}

/**
 * Replaces occurrences of type parameters using [mapping].
 * Returns a pair of result type and boolean indicating if anything was changed.
 */
private fun KSType.replaceTypeParameters0(
    mapping: Map<KSName, KSType>,
    data: Data
): Pair<KSType, Boolean> =
    when (val dec = declaration) {

        is KSTypeParameter -> {
            data.logger.logging { "leaf type parameter $dec will be replaced with ${mapping[dec.name]}" }
            mapping[dec.name]?.to(true) ?: (this to false)
        }

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

            if (anythingChanged) data.logger.logging { "$arguments will be replaced with $newArguments" }
            else data.logger.logging { "$arguments wouldn't be changed" }

            if (anythingChanged) {
                replace(newArguments) to true
            } else {
                this to false
            }
        }
    }