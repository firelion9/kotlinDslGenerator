/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.util

import com.firelion.dslgen.generator.util.Data
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.*

internal class ExtensionKSValueParameter private constructor(
    override val name: KSName?,
    override val type: KSTypeReference,
    parentFunction: KSFunctionDeclaration,
) : KSValueParameter {
    override val parent: KSNode = parentFunction
    override val location: Location get() = parent.location

    override val annotations: Sequence<KSAnnotation> get() = emptySequence()

    override val origin: Origin get() = Origin.SYNTHETIC

    override val hasDefault: Boolean get() = false
    override val isCrossInline: Boolean get() = false
    override val isNoInline: Boolean get() = false
    override val isVal: Boolean get() = false
    override val isVar: Boolean get() = false
    override val isVararg: Boolean get() = false

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitValueParameter(this, data)
    }

    companion object Factory {

        fun receiversOf(function: KSFunctionDeclaration, data: Data): Sequence<KSValueParameter> = sequence {
            yieldAll(implicitReceiversOf(function, data))
            dispatchReceiverOf(function, data)?.let { yield(it) }
        }

        private fun dispatchReceiverOf(function: KSFunctionDeclaration, data: Data): KSValueParameter? =
            if (function.isConstructor()) null
            else if (function.extensionReceiver != null)
                ExtensionKSValueParameter(
                    data.getDispatchReceiverName(),
                    function.extensionReceiver!!,
                    function
                )
            else if (function.parentDeclaration is KSClassDeclaration)
                ExtensionKSValueParameter(
                    data.getDispatchReceiverName(),
                    (function.parentDeclaration as KSClassDeclaration).let { classDec ->
                        data.resolver.createKSTypeReferenceFromKSType(classDec.asStarProjectedType()) // TODO: use type parameters
                    },
                    function
                )
            else null

        private fun implicitReceiversOf(function: KSFunctionDeclaration, data: Data): Sequence<KSValueParameter> =
            if (function.isConstructor()) emptySequence()
            else if (function.parentDeclaration is KSClassDeclaration && function.extensionReceiver != null) sequenceOf(
                ExtensionKSValueParameter(
                    data.getImplicitReceiverName(0),
                    (function.parentDeclaration as KSClassDeclaration).let { classDec ->
                        data.resolver.createKSTypeReferenceFromKSType(classDec.asStarProjectedType()) // TODO: use type parameters
                    },
                    function
                )
            )
            else emptySequence()

        private fun Data.getDispatchReceiverName() = resolver.getKSNameFromString("dispatchReceiver")
        private fun Data.getImplicitReceiverName(idx: Int) = resolver.getKSNameFromString("implicitReceiver$idx")
    }
}