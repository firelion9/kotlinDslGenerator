/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.util

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.Location

/**
 * Exception with specifiable [Location].
 */
internal class ProcessingException(location: Location, cause: Exception?, message: String) :
    RuntimeException(((location as? FileLocation)?.run { "$filePath:$lineNumber: " } ?: "") + message, cause)

internal fun processingException(location: Location, cause: Exception? = null, message: String = "see exception below for details"): Nothing =
    throw ProcessingException(location, cause, message)