/*
 * Copyright (c) 2024 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*

class DslGenSubplugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.platformType == KotlinPlatformType.jvm
    }

    override fun getCompilerPluginId(): String = "com.firelion.dslgen"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact("com.firelion.dslgen", "postProcessor", DslGenPlugin.VERSION)

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val ext = kotlinCompilation.project.extensions.findByType(DslGenExt::class.java)
            ?: error("Missing DslGenExt extension")

        return kotlinCompilation.project.provider {
            listOf(
                SubpluginOption(
                    "allowDslDefaultArgs",
                    (ext.dslPostProcessorMode == DslPostProcessorMode.COMPILER_PLUGIN && ext.allowDefaultArgs).toString()
                )
            )
        }
    }
}
