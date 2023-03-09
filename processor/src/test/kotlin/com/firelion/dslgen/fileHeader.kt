/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import org.intellij.lang.annotations.Language

@Language("kotlin")
internal const val BASE_FILE_HEADER: String = """
            import com.firelion.dslgen.annotations.*
            import kotlin.test.*
            
            
            @DslMarker
            @Retention(AnnotationRetention.BINARY)
            @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
            annotation class Dsl
            """
