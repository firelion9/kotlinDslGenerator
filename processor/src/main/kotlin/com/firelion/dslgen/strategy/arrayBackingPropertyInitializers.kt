/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.strategy

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import java.util.*

object ArrayBackingPropertyInitializers {
    /**
     * [LinkedList], used as temporary storage for array DSL parameters.
     */
    private val LINKED_LIST = ClassName(LinkedList::class.java.packageName, LinkedList::class.java.simpleName)

    val LINKED_LIST_INITIALIZER = CodeBlock.of("%T()", LINKED_LIST)

    val MUTABLE_LIST_OF_INITIALIZER = CodeBlock.of("mutableListOf()")
}