/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.util

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName


internal fun ClassName.withTypeArguments(arguments: List<TypeName>): TypeName = when {
    arguments.isEmpty() -> this
    else -> this.parameterizedBy(arguments)
}
