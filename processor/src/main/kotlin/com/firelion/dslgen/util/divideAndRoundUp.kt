/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.util

/**
 * Divides one integer on another and rounds up the result.
 */
internal infix fun Int.divideAndRoundUp(divisor: Int) = (this + divisor - 1) / divisor