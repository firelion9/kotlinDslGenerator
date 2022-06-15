/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.firelion.dslgen.util.unreachableCode
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.MemberName

/**
 * Returns name of this [KSDeclaration] as [MemberName]:
 * parent class name for constructors and self name for other declarations.
 */
internal fun KSDeclaration.memberName(): MemberName =
    if (this is KSFunctionDeclaration && isConstructor())
        (parentDeclaration ?: unreachableCode()).memberName()
    else
        qualifiedName?.let { MemberName(it.getQualifier(), it.getShortName()) }
            ?: MemberName("", simpleName.getShortName())