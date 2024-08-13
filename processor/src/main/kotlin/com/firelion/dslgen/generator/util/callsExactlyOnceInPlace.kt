/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.generator.generation.CONTRACT_NAME
import com.firelion.dslgen.generator.generation.EXACTLY_ONCE_NAME
import com.firelion.dslgen.generator.generation.EXPERIMENTAL_CONTRACTS_OPT_IN
import com.squareup.kotlinpoet.FunSpec


internal fun FunSpec.Builder.callsExactlyOnceInPlace(lambdaName: String): FunSpec.Builder = apply {
    addAnnotation(EXPERIMENTAL_CONTRACTS_OPT_IN)
    addCode(
        """%M {
        |   callsInPlace(%N, %M)
        |}
        |""".trimMargin(),
        CONTRACT_NAME,
        lambdaName,
        EXACTLY_ONCE_NAME
    )
}
