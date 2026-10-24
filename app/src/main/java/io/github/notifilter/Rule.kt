package io.github.notifilter

import java.util.UUID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A filter rule. Every non-blank condition must match for the rule to apply; blank conditions are
 * ignored. Patterns are Java regular expressions and match anywhere in the text (use ^ and $ to
 * anchor).
 */
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    /** Exact package name of the posting app, or blank for any app. */
    val packageName: String = "",
    val titlePattern: String = "",
    val textPattern: String = "",
    val ignoreCase: Boolean = true,
) {
    /** A rule without conditions would match every notification, so it is never applied. */
    val isEmpty: Boolean
        get() = packageName.isBlank() && titlePattern.isBlank() && textPattern.isBlank()

    val displayName: String
        get() = name.ifBlank { titlePattern.ifBlank { textPattern.ifBlank { packageName } } }

    val flags: Int
        get() = if (ignoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0

    /** Returns null if the rule is empty or one of its patterns is invalid. */
    fun compileOrNull(): CompiledRule? {
        if (isEmpty) return null
        return try {
            CompiledRule(this, compile(titlePattern), compile(textPattern))
        } catch (_: PatternSyntaxException) {
            null
        }
    }

    private fun compile(pattern: String): Pattern? =
        if (pattern.isBlank()) null else Pattern.compile(pattern, flags)
}

class CompiledRule(val rule: Rule, private val title: Pattern?, private val text: Pattern?) {
    fun matches(n: NotificationInfo): Boolean {
        if (rule.packageName.isNotBlank() && rule.packageName.trim() != n.packageName) return false
        if (title != null && !title.matcher(n.title).find()) return false
        if (text != null && !text.matcher(n.text).find()) return false
        return true
    }
}

fun compileAll(rules: List<Rule>): List<CompiledRule> =
    rules.filter { it.enabled }.mapNotNull { it.compileOrNull() }

/** Human-readable error for an invalid pattern, or null if it is valid (or blank). */
fun patternError(pattern: String, ignoreCase: Boolean): String? {
    if (pattern.isBlank()) return null
    return try {
        Pattern.compile(pattern, Rule(ignoreCase = ignoreCase).flags)
        null
    } catch (e: PatternSyntaxException) {
        e.description ?: "Invalid regular expression"
    }
}

private val REGEX_SPECIALS = "\\^$.|?*+()[]{}".toSet()

/** Escapes [literal] so it can be used as a regex that matches exactly that text. */
fun escapeRegex(literal: String): String = buildString {
    for (c in literal) {
        if (c in REGEX_SPECIALS) append('\\')
        append(c)
    }
}

private val DIGITS = Regex("\\d+")

/**
 * Like [escapeRegex], but each number in [literal] matches any number, so a pattern made from
 * "3 new messages" also catches "12 new messages", and one made from a code message catches the next code.
 */
fun patternAnyNumber(literal: String): String = literal.split(DIGITS).joinToString("\\d+") { escapeRegex(it) }

/**
 * Starts a rule that matches this notification: same app, exact title, and text containing its first
 * line, with numbers allowed to change. The user can generalise it further before saving.
 */
fun ruleFrom(n: NotificationInfo) = Rule(
    name = n.title.ifBlank { n.appLabel }.take(60),
    packageName = n.packageName,
    titlePattern = if (n.title.isNotBlank()) "^${patternAnyNumber(n.title)}$" else "",
    textPattern = if (n.text.isNotBlank()) patternAnyNumber(n.text.lineSequence().first()) else "",
)

/** A snapshot of a notification's matchable content. */
data class NotificationInfo(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    /** Set when this notification was dismissed by a rule. */
    val ruleId: String? = null,
    val ruleName: String? = null,
)
