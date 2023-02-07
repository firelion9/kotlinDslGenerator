/*
 * Copyright (c) 2022-2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.util

/**
 * Marks an expression as unreachable.
 * It is not checked anyway by the compiler and intended only to improve code readability.
 */
internal fun unreachableCode(): Nothing = throw AssertionError("this code couldn't be reached")

/**
 * Marks an expression as one that _should not_ be reached.
 * It is less strict assertion than [unreachableCode]:
 * [shouldNotBeReached] may only be reached if the program has bugs.
 */
internal fun shouldNotBeReached(): Nothing =
    throw IllegalStateException("this code couldn't be reached")

