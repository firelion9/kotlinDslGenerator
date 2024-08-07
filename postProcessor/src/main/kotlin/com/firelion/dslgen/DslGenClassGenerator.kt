/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import org.jetbrains.kotlin.backend.jvm.extensions.ClassGenerator
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.org.objectweb.asm.MethodVisitor

class DslGenClassGenerator(private val parent: ClassGenerator) : ClassGenerator by parent {
    override fun newMethod(
        declaration: IrFunction?,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val parent = parent.newMethod(declaration, access, name, desc, signature, exceptions)
        return if (declaration?.name?.asString() == DslGenConstants.CREATE_FUNCTION_NAME && declaration.valueParameters.isEmpty()) {
            PostProcessingMethodVisitor(parent)
        } else {
            parent
        }
    }
}
