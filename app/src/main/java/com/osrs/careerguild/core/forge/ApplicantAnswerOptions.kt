package com.osrs.careerguild.core.forge

/**
 * Strict selection states for legal, authorization, and demographic fields.
 * Prevents free-text hallucinations and guarantees deterministic values across
 * Room persistence, UI option buttons, and the Courier background pipeline.
 */
enum class YesNoOption(val value: String) {
    YES("Yes"),
    NO("No");

    companion object {
        fun fromString(raw: String?): YesNoOption? {
            val trimmed = raw?.trim()?.lowercase() ?: return null
            if (trimmed in setOf("yes", "y", "true", "1")) return YES
            if (trimmed in setOf("no", "n", "false", "0")) return NO
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
        }
    }
}

enum class EEOOption(val value: String) {
    YES("Yes"),
    NO("No"),
    PREFER_NOT_TO_SAY("Prefer not to say");

    companion object {
        fun fromString(raw: String?): EEOOption? {
            val trimmed = raw?.trim()?.lowercase() ?: return null
            if (trimmed in setOf("yes", "y", "true")) return YES
            if (trimmed in setOf("no", "n", "false")) return NO
            if (trimmed in setOf("prefer not to say", "prefer not", "decline to self-identify", "decline", "i choose not to disclose", "choose not to disclose", "do not wish to answer", "i do not wish to answer")) return PREFER_NOT_TO_SAY
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
        }
    }
}

enum class GenderOption(val value: String) {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Non-binary"),
    PREFER_NOT_TO_SAY("Prefer not to say");

    companion object {
        fun fromString(raw: String?): GenderOption? {
            val trimmed = raw?.trim()?.lowercase() ?: return null
            if (trimmed in setOf("male", "man", "m")) return MALE
            if (trimmed in setOf("female", "woman", "w", "f")) return FEMALE
            if (trimmed in setOf("non-binary", "nonbinary", "nb")) return NON_BINARY
            if (trimmed in setOf("prefer not to say", "prefer not", "decline to self-identify", "decline", "do not wish to answer", "choose not to disclose")) return PREFER_NOT_TO_SAY
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
        }
    }
}

enum class VeteranOption(val value: String) {
    VETERAN("Veteran"),
    NOT_A_VETERAN("Not a veteran"),
    PREFER_NOT_TO_SAY("Prefer not to say");

    companion object {
        fun fromString(raw: String?): VeteranOption? {
            val trimmed = raw?.trim()?.lowercase() ?: return null
            if (trimmed in setOf("not a veteran", "not a protected veteran", "i am not a protected veteran", "am not a veteran", "not veteran", "non-veteran")) return NOT_A_VETERAN
            if (trimmed in setOf("veteran", "i identify as a veteran", "protected veteran", "identify as a veteran")) return VETERAN
            if (trimmed in setOf("prefer not to say", "prefer not", "i decline to self-identify for protected veteran status", "decline to self-identify", "decline", "do not wish to answer", "choose not to disclose")) return PREFER_NOT_TO_SAY
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
        }
    }
}

enum class StartDateOption(val value: String) {
    IMMEDIATELY("Immediately"),
    TWO_WEEKS("2 Weeks"),
    ONE_MONTH("1 Month");

    companion object {
        fun fromString(raw: String?): StartDateOption? {
            val trimmed = raw?.trim()?.lowercase() ?: return null
            if (trimmed in setOf("immediately", "immediate", "now", "asap")) return IMMEDIATELY
            if (trimmed in setOf("2 weeks", "two weeks", "2w")) return TWO_WEEKS
            if (trimmed in setOf("1 month", "one month", "1m", "4 weeks")) return ONE_MONTH
            return entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true) }
        }
    }
}
