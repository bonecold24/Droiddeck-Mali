package com.droiddeck.launcher.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Directive #11 UPDATE: Redactor hardening.
 *
 * Table-driven test for SECRET_KV over many secret formats. Each case asserts:
 * 1. The secret VALUE is gone from the output (redacted).
 * 2. The key NAME is still readable (not over-redacted).
 *
 * The regex uses (?<![A-Za-z0-9]) instead of \b so that underscore-wrapped
 * names like __token__ match (underscore is a word character, so \b fails).
 */
class SecretKvTableTest {

    data class Case(
        val input: String,
        val secretValue: String,
        val keyName: String,
    )

    private val cases = listOf(
        // Python-style dunder token (the original leak)
        Case(
            "fetch https://example.com/api?__token__=exp=1~hmac=deadbeefcafe",
            "deadbeefcafe", "__token__",
        ),
        // Standard OAuth token names
        Case("GET /x?access_token=abc123xyz789", "abc123xyz789", "access_token"),
        Case("POST /refresh refresh_token=rt_9876543210", "rt_9876543210", "refresh_token"),
        Case("auth id_token=eyJhbGciOiJIUzI1NiJ9.payload", "eyJhbGciOiJIUzI1NiJ9.payload", "id_token"),
        // API keys (x- prefix is not part of the key name per the regex)
        Case("curl -H 'x-api-key: sk_live_51H7xYz' https://api.example.com", "sk_live_51H7xYz", "api-key"),
        Case("api_key=AKIAIOSFODNN7EXAMPLE", "AKIAIOSFODNN7EXAMPLE", "api_key"),
        // Authorization header
        Case("Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", "Authorization"),
        // Passwords and secrets
        Case("login password=SuperSecret123!", "SuperSecret123!", "password"),
        Case("config secret=my_db_password_42", "my_db_password_42", "secret"),
        // JSON formats
        Case("""{"token":"json_secret_abc123"}""", "json_secret_abc123", "token"),
        Case("""{"access_token":"json_at_xyz789"}""", "json_at_xyz789", "access_token"),
        // URL query parameters
        Case("GET /callback?token=url_token_12345&state=xyz", "url_token_12345", "token"),
        Case("GET /callback?state=xyz&token=amp_token_67890", "amp_token_67890", "token"),
    )

    @Test fun allSecretsRedactedKeyNamesPreserved() {
        for (c in cases) {
            val out = LogRedactor.redact(c.input)
            assertFalse(
                "secret value '${c.secretValue}' survived in: ${c.input} -> $out",
                out.contains(c.secretValue)
            )
            assertTrue(
                "key name '${c.keyName}' was over-redacted in: ${c.input} -> $out",
                out.contains(c.keyName)
            )
        }
    }

    @Test fun underscoreVariants() {
        // Token with single underscores
        val out1 = LogRedactor.redact("get _token_=abc123")
        assertFalse(out1.contains("abc123"))
        // Token with leading underscore only
        val out2 = LogRedactor.redact("get _token=def456")
        assertFalse(out2.contains("def456"))
    }
}
