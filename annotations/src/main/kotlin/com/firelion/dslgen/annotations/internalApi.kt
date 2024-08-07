/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.annotations

// @hardlink#004

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
@DslGeneratorInternalApi
fun callDefaultImplMarker() = Unit

/**
 * __DO NOT APPLY IT TO YOUR FILES__
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FILE)
@DslGeneratorInternalApi
annotation class GeneratedDsl

@RequiresOptIn(message = "This declaration is a part of internal DSL generator API. Do not call it explicitly")
@Retention(AnnotationRetention.BINARY)
annotation class DslGeneratorInternalApi
