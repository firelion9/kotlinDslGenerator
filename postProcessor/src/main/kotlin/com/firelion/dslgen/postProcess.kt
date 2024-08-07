/*
 * Copyright (c) 2022-2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.firelion.dslgen.DslGenConstants.INITIALIZATION_INFO_NAME_PREFIX
import org.jetbrains.org.objectweb.asm.*
import java.io.File
import kotlin.math.max

// @hardlink#004
private const val INTERNAL_API_CLASS = "com/firelion/dslgen/annotations/InternalApiKt"
private const val POST_PROCESS_MARKER_NAME = "callDefaultImplMarker"

/**
 * Recursive post-processes directories and `.class` files.
 *
 * @see PostProcessingClassVisitor
 */
fun postProcessDir(dir: File) {
    dir.listFiles()?.forEach {
        if (it.isDirectory) postProcessDir(it)
        else if (it.extension == "class") postProcessClassFile(it)
    }
}

/**
 * Post processes a `.class` files.
 *
 * @see PostProcessingClassVisitor
 */
private fun postProcessClassFile(classFile: File): Unit = classFile.readBytes().let { bytes ->
    val classReader = ClassReader(bytes)
    val classWriter = ClassWriter(0) // do not compute maxes or stack frames

    val processor = PostProcessingClassVisitor(classWriter)

    classReader.accept(
        processor,
        0 // do not skip or expand anything
    )

    if (processor.classChanged) {
        classFile.writeBytes(classWriter.toByteArray())
    }
}

/**
 * *The Kotlin Dsl Generator* post-processor.
 *
 * Assumes that `callDefaultImplMarker` calls are located only before
 * calls to functions which have default parameters
 * with all arguments pushed onto stack from single generated DSL context class instance:
 * ```
 * INVOKESTATIC com/firelion/dslgen/annotations/PostProcessorTargetMarkerKt.callDefaultImplMarker:()V
 * [
 * NEW result_class
 * DUP
 * ]
 * {
 * ALOAD CONTEXT_LOCAL_INDEX
 * GETFIELD CONTEXT_CLASS.arg0
 * [
 * DUP
 * INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkNotNull(Ljava/lang/Object;)V
 * ]
 * }*
 *
 * INVOKESTATIC/INVOKESPECIAL function_or_constructor_with_default_args
 * ```
 */
private class PostProcessingClassVisitor(classVisitor: ClassVisitor) : ClassVisitor(Opcodes.ASM9, classVisitor) {
    var classChanged = false; private set

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        return super.visitAnnotation(descriptor, visible)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)

        return PostProcessingMethodVisitor(visitor) { classChanged = true }
    }
}

internal class PostProcessingMethodVisitor(
    visitor: MethodVisitor?,
    private val onClassChanged: () -> Unit = {}
) : MethodVisitor(Opcodes.ASM9, visitor) {

    private var isInProcessing: Boolean = false

    /**
     * `true` if `NEW` instruction was just visited.
     */
    private var isJustCalledNew: Boolean = false

    /**
     * Last `GETFIELD` owner argument.
     */
    private var contextClassName: String? = null

    /**
     * Maximum count of arguments in processed calls (before processing).
     */
    private var maxInitInfoCount: Int = -1

    /**
     * Last argument of `ALOAD`.
     */
    private var thisRefIndex: Int = -1

    override fun visitInsn(opcode: Int) {
        if (isInProcessing && opcode == Opcodes.DUP) {
            if (isJustCalledNew) isJustCalledNew = false // DUP after NEW
            else return // remove !! assertion
        }

        super.visitInsn(opcode)
    }

    override fun visitVarInsn(opcode: Int, `var`: Int) {
        if (isInProcessing && opcode == Opcodes.ALOAD)
            thisRefIndex = `var`

        super.visitVarInsn(opcode, `var`)
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        if (isInProcessing && opcode == Opcodes.GETFIELD)
            contextClassName = owner

        super.visitFieldInsn(opcode, owner, name, descriptor)
    }

    override fun visitTypeInsn(opcode: Int, type: String?) {
        if (isInProcessing) isJustCalledNew = true

        super.visitTypeInsn(opcode, type)
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        if (!isInProcessing) {
            if (
                opcode == Opcodes.INVOKESTATIC
                && owner == INTERNAL_API_CLASS
                && name == POST_PROCESS_MARKER_NAME
                && descriptor == "()V"
            ) isInProcessing = true // start processing and remove marker
            else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

            return
        }

        if (
            opcode == Opcodes.INVOKESTATIC
            && owner == "kotlin/jvm/internal/Intrinsics"
            && name == "checkNotNull"
            && descriptor == "(Ljava/lang/Object;)V"
        ) return // remove !! assertion

        // round_up(argument_count / int_bit_size)
        val initInfoCount = (
                descriptor
                    .substringAfter("(")
                    .substringBeforeLast(")")
                    .let("\\[*([BCDFIJSZ]|(L(\\w+/)*\\w+;))".toRegex()::findAll)
                    .count()
                        + Int.SIZE_BITS - 1
                ) / Int.SIZE_BITS

        val isConstructor = name == "<init>"
        val newName = if (isConstructor) name else "$name\$default"

        // add initialization info integer (I) and DefaultConstructorMarker or Object arguments
        val newDescriptor = descriptor.replace(
            ")",
            "I".repeat(initInfoCount) +
                    if (isConstructor) "Lkotlin/jvm/internal/DefaultConstructorMarker;)"
                    else "Ljava/lang/Object;)"
        )

        // pushes initialization info onto stack
        repeat(initInfoCount) {
            super.visitVarInsn(Opcodes.ALOAD, thisRefIndex)
            super.visitFieldInsn(Opcodes.GETFIELD, contextClassName, INITIALIZATION_INFO_NAME_PREFIX + it, "I")
        }
        // pushes null which could be of type Object or DefaultConstructorMarker
        super.visitInsn(Opcodes.ACONST_NULL)

        // calls default variant of processed function
        super.visitMethodInsn(opcode, owner, newName, newDescriptor, isInterface)

        // reset state, mark as changed
        isInProcessing = false
        onClassChanged()
        thisRefIndex = -1
        contextClassName = null
        maxInitInfoCount = max(maxInitInfoCount, initInfoCount)
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // we add maxInitInfoCount variables onto stack while pushing initialization info
        // and an extra null which we use as the last argument,
        // so we need maximum maxInitInfoCount + 1 new stack slots and no new local slots
        super.visitMaxs(maxStack + maxInitInfoCount + 1, maxLocals)
    }
}
