/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import kotlin.test.Test

class AdvancedGenericFunctionTests {

    // TODO: replace with more meaningful tests
    @Test
    fun `raw test`() {
        compileWithKspAndRun(
            """
            import com.firelion.dslgen.annotations.GenerateDsl
            import com.firelion.dslgen.annotations.PropertyAccessor
            import com.firelion.dslgen.annotations.UseAlternativeConstruction
            import com.firelion.dslgen.annotations.UseDefaultConstructions

            @DslMarker
            @Retention(AnnotationRetention.BINARY)
            @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
            annotation class Dsl

            @GenerateDsl(Dsl::class)
            fun<T0> tester(@UseAlternativeConstruction(functionPackageName = "", functionName = "mapper", name = "active", functionReturnType = Map::class) map: Map<T0, Comparator<T0>>) = map.toString()
    
            fun<T1, C> mapper(vararg elements: Pair<T1, Comparator<C>>) = elements.toMap()
            @GenerateDsl(Dsl::class)
            
            fun main() {
                tester {
                    active {
                        element(1, Comparator.comparingInt { it })
                        element(2, Comparator.comparingInt { it })
                    }
                }

                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }
}