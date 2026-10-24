package io.github.notifilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTest {
    private fun notification(pkg: String = "com.shop", title: String = "", text: String = "") =
        NotificationInfo(key = "k", packageName = pkg, appLabel = "Shop", title = title, text = text, postTime = 0)

    private fun Rule.matches(n: NotificationInfo) = compileOrNull()!!.matches(n)

    @Test
    fun allFilledConditionsMustMatch() {
        val rule = Rule(packageName = "com.shop", titlePattern = "sale", textPattern = "\\d+% off")
        assertTrue(rule.matches(notification(title = "Big SALE today", text = "Get 50% off")))
        assertFalse(rule.matches(notification(title = "Big SALE today", text = "Free shipping")))
        assertFalse(rule.matches(notification(pkg = "com.other", title = "Big SALE today", text = "Get 50% off")))
    }

    @Test
    fun blankConditionsAreIgnored() {
        val rule = Rule(packageName = "com.shop")
        assertTrue(rule.matches(notification(title = "anything")))
        assertTrue(Rule(textPattern = "promo").matches(notification(pkg = "any.app", text = "a promo code")))
    }

    @Test
    fun ignoreCaseIsRespected() {
        assertTrue(Rule(titlePattern = "sale").matches(notification(title = "SALE")))
        assertFalse(Rule(titlePattern = "sale", ignoreCase = false).matches(notification(title = "SALE")))
    }

    @Test
    fun emptyAndInvalidRulesNeverCompile() {
        assertNull(Rule().compileOrNull())
        assertNull(Rule(titlePattern = "(unclosed").compileOrNull())
        assertNotNull(patternError("(unclosed", ignoreCase = true))
        assertNull(patternError("", ignoreCase = true))
    }

    @Test
    fun compileAllSkipsDisabledAndInvalidRules() {
        val ok = Rule(titlePattern = "a")
        val compiled = compileAll(listOf(ok, Rule(titlePattern = "b", enabled = false), Rule(titlePattern = "[")))
        assertEquals(listOf(ok), compiled.map { it.rule })
    }

    @Test
    fun escapedTextMatchesItselfLiterally() {
        val title = "Order #12 (shipped) costs $3.50 [x]?"
        val rule = Rule(titlePattern = "^${escapeRegex(title)}$")
        assertTrue(rule.matches(notification(title = title)))
        assertFalse(rule.matches(notification(title = "Order #12 (shipped) costs $3X50 [x]?")))
    }

    @Test
    fun patternAnyNumberLetsNumbersChangeButEscapesTheRest() {
        assertEquals("\\d+ new messages", patternAnyNumber("3 new messages"))
        assertEquals("Order #\\d+ costs \\$\\d+\\.\\d+", patternAnyNumber("Order #12 costs $3.50"))
        assertEquals("code \\d+", patternAnyNumber("code 482913"))
        assertEquals("\\d+", patternAnyNumber("2026"))
        assertEquals("no digits here\\.", patternAnyNumber("no digits here."))
    }

    @Test
    fun ruleFromMatchesTheSameNotificationWithDifferentNumbers() {
        val original = notification(title = "3 new messages", text = "Your code is 482913\nDon't share it")
        val rule = ruleFrom(original)
        assertEquals("^\\d+ new messages$", rule.titlePattern)
        assertEquals("Your code is \\d+", rule.textPattern)
        assertEquals("com.shop", rule.packageName)
        assertTrue(rule.matches(original))
        assertTrue(rule.matches(notification(title = "12 new messages", text = "Your code is 731605")))
        assertFalse(rule.matches(notification(title = "12 new messages", text = "Your order shipped")))
        assertFalse(rule.matches(notification(title = "New messages: 12", text = "Your code is 731605")))
    }

    @Test
    fun ruleFromWithoutTitleUsesTextOnly() {
        val rule = ruleFrom(notification(text = "Backup (1 of 2) done"))
        assertEquals("", rule.titlePattern)
        assertEquals("Backup \\(\\d+ of \\d+\\) done", rule.textPattern)
        assertEquals("Shop", rule.name)
    }
}
