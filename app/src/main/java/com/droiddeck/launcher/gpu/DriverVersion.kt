package com.droiddeck.launcher.gpu

/**
 * Ordering for driver package versions ("0.1.0-rc3-universal+local1").
 *
 * Semver-flavoured, forgiving by design: versions come from zip metadata the user
 * supplies, so junk must sort, never throw. Numeric core parts compare numerically;
 * a release (no qualifier) beats any prerelease; known prerelease tags order
 * alpha < beta < rc < universal < anything else; build metadata ("+local1") is
 * ignored for precedence, like semver.
 */
object DriverVersion {

    fun compare(a: String, b: String): Int {
        val (coreA, qualA) = split(a)
        val (coreB, qualB) = split(b)
        val n = maxOf(coreA.size, coreB.size)
        for (i in 0 until n) {
            val x = coreA.getOrElse(i) { 0 }
            val y = coreB.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return compareQualifier(qualA, qualB)
    }

    fun isNewer(a: String, b: String): Boolean = compare(a, b) > 0

    private fun split(v: String): Pair<List<Int>, String> {
        val noBuild = v.substringBefore('+')
        val core = noBuild.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val qualifier = noBuild.substringAfter('-', "")
        return core to qualifier
    }

    private fun compareQualifier(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return 1 // release > prerelease
        if (b.isEmpty()) return -1
        val ta = a.split('.', '-', '_')
        val tb = b.split('.', '-', '_')
        val n = maxOf(ta.size, tb.size)
        for (i in 0 until n) {
            val x = ta.getOrElse(i) { "" }
            val y = tb.getOrElse(i) { "" }
            if (x == y) continue
            val rx = rank(x)
            val ry = rank(y)
            if (rx != ry) return rx.compareTo(ry)
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            if (nx != null && ny != null && nx != ny) return nx.compareTo(ny)
            val c = x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }

    private fun rank(token: String): Int = when (token.lowercase()) {
        "alpha" -> 0
        "beta" -> 1
        "rc" -> 2
        "universal" -> 3
        else -> 4
    }
}
