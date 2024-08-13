/*
 * Copyright (c) 2023-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import kotlin.test.Ignore
import kotlin.test.Test

@Ignore // FIXME: compile testing is not working with dslgen plugin due to kotlin version conflicts
internal class WithDefaultTests {
    @Test
    fun `test default parameters`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun sum(a: Int, b: Int, c: Int = 4) = a + b + c
            
            fun main() {
                val s = sum {
                    a(10)
                    b(5)
                }
            
                assertEquals(19, s)
            
                println("Generated method executed successfully")
            }
            """.trimIndent(),
            withPlugin = true
        )
    }

    @Test
    fun `test default parameter override`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun sum(a: Int, b: Int, c: Int = 4) = a + b + c
            
            fun main() {
                val s = sum {
                    a(10)
                    b(5)
                    c(1)
                }
            
                assertEquals(16, s)
            
                println("Generated method executed successfully")
            }
            """.trimIndent(),
            withPlugin = true
        )
    }

    @Test
    fun `test required parameters are required`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun sum(a: Int, b: Int, c: Int = 4) = a + b + c
            
            fun main() {
                assertFails {
                    val s = sum {
                        a(10)
                    }
                }
                
            
                println("Generated method executed successfully")
            }
            """.trimIndent(),
            withPlugin = true
        )
    }

    @Test
    fun `test nullable default parameters`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun makePair(a: String?, b: String? = null) = a to b
            
            fun main() {
                val par = makePair {
                    a(null)
                }
                
                assertEquals(null to null, par)
            
                println("Generated method executed successfully")
            }
            """.trimIndent(),
            withPlugin = true
        )
    }
}
