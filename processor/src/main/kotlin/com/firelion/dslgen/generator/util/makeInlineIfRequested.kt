/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.GenerationParameters
import com.firelion.dslgen.generator.generation.NOTHING_TO_INLINE
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier

internal fun FunSpec.Builder.makeInlineIfRequested(generationParameters: GenerationParameters, suppressWarning: Boolean = true) = apply {
    if (generationParameters.makeInline) {
        addModifiers(KModifier.INLINE)
        if (suppressWarning) addAnnotation(NOTHING_TO_INLINE)
    }
}