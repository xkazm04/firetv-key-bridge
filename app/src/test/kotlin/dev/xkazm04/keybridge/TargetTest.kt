package dev.xkazm04.keybridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetTest {
    // -- loopback (ported from the Study Desk stick)

    @Test
    fun `every spelling of this device is loopback`() {
        listOf("localhost", "LOCALHOST", "localhost.", "desk.localhost", "127.0.0.1", "127.8.9.10", "::1", "[::1]",
            "0:0:0:0:0:0:0:1", "0.0.0.0", "::ffff:127.0.0.1").forEach { assertTrue(Target.isLoopback(it), it) }
    }

    @Test
    fun `a LAN address or a name is not loopback`() {
        listOf("10.0.0.141", "192.168.1.20", "128.0.0.1", "desk.lan", "localhosted").forEach { assertFalse(Target.isLoopback(it), it) }
    }

    // -- parsing, kept whole

    @Test
    fun `the path query and fragment are kept`() {
        assertEquals("http://10.0.0.141:3000/writing", Target.parse("http://10.0.0.141:3000/writing"))
        assertEquals("https://app.example.com/tv/home?x=1&y=a%20b#top", Target.parse("https://app.example.com/tv/home?x=1&y=a%20b#top"))
        assertEquals("http://10.0.0.5:3000/a/b/", Target.parse("10.0.0.5:3000/a/b/"))
        assertEquals("http://10.0.0.5/?k=v", Target.parse("10.0.0.5?k=v"))
    }

    @Test
    fun `an empty path becomes a slash`() {
        assertEquals("https://example.com/", Target.parse("https://example.com"))
        assertEquals("http://10.0.0.1:8080/", Target.parse("  10.0.0.1:8080 "))
    }

    @Test
    fun `scheme is inferred from where the host is`() {
        assertEquals("https://app.example.com/", Target.parse("app.example.com"))
        assertEquals("http://app.example.com:8080/", Target.parse("app.example.com:8080"))
        assertEquals("http://10.0.0.5/", Target.parse("10.0.0.5"))
        assertEquals("http://my-pc.local/", Target.parse("my-pc.local"))
        assertEquals("http://nas/", Target.parse("nas"))
        assertEquals("http://localhost:5173/", Target.parse("localhost:5173"))
        assertEquals("http://example.com/", Target.parse("HTTP://Example.COM"))
    }

    @Test
    fun `host is lowercased and the path is not`() {
        assertEquals("https://app.example.com/Case/Kept", Target.parse("https://App.Example.COM/Case/Kept"))
    }

    @Test
    fun `ipv6 in brackets`() {
        assertEquals("http://[fe80::1]:3000/tv", Target.parse("http://[fe80::1]:3000/tv"))
        assertEquals("http://[::1]/", Target.parse("[::1]"))
    }

    @Test
    fun `what is not an address is refused`() {
        listOf(null, "", "   ", "ftp://x", "file:///etc/passwd", "javascript:alert(1)", "10.0.0.256", "10.0.0", "10.0.0.141:0",
            "10.0.0.141:65536", "10.0.0.141:30a0", "10.0.0.141:", "user@10.0.0.141", "https://user:pw@host/", "a::b:3000",
            "-desk.lan", "desk_lan", "[fe80::1", "/just/a/path", "two words", "http://", "https://exa mple.com")
            .forEach { assertNull(Target.parse(it), "$it") }
    }

    @Test
    fun `the demo is a sentinel that cannot be typed`() {
        assertTrue(Target.isDemo(Target.DEMO_URL))
        assertNull(Target.parse(Target.DEMO_URL))
    }

    // -- what may be shown or logged

    @Test
    fun `the label and the log line never carry a query`() {
        val u = "https://app.example.com:8443/tv?token=SECRET#frag"
        assertEquals("app.example.com:8443", Target.label(u))
        assertEquals("https://app.example.com:8443/tv?…", Target.redact(u))
        assertFalse(Target.redact(u).contains("SECRET"))
        assertEquals("http://10.0.0.5/x", Target.redact("http://10.0.0.5/x"))
    }

    // -- start-up decision

    @Test
    fun `an adb extra wins over the kept address`() {
        assertEquals(Boot.Load("http://10.0.0.5:3000/tv", fromExtra = true), Startup.decide("10.0.0.5:3000/tv", "https://old.example/"))
    }

    @Test
    fun `the kept address opens when nothing was given`() {
        assertEquals(Boot.Load("https://old.example/app", fromExtra = false), Startup.decide(null, "https://old.example/app"))
        assertEquals(Boot.Load("https://old.example/app", fromExtra = false), Startup.decide("  ", "https://old.example/app"))
    }

    @Test
    fun `nothing kept and nothing given goes to setup`() {
        assertEquals(Boot.Setup(""), Startup.decide(null, null))
        assertEquals(Boot.Setup(""), Startup.decide(null, "garbage in prefs"))
    }

    @Test
    fun `a bad extra goes to setup with a note and without its query`() {
        val boot = Startup.decide("ftp://host/?token=SECRET", "https://old.example/")
        assertTrue(boot is Boot.Setup)
        assertFalse((boot as Boot.Setup).note.contains("SECRET"))
        assertTrue(boot.note.isNotEmpty())
    }

    // -- the error screen

    @Test
    fun `failures are put in plain words`() {
        assertTrue(Reasons.describe(-2, "net::ERR_NAME_NOT_RESOLVED").contains("name"))
        assertTrue(Reasons.describe(-6, null).contains("listening"))
        assertTrue(Reasons.describe(-8, null).contains("in time"))
        assertEquals("the page did not load (code -99)", Reasons.describe(-99, "net::ERR_WEIRD"))
        assertEquals("custom words", Reasons.describe(-99, " Custom Words "))
        assertEquals("the server answered HTTP 503", Reasons.http(503))
    }

    @Test
    fun `the retry waits longer each time and then holds at thirty seconds`() {
        val b = Backoff()
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 30_000L, 30_000L), List(5) { b.nextMs() })
        b.reset()
        assertEquals(5_000L, b.nextMs())
    }
}
