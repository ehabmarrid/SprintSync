package com.ehab.sprintsync.util

import androidx.core.text.BidiFormatter

/**
 * Wraps a run of text whose direction may differ from the paragraph around it.
 *
 * A TextView decides its paragraph direction from the first strong character, so a Hebrew
 * string that happens to begin with a Latin substitution - "SPRINT-3 · 6 משימות" - is laid
 * out left-to-right and aligns left, even in a Hebrew locale with supportsRtl enabled.
 * Isolating the Latin run with the Unicode bidi marks stops it dictating the direction of
 * the whole line, and keeps adjacent punctuation on the side it belongs to.
 *
 * Layout attributes alone are not enough: `textAlignment="viewStart"` fixes where the line
 * sits, but not the order of the runs inside it. Both halves are needed.
 */
object BidiText {

    /**
     * Not cached: [BidiFormatter.getInstance] resolves against the current default locale,
     * and the in-app language switcher changes that at runtime. A stored instance would go
     * on formatting for whichever locale happened to be active when it was created.
     */
    fun isolate(value: String): String = BidiFormatter.getInstance().unicodeWrap(value)
}
