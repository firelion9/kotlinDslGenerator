/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias

/**
 * Resolves aliased class declaration of (possibly nested) type alias.
 */
internal tailrec fun KSType.getClassDeclaration(): KSClassDeclaration? =
    when (val dec = declaration) {
        is KSTypeAlias -> dec.type.resolve().getClassDeclaration()
        is KSClassDeclaration -> dec
        else -> null
    }