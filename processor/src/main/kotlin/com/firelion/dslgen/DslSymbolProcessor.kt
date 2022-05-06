/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.firelion.dslgen.annotations.GenerateDsl
import com.firelion.dslgen.generator.processFunction
import com.firelion.dslgen.generator.util.Data
import com.firelion.dslgen.generator.util.UsefulTypes
import com.firelion.dslgen.util.processingException
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.visitor.KSDefaultVisitor

internal class DslSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val allowDefaultArguments: Boolean,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenerateDsl::class.qualifiedName!!)
        val invalid = symbols.filter { !it.validate() }.toList()

        val data = Data(
            resolver,
            codeGenerator,
            logger,
            UsefulTypes(resolver),
            allowDefaultArguments,
        )

        symbols
            .filter { it is KSFunctionDeclaration && it.validate() }
            .forEach {
                try {
                    it.accept(DslKSVisitor(), data)
                } catch (e: Exception) {
                    processingException(it.location, e)
                }
            }

        return invalid
    }
}


/**
 * The main processor class that accepts a function through [visitFunctionDeclaration]
 * and generates DSL for it.
 */
private class DslKSVisitor : KSDefaultVisitor<Data, Unit>() {
    override fun defaultHandler(node: KSNode, data: Data) = Unit

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Data) {
        processFunction(function, data)
    }

}


