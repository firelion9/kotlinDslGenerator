/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

inline fun KSPLogger.logging(node: KSNode? = null, lazyMessage: () -> String) {
    logging(lazyMessage(), node)
}

inline fun KSPLogger.info(node: KSNode? = null, lazyMessage: () -> String) {
    info(lazyMessage(), node)
}
