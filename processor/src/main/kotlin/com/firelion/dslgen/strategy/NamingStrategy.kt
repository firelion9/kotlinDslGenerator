/*
 * Copyright (c) 2023 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.strategy


private const val INITIALIZATION_INFO_PREFIX = "\$initializationInfo\$"

internal interface NamingStrategy {
    fun specificationFileName(
        uid: String,
        baseContextClassName: String,
    ): String

    fun dslContextClassName(
        uid: String,
        exitFunctionName: String,
    ): String

    fun dslFileName(
        dslUid: String,
        contextClassName: String,
        exitFunctionName: String,
    ): String

    fun builderLambdaName(forParameterName: String?): String

    fun elementAdderName(parameterName: String): String

    fun backingPropertyName(parameterName: String): String

    fun recoverParameterName(backingPropertyName: String): String


    // @hardlink#002
    fun isInitializationInfoProperty(propertyName: String): Boolean =
        propertyName.startsWith(INITIALIZATION_INFO_PREFIX)

    // @hardlink#002
    fun initializationInfoName(index: Int): String = "$INITIALIZATION_INFO_PREFIX$index"

    val createFunctionName: String

    object Legacy : NamingStrategy by Default {
        override fun elementAdderName(parameterName: String): String = "element"
    }

    object Default : NamingStrategy {
        override fun specificationFileName(uid: String, baseContextClassName: String): String =
            "\$Dsl\$Specification\$$uid"

        override fun dslContextClassName(uid: String, exitFunctionName: String): String =
            "\$Context\$$uid"

        override fun dslFileName(dslUid: String, contextClassName: String, exitFunctionName: String): String =
            "\$Dsl\$$dslUid"

        override fun builderLambdaName(forParameterName: String?): String = "builder"

        override fun elementAdderName(parameterName: String): String = "${parameterName}Element"

        override fun backingPropertyName(parameterName: String): String = "\$\$$parameterName\$\$"

        override fun recoverParameterName(backingPropertyName: String): String =
            backingPropertyName.removeSurrounding("\$\$")

        override val createFunctionName: String get() = "\$create\$"
    }
}