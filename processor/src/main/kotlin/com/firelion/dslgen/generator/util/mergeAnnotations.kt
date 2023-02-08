/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.generator.generation.SUPPRESS
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec

/**
 * Merges annotations in [FunSpec.Builder].
 *
 * * Merges multiple @[Suppress] into one
 */
internal fun FunSpec.Builder.mergeAnnotations() = apply {
    val suppress = annotations.asSequence().filter { it.typeName == SUPPRESS }.flatMap { it.members }.toSet()

    if (suppress.isNotEmpty()) {
        annotations.removeAll { it.typeName == SUPPRESS }
        annotations.add(
            AnnotationSpec.builder(SUPPRESS)
                .apply {
                    suppress.forEach { addMember(it) }
                }
                .build()
        )
    }
}