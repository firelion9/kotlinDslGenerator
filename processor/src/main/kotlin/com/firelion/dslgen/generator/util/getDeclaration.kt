/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.util.unreachableCode
import com.google.devtools.ksp.symbol.*

/**
 * Resolves aliased class declaration of (possibly nested) type alias.
 */
internal fun KSType.getClassDeclaration(): KSClassDeclaration? = getClassOrTypeParameterDeclaration() as? KSClassDeclaration

/**
 * Resolves aliased class or type parameter declaration of (possibly nested) type alias.
 */
internal tailrec fun KSType.getClassOrTypeParameterDeclaration(): KSDeclaration =
    when (val dec = declaration) {
        is KSTypeAlias -> dec.type.resolve().getClassOrTypeParameterDeclaration()
        is KSClassDeclaration -> dec
        is KSTypeParameter -> dec
        else -> unreachableCode()
    }