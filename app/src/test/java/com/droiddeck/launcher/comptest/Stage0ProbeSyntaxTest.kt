package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Directive #11 A1: the Stage 0 guest probe script must be valid POSIX sh.
 *
 * Bug history: the script contained `for $f in ...` (a `$` on the loop
 * variable declaration). /bin/sh rejects this with
 * `$f': not a valid identifier`, so Stage 0 returned FAIL_EXIT(2) even on a
 * healthy device and the libc/loader sha256 integrity section never ran.
 *
 * The test runs `sh -n` (parse-only, no execution) over the real script text
 * and also lints for the `$`-on-loop-variable pattern. It fails on the old
 * script and passes on the fixed one.
 */
class Stage0ProbeSyntaxTest {

    private fun shSyntaxCheck(script: String): Pair<Int, String> {
        val tmp = File.createTempFile("stage0-probe", ".sh")
        return try {
            tmp.writeText(script)
            val proc = ProcessBuilder("sh", "-n", tmp.absolutePath)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            code to out
        } finally {
            tmp.delete()
        }
    }

    @Test fun probeScriptPassesShSyntaxCheck() {
        val (code, out) = shSyntaxCheck(CompatTestExecutor.STAGE0_GUEST_SCRIPT)
        assertEquals("sh -n failed on STAGE0_GUEST_SCRIPT:\n$out", 0, code)
    }

    @Test fun probeScriptHasNoDollarOnLoopVariables() {
        // `for $x in` is a syntax error; loop variables are bare words.
        val bad = Regex("""(?m)^\s*for\s+\$""")
        assertFalse(
            "STAGE0_GUEST_SCRIPT declares a loop variable with a \$ prefix",
            bad.containsMatchIn(CompatTestExecutor.STAGE0_GUEST_SCRIPT)
        )
    }

    @Test fun oldScriptWithDollarLoopVarFailsSyntaxCheck() {
        // Regression proof: the pre-#11 script text must fail this test.
        val old = CompatTestExecutor.STAGE0_GUEST_SCRIPT
            .replace("for f in /usr/lib/libc.so.6", "for \$f in /usr/lib/libc.so.6")
        assertTrue("sanity: test setup did not inject the bug", old.contains("for \$f in"))
        val (code, out) = shSyntaxCheck(old)
        assertTrue(
            "expected sh -n to reject the old `for \$f in` script, got exit=$code out=$out",
            code != 0
        )
        val bad = Regex("""(?m)^\s*for\s+\$""")
        assertTrue(bad.containsMatchIn(old))
    }

    @Test fun probeScriptPrintsSha256Hashes() {
        // The integrity section must survive to runtime: the script must
        // contain the sha256 header line and the loop over the two files.
        val s = CompatTestExecutor.STAGE0_GUEST_SCRIPT
        assertTrue(s.contains("sha256 (first 16 hex):"))
        assertTrue(s.contains("for f in /usr/lib/libc.so.6 /usr/lib/ld-linux-aarch64.so.1"))
        assertTrue(s.contains("sha256sum"))
    }
}
