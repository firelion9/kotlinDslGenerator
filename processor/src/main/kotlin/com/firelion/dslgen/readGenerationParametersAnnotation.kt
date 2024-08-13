/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.firelion.dslgen.util.processingException
import com.firelion.dslgen.util.unreachableCode
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName

/**
 * `@GenerateDsl` representation.
 */
internal data class GenerationParameters(
    val markerClass: KSType,
    val functionName: String?,
    val contextClassName: String,
    val monoParameter: Boolean,
    val makeInline: Boolean,
) {
    val dslMarker by lazy { AnnotationSpec.builder(markerClass.toClassName()).build() }
}

/**
 * Reads `@GenerateDsl` from a function, verifies them
 * and applies default values to unspecified annotation arguments.
 */
internal fun readGenerationParametersAnnotation(
    annotation: KSAnnotation,
    function: KSFunctionDeclaration,
    precomputedIdentifier: String,
    logger: KSPLogger,
): GenerationParameters {
    var markerClass: KSType? = null
    var functionName = ""
    var contextClassName = ""
    var monoParameter = false
    var makeInline = true

    annotation.arguments.forEach { arg ->
        when (arg.name!!.asString()) {
            "markerClass" -> {
                markerClass = arg.value as KSType

                var classDeclaration = markerClass!!.declaration

                while (classDeclaration is KSTypeAlias) {
                    markerClass = classDeclaration.type.resolve()
                    classDeclaration = markerClass!!.declaration
                }

                if (classDeclaration !is KSClassDeclaration) unreachableCode()

                if (classDeclaration.classKind != ClassKind.ANNOTATION_CLASS)
                    processingException(
                        arg.location,
                        message = "markerClass should be an annotation"
                    )

                if (!classDeclaration.annotations.any {
                        it.shortName.asString() == "DslMarker"
                                && it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.DslMarker"
                    })
                    processingException(
                        arg.location,
                        message = "markerClass should be annotated with @DslMarker"
                    )

            }

            "functionName" -> functionName = arg.value as String

            "contextClassName" -> {
                contextClassName = arg.value as String
            }

            "monoParameter" -> {
                monoParameter = arg.value as Boolean

                if (monoParameter) {
                    logger.warn(
                        "Mono-parameter DSLs are not supported yet. monoParameter = true would be ignored",
                        arg
                    )

                    if (function.parameters.size != 1)
                        logger.warn(
                            "Mono-parameter DSL can be generated only for single-parameter functions. " +
                                    "This warning would be treated as error when mono-parameter DSLs come supported.",
                            arg
                        )
                }
            }

            "makeInline" -> {
                makeInline = arg.value as Boolean
            }
        }
    }

    if (markerClass == null)
        processingException(
            annotation.location,
            message = "markerClass is not set in the source file"
        )

    if (functionName.isEmpty())
        functionName = function.simpleName.getShortName()
            .takeUnless { it == "<init>" }
            ?: function.parentDeclaration!!.simpleName.asString().classNameToFunctionName()

    if (contextClassName.isEmpty()) contextClassName = "\$Context\$$precomputedIdentifier"

    return GenerationParameters(markerClass!!, functionName, contextClassName, monoParameter, makeInline)
}

private fun String.classNameToFunctionName() = this.first().toString().lowercase() + substring(1)