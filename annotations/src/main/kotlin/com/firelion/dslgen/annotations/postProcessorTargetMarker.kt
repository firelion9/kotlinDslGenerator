/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.annotations

/**
 * __DO NOT CALL IT DIRECTLY FROM YOUR CODE__
 *
 * When *The Kotlin Dsl Generator* is used with gradle plugin,
 * plugin would perform extra post-processing tasks after compilation:
 * it would remove this function call and any !! assertions
 * (`DUP`; `INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkNotNull(Ljava/lang/Object;)V`; bytecode instructions)
 * between next function call (`INVOKE*`; bytecode instruction),
 * put initialization info (using GETFIELD and the last GETFIELD owner argument) and one `null` onto stack,
 * and replace the next call with function with its default variant
 * (+initialization info and Object args, +`$default` suffix to name for functions
 * and +initialization info and DefaultConstructorMarker args for constructors).
 */
fun callDefaultImplMarker() = Unit