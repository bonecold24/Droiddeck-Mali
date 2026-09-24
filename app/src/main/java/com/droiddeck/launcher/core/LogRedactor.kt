package com.droiddeck.launcher.core

/**
 * Scrubs credentials out of a log line before it is written somewhere a user will share.
 *
 * The Steam client's own logs are the reason this exists: they carry the account's session token
 * (logged as a bare number after "Using JWT"), machine-auth GUIDs, `token=`/`sessionid=` pairs,
 * Steam Guard codes, 32-hex WebAPI keys and the account's SteamID. A session folder from this app
 * is meant to be attached to a bug report as it is, so none of that may be in it.
 *
 * Ported from Bannerlator's {@code SteamLogRedactor} (GPL-3.0), which was written against real
 * client logs; the account-name registration is left out because this app never learns it.
 *
 * What is deliberately KEPT, because it is what makes a log worth reading: EResult codes, CM host
 * names and IP addresses, timings, token expiry dates, app IDs, pids, connection-state changes and
 * file paths. A SteamID is masked rather than deleted - first four and last four digits survive -
 * so a reader can still tell two accounts' lines apart without the value identifying either.
 */
object LogRedactor {
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    /** "Using JWT 25484942796017334" - the client logs its session token as digits, not base64. */
    private val JWT_LABELLED = Regex("(?i)(\\bJWT[\\s=:]+)(\\d{8,})")
    /** A 3-part base64url JWT logged verbatim (a refresh or access token). */
    private val JWT_BASE64 = Regex("ey[A-Za-z0-9_\\-]{6,}\\.[A-Za-z0-9_\\-]{6,}\\.[A-Za-z0-9_\\-]{6,}")
    /** Machine-auth and other GUIDs. */
    private val GUID = Regex("\\b[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\\b")
    /**
     * key=value secrets. A bare `key` is excluded on purpose so "key=english" survives; a real
     * WebAPI key is caught by [WEBAPI_KEY]. The value class excludes `<` and `>` so running this
     * twice over an already-scrubbed line changes nothing.
     */
    private val SECRET_KV = Regex(
        "(?i)\\b(access[_-]?token|refresh[_-]?token|auth[_-]?token|authtoken|token|authcode|" +
            "auth[_-]?ticket|ticket|sessionid|steamloginsecure|webapikey|api[_-]?key|" +
            "machine[_-]?auth(?:[_-]?token)?|machineauth|password|passwd|pwd|secret)" +
            "(\\s*[=:]\\s*|=)([^\\s\"'<>&;,]{4,})"
    )
    /** A Steam Guard code, only where the text around it says that is what it is. */
    private val GUARD_CODE = Regex(
        "(?i)((?:steam\\s*)?guard\\s*code[\\s:=]*|two[\\s-]?factor[\\s:=]*|2fa[\\s:=]*)([A-Za-z0-9]{5})"
    )
    /** Exactly 32 hex - a WebAPI key. Bounded so a 40-hex depot chunk id is left alone. */
    private val WEBAPI_KEY = Regex("\\b[0-9A-Fa-f]{32}\\b")
    /** A long opaque run after a sensitive word, for anything the rules above missed. */
    private val RESIDUAL = Regex(
        "(?i)\\b(jwt|token|ticket|sessionid|steamloginsecure|machineauth)\\b[\\s=:]*([A-Za-z0-9+/=_\\-]{12,})"
    )
    private val LONG_TOKEN = Regex("[A-Za-z0-9_\\-]{88,}")
    private val STEAMID64 = Regex("\\b(76561)(\\d{8})(\\d{4})\\b")
    private val STEAMID3 = Regex("\\[U:1:(\\d+)]")

    /** [line] with every credential shape replaced. Null- and exception-safe by construction. */
    fun redact(line: String): String {
        if (line.isEmpty()) return line
        return try {
            var out = line
            out = GUID.replace(out, "<redacted:guid>")
            out = JWT_LABELLED.replace(out) { "${it.groupValues[1]}<redacted:jwt>" }
            out = JWT_BASE64.replace(out, "<redacted:jwt>")
            out = SECRET_KV.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}<redacted:token>" }
            out = GUARD_CODE.replace(out) { "${it.groupValues[1]}<redacted:code>" }
            out = WEBAPI_KEY.replace(out, "<redacted:key>")
            // Mask, not delete: the last four digits let a reader correlate lines to one account.
            out = STEAMID64.replace(out) { "${it.groupValues[1]}********${it.groupValues[3]}" }
            out = STEAMID3.replace(out) { m ->
                val id = m.groupValues[1]
                "[U:1:${if (id.length > 4) "*".repeat(id.length - 4) + id.takeLast(4) else id}]"
            }
            out = EMAIL.replace(out, "<redacted:email>")
            out = RESIDUAL.replace(out) { "${it.groupValues[1]}=<redacted:token>" }
            out = LONG_TOKEN.replace(out, "<redacted:token>")
            out
        } catch (t: Throwable) {
            // A log line is never worth crashing a session over, but an unscrubbed one must not
            // reach the file either.
            "<redaction failed; line withheld>"
        }
    }
}
