/*
 * Copyright (c) 2022 Ternopol Leonid.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package com.firelion.dslgen.generator.util

import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Variance

internal fun KSValueParameter.resolveActualType(data: Data) =
    if (isVararg) data.usefulTypes.ksArray.replace(listOf(data.resolver.getTypeArgument(type, Variance.INVARIANT)))
    else type.resolve()