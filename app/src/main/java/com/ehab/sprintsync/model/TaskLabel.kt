package com.ehab.sprintsync.model

/**
 * The tag shown on a task card, modelled the same way as [TaskStatus].
 *
 * The [wireValue]s are exactly the strings already stored in Realtime Database and in the
 * local demo database, so existing data keeps working with no migration and the security
 * rules - which only require `label` to be a string - are unaffected.
 *
 * Declaration order is the order the editor offers them, and it is the single source for
 * both that dropdown and the card styling. Previously the set was written out twice, in
 * `R.array.task_labels` for each locale and again as a `when` over raw strings in the
 * adapter, so adding a label meant three edits and a typo silently fell through to the
 * default styling.
 */
enum class TaskLabel(val wireValue: String) {
    FEATURE("FEATURE"),
    BUG("BUG"),
    UI("UI"),
    API("API"),
    DOCS("DOCS");

    companion object {
        /**
         * Degrades to [FEATURE] rather than throwing, so a value written by a newer build -
         * or by hand in the Firebase console - cannot crash an older client. Matching ignores
         * case and surrounding space because the field is free text on the wire.
         */
        fun fromValue(value: String?): TaskLabel =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
                ?: FEATURE
    }
}
