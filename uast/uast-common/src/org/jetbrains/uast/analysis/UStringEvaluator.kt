// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@Suppress("FunctionName")
@ApiStatus.Experimental
@ApiStatus.Internal
fun UStringEvaluator(): UNeDfaValueEvaluator<PartiallyKnownString> = UNeDfaValueEvaluator(UStringEvaluationStrategy)

object UStringEvaluationStrategy : UNeDfaValueEvaluator.UValueEvaluatorStrategy<PartiallyKnownString> {
  override fun calculateLiteral(element: ULiteralExpression): PartiallyKnownString? {
    return element.value?.toString()?.let {
      PartiallyKnownString(StringEntry.Known(it, element.sourcePsi!!, element.ownTextRange))
    }
  }

  override fun calculatePolyadicExpression(element: UPolyadicExpression): UNeDfaValueEvaluator.CalculateRequest<PartiallyKnownString>? {
    if (element.operator == UastBinaryOperator.PLUS) {
      return UNeDfaValueEvaluator.CalculateRequest(element.operands) { operandResults ->
        val entries = mutableListOf<StringEntry>()
        for (operand in operandResults) {
          val segments = operand?.segments
          if (segments != null) {
            entries += segments
          }
          else {
            entries += StringEntry.Unknown(null, TextRange.EMPTY_RANGE)
          }
        }
        PartiallyKnownString(entries)
      }
    }
    return null
  }

  override fun constructValueFromList(element: UElement, values: List<PartiallyKnownString>?): PartiallyKnownString {
    if (values == null) {
      return PartiallyKnownString(StringEntry.Unknown(element.sourcePsi!!, element.ownTextRange))
    }
    return values.collapse(element)
  }

  override fun constructUnknownValue(element: UElement): PartiallyKnownString {
    return PartiallyKnownString(StringEntry.Unknown(element.sourcePsi!!, element.ownTextRange))
  }
}

private val UElement.ownTextRange: TextRange
  get() = TextRange(0, sourcePsi!!.textLength)

private fun List<PartiallyKnownString>.collapse(element: UElement): PartiallyKnownString = when {
  isEmpty() -> PartiallyKnownString(StringEntry.Unknown(element.sourcePsi, element.ownTextRange))
  size == 1 -> single()
  else -> {
    val maxIndex = this.maxOf { it.segments.lastIndex }
    val segments = mutableListOf<StringEntry>()
    for (segmentIndex in 0..maxIndex) {
      val segment = this.mapNotNull { it.segments.getOrNull(segmentIndex) }.firstOrNull() ?: break
      if (map { it.segments.getOrNull(segmentIndex) }.any { !it.areEqual(segment) }) {
        break
      }
      segments.add(segment)
    }

    if (segments.size != maxIndex + 1) {
      segments.add(StringEntry.Unknown(
        element.sourcePsi!!,
        element.ownTextRange,
        map { PartiallyKnownString(it.segments.subList(segments.size, it.segments.size)) }
      ))
    }

    PartiallyKnownString(segments)
  }
}

private fun StringEntry?.areEqual(other: StringEntry?): Boolean {
  if (this == null && other == null) return true
  if (this?.javaClass != other?.javaClass) return false
  if (this is StringEntry.Unknown && other is StringEntry.Unknown) {
    return this.sourcePsi == other.sourcePsi && this.range == other.range
  }
  if (this is StringEntry.Known && other is StringEntry.Known) {
    return this.sourcePsi == other.sourcePsi && this.range == other.range && this.value == other.value
  }
  return false
}