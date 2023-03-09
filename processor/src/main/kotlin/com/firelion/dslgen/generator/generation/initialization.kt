/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */



package com.firelion.dslgen.generator.generation

/*
 * functions in this file are working with "index" - index of parameter in function call
 * and initialization info - integers representing bit flags for each argument in function call,
 * where "0" means "parameter is passed"
 * and "1" means "parameter is not passed and will be replaced with default value before executing function"
 *
 * Note: "initialization info" is not standard term (and I don't really know how kotlin compiler call this thing).
 */
import com.firelion.dslgen.generator.util.Data
import com.squareup.kotlinpoet.CodeBlock

/**
 * Expression to check parameter with specified [index] against initialization.
 *
 * @param [name] is used only to generate error message
 */
internal fun checkInitialization(index: Int, name: String, data: Data) =
    CodeBlock.of(
        "require(%N and %L == 0) { %S }\n",
        data.namingStrategy.initializationInfoName(index / Int.SIZE_BITS),
        1 shl (index % Int.SIZE_BITS),
        "backing property $name hasn't been initialized",
    )

/**
 * Expression to check parameter with specified [index] against no initialization.
 *
 * @param [name] is used only to generate error message
 */
internal fun checkNoInitialization(index: Int, name: String, data: Data) =
    CodeBlock.of(
        "require(%N and %L != 0) { %S }\n",
        data.namingStrategy.initializationInfoName(index / Int.SIZE_BITS),
        1 shl (index % Int.SIZE_BITS),
        "backing property $name has been already initialized",
    )

/**
 * Expression to initialize parameter at specified [index].
 */
internal fun initialize(index: Int, data: Data) =
    CodeBlock.builder()
        .addNamed(
            "%info:N = %info:N and %mask:L\n",
            mapOf(
                "info" to data.namingStrategy.initializationInfoName(index / Int.SIZE_BITS),
                "mask" to (1 shl (index % Int.SIZE_BITS)).inv()
            )
        )
        .build()