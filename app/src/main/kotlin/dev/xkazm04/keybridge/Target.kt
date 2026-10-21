package dev.xkazm04.keybridge

/**
 * The page the shell loads. Plain JVM so the rules run in TargetTest; the activity does the loading.
 *
 * The URL is kept whole: scheme, host, port, path, query and fragment. (The Study Desk build threw the path away,
 * which made every page but one unreachable.)
 */
object Target {
    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val NAME = Regex("""^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$""")
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".internal", ".localdomain")

    /** The built-in demo page (docs/demo/index.html, packaged as an asset). Only the "Try the demo" button loads it. */
    const val DEMO_URL = "file:///android_asset/index.html"

    fun isDemo(url: String?) = url == DEMO_URL

    /** True for an address that means "this device": localhost, 127/8, ::1, 0.0.0.0. */
    fun isLoopback(host: String): Boolean {
        val h = host.trim().removePrefix("[").removeSuffix("]").lowercase().trimEnd('.')
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h == "::1" || h == "0:0:0:0:0:0:0:1" || h == "::" || h == "0.0.0.0") return true
        val v4 = h.removePrefix("::ffff:")
        return IPV4.matches(v4) && v4.substringBefore('.') == "127"
    }

    /**
     * Turns what a person typed (or an adb extra carried) into a loadable http(s) URL, or null.
     *
     * - `https://app.example/tv?x=1#a` stays as is (scheme and host lowercased).
     * - A bare `10.0.0.5:3000/tv` or `my-pc.local` gets `http://`; a bare public name (`app.example.com`) gets `https://`.
     * - An empty path becomes `/`. Path, query and fragment are never dropped.
     * - Refused: other schemes, a user part (`user@host`), spaces, a bad host or port.
     */
    fun parse(input: String?): String? {
        var s = input?.trim().orEmpty()
        if (s.isEmpty() || s.any { it.isWhitespace() || it.isISOControl() }) return null
        var scheme: String? = null
        val sep = s.indexOf("://")
        if (sep >= 0) {
            scheme = s.substring(0, sep).lowercase()
            if (scheme != "http" && scheme != "https") return null
            s = s.substring(sep + 3)
        } else if (s.startsWith("/")) {
            return null
        }
        val cut = s.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (cut < 0) s else s.substring(0, cut)
        var rest = if (cut < 0) "" else s.substring(cut)
        if (authority.isEmpty() || authority.contains('@')) return null

        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end < 0) return null
            host = authority.substring(1, end).lowercase()
            val after = authority.substring(end + 1)
            portText = when {
                after.isEmpty() -> null
                after.startsWith(":") -> after.substring(1)
                else -> return null
            }
            if (!host.contains(':') || !host.all { it.isDigit() || it in 'a'..'f' || it == ':' || it == '.' }) return null
        } else {
            if (authority.count { it == ':' } > 1) return null
            host = authority.substringBefore(':').lowercase()
            portText = if (authority.contains(':')) authority.substringAfter(':') else null
            if (!hostOk(host)) return null
        }
        val port = if (portText == null) null else portText.toIntOrNull()?.takeIf { portText.all(Char::isDigit) && it in 1..65535 } ?: return null

        val useScheme = scheme ?: if (port != null || looksLocal(host)) "http" else "https"
        if (rest.isEmpty() || rest[0] != '/') rest = "/$rest"
        val hostOut = if (host.contains(':')) "[$host]" else host
        return "$useScheme://$hostOut${if (port != null) ":$port" else ""}$rest"
    }

    private fun looksLocal(host: String): Boolean =
        IPV4.matches(host) || isLoopback(host) || !host.contains('.') || LOCAL_SUFFIXES.any { host.endsWith(it) }

    private fun hostOk(h: String): Boolean {
        val m = IPV4.matchEntire(h)
        if (m != null) return m.groupValues.drop(1).all { it.toInt() in 0..255 }
        // all digits and dots but not four octets is a mistyped address, not a name
        if (h.all { it.isDigit() || it == '.' }) return false
        return h.length <= 253 && NAME.matches(h)
    }

    /** `host[:port]` of a parsed URL, for screens (no path, never a query: it may carry a token). */
    fun label(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')

    /** The URL with its query and fragment replaced by an ellipsis: what is safe to log. */
    fun redact(url: String): String {
        val cut = url.indexOfFirst { it == '?' || it == '#' }
        return if (cut < 0) url else url.substring(0, cut) + "?…"
    }
}

/** What to do when the shell starts or is re-launched. */
sealed interface Boot {
    /** Load [url]; [fromExtra] when it came from the adb extra (it is kept only once it has loaded). */
    data class Load(val url: String, val fromExtra: Boolean) : Boot
    /** No usable address: show the setup screen with this note. */
    data class Setup(val note: String) : Boot
}

object Startup {
    /**
     * [extra] is the intent's `url` (scripted setup), [saved] what the last successful run kept. An extra wins; one
     * that is not an address goes to setup with a note rather than silently loading the old page.
     */
    fun decide(extra: String?, saved: String?): Boot {
        val given = extra?.trim().orEmpty()
        if (given.isNotEmpty()) {
            val url = Target.parse(given)
                ?: return Boot.Setup("The address given from the computer is not one this app can use: ${Target.redact(given)}")
            return Boot.Load(url, fromExtra = true)
        }
        val kept = Target.parse(saved)
        return if (kept != null) Boot.Load(kept, fromExtra = false) else Boot.Setup("")
    }
}

/** Why a main-frame load failed, in words for a person across the room. Codes are android.webkit.WebViewClient's ERROR_*. */
object Reasons {
    fun describe(errorCode: Int, detail: String?): String = when (errorCode) {
        -2 -> "the name could not be found (check the address and the network)"
        -6 -> "nothing is listening there (is the server running?)"
        -7 -> "the connection dropped"
        -8 -> "it did not answer in time"
        -11 -> "the secure connection failed (certificate or clock)"
        -12 -> "the address is malformed"
        -14 -> "that file is missing"
        else -> detail?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("net::") }?.lowercase()
            ?: "the page did not load (code $errorCode)"
    }

    fun http(status: Int) = "the server answered HTTP $status"
}

/** Milliseconds to wait before the error screen retries by itself: 5 s, 10 s, 20 s, then 30 s for good. */
class Backoff(private val steps: List<Long> = listOf(5_000L, 10_000L, 20_000L, 30_000L)) {
    private var n = 0
    fun reset() { n = 0 }
    fun nextMs(): Long = steps[minOf(n++, steps.size - 1)]
}
