/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import org.intellij.lang.annotations.Language
import kotlin.test.Test

@Language("kotlin")
private const val BASE_FILE_HEADER: String = """
            import com.firelion.dslgen.annotations.*
            import kotlin.test.*
            
            
            @DslMarker
            @Retention(AnnotationRetention.BINARY)
            @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
            annotation class Dsl
            """

internal class ModifierAnnotationTests {
    @Test
    fun `test UseAlternativeConstruction`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            // fake construction
            fun constructor(map: Map<String, String>) = map.toString()
            
            // real construction 
            fun constructor(str: String) = str.toInt()          
            
            @GenerateDsl(Dsl::class)
            fun format(
                @UseAlternativeConstruction(isElementConstruction = true, functionPackageName = "", functionName = "constructor", name = "add") arr: Array<Int>,
            ) = arr.joinToString()
            
            fun main() {
                assertEquals("3, 14, 15, 92, 6, 5", format {
                    add { str("3") }
                    add { str("14") }
                    add { str("15") }
                    add { str("92") }
                    add { str("6") }
                    add { str("5") }
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test UseDefaultConstruction`() {
        compileWithKspAndRun(
            """
            $BASE_FILE_HEADER
            
            @GenerateDsl(Dsl::class)
            fun format(
                @UseDefaultConstructions(usePropertyLikeAccessor = PropertyAccessor.GETTER_AND_SETTER) a: String,
                @UseDefaultConstructions(usePropertyLikeAccessor = PropertyAccessor.GETTER_AND_SETTER) b: String,
            ) = a + ':' + b
            
            fun main() {
                assertEquals("hi:hi-fi", format {
                    a = "hi"
                    b = a + "-fi"
                })
            
                println("Generated method executed successfully")
            }
            """.trimIndent()
        )
    }
}