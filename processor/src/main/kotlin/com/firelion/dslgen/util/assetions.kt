/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.util

/**
 * Marks an expression as unreachable.
 * It is not checked anyway by the compiler and intended only to improve code readability.
 */
internal fun unreachableCode(): Nothing = throw AssertionError("this code couldn't be reached")
