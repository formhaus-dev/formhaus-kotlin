package dev.formhaus.core.unicode

private val idStart by lazy { RangeTable(ID_START_RANGES) }
private val idContinue by lazy { RangeTable(ID_CONTINUE_RANGES) }
private val letterMarkNumber by lazy { RangeTable(LETTER_MARK_NUMBER_RANGES) }
private val closingPunctuation by lazy { RangeTable(CLOSING_PUNCTUATION_RANGES) }
internal val whiteSpace: RangeTable by lazy { RangeTable(WHITE_SPACE_RANGES) }
internal val canonicalRuns: CaseRuns by lazy { CaseRuns(CANONICAL_RUNS) }

internal fun isIdStart(point: Int): Boolean = point in idStart

internal fun isIdContinue(point: Int): Boolean = point in idContinue

internal fun isLetterMarkNumber(point: Int): Boolean = point in letterMarkNumber

internal fun endsWithClosingPunctuation(text: String): Boolean =
    text.isNotEmpty() && text.codePointBefore(text.length) in closingPunctuation
