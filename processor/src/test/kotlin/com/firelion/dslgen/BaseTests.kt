/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import kotlin.test.Test

internal class BaseTests {
    @Test
    fun `test DSL for function with primitive arguments`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun sum(a: Int, b: Int) = a + b
            
            @GenerateDsl(Dsl::class)
            fun sum2(a: Int, b: Float) = a.toFloat() + b
            
            fun main() {
                assertEquals(3, sum { a(1); b(2) })
                assertEquals(4.2f, sum2 { a(4); b(0.2f) })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test DSL for function with class arguments`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            class MyClass(val x: Int, val y: String)
            
            @GenerateDsl(Dsl::class)
            fun format(a: Int, b: MyClass) = "" + a + ':' + b.x + ':' + b.y
            
            fun main() {
                assertEquals("1:2:hello", format { 
                    a(1)  
                    b {
                        x(2)
                        y("hello")
                    }
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test DSL for function with specified generic class arguments`() {
        compileWithKspAndRun(
            """
            import generated.kotlin.*
            $BASE_FILE_HEADER
            
            class MyClass(val x: Int, val y: String)
            
            @GenerateDsl(Dsl::class)
            fun format(a: Int, b: Pair<Int, String>) = "" + a + ':' + b.first + ':' + b.second
            
            fun main() {
                assertEquals("1:2:hello", format { 
                    a(1)  
                    b {
                        first(2)
                        second("hello")
                    }
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test DSL for generic function`() {
        compileWithKspAndRun(
            """
            import generated.kotlin.*
            $BASE_FILE_HEADER
            
            class MyClass(val x: Int, val y: String)
            
            @GenerateDsl(Dsl::class)
            fun<A, B> format(a: Int, b: Pair<A, B>) = "" + a + ':' + b.first + ':' + b.second
            
            fun main() {
                assertEquals("1:2:hello", format { 
                    a(1)  
                    b {
                        first(2)
                        second("hello")
                    }
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test transitive DSL for function with specified generic arguments`() {
        compileWithKspAndRun(
            """
            import generated.kotlin.*
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun format(a: Int, b: Pair<Pair<Int, Int>, String>) = 
                "" + a + ':' + b.first.first + ':' + b.first.second + ':' + b.second
            
            fun main() {
                assertEquals("1:2:3:hello", format { 
                    a(1)  
                    b {
                        first {
                            first(2)
                            second(3)
                        }
                        second("hello")
                    }
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test element construction`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun format(args: Array<String>) = 
                args.joinToString()
            
            fun main() {
                assertEquals("Hello, world!", format {
                    element("Hello")
                    element("world!")
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test lambda parameters`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class, functionName = "exec")
            fun execute(lambda: () -> Unit) = lambda()
            
            fun main() {
                var a = 0
                exec {
                    lambda {
                        a += 1
                    }
                }
            
                assertEquals(1, a)
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }
}