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
}
