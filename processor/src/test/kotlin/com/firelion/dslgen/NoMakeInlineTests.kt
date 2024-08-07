/*
 * Copyright (c) 2023-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import kotlin.test.Test

internal class NoMakeInlineTests {
    @Test
    fun `test `() {
        compileWithKspAndRun(
            """
            import generated.kotlin.*
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class, makeInline = false)
            fun format(a: Int, b: Array<Pair<Int, String>>) = 
                "" + a + ':' + b.joinToString(":") { "" + it.first + ";" + it.second }
            
            fun main() {
                assertEquals("1:2;hi:3;bye", format { 
                    a(1)  
                    element {
                        first(2)
                        second("hi")
                    }
                    element {
                        first(3)
                        second("bye")
                    }
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }
}