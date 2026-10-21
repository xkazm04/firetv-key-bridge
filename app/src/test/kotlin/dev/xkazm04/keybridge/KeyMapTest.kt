package dev.xkazm04.keybridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyMapTest {
    /** The README's key table, row for row: Android key code to (key, code, keyCode). */
    private val documented = mapOf(
        19 to PageKey("ArrowUp", "ArrowUp", 38),
        20 to PageKey("ArrowDown", "ArrowDown", 40),
        21 to PageKey("ArrowLeft", "ArrowLeft", 37),
        22 to PageKey("ArrowRight", "ArrowRight", 39),
        23 to PageKey("Enter", "Enter", 13),
        66 to PageKey("Enter", "Enter", 13),
        160 to PageKey("Enter", "Enter", 13),
        96 to PageKey("Enter", "Enter", 13),
        82 to PageKey("ContextMenu", "ContextMenu", 93),
        85 to PageKey("MediaPlayPause", "MediaPlayPause", 179),
        126 to PageKey("MediaPlay", "MediaPlay", 415),
        127 to PageKey("MediaPause", "MediaPause", 19),
        86 to PageKey("MediaStop", "MediaStop", 178),
        87 to PageKey("MediaTrackNext", "MediaTrackNext", 176),
        88 to PageKey("MediaTrackPrevious", "MediaTrackPrevious", 177),
        89 to PageKey("MediaRewind", "MediaRewind", 412),
        90 to PageKey("MediaFastForward", "MediaFastForward", 417),
    )

    @Test
    fun `the table is exactly the documented one`() {
        assertEquals(documented, KeyMap.table())
    }

    @Test
    fun `named constants carry android's real key code values`() {
        assertEquals(4, KeyMap.KEYCODE_BACK)
        assertEquals(23, KeyMap.KEYCODE_DPAD_CENTER)
        assertEquals(82, KeyMap.KEYCODE_MENU)
        assertEquals(85, KeyMap.KEYCODE_MEDIA_PLAY_PAUSE)
        assertEquals(66, KeyMap.KEYCODE_ENTER)
    }

    @Test
    fun `every page key has a code that names its key`() {
        KeyMap.table().values.forEach { assertEquals(it.key, it.code, it.toString()) }
    }

    @Test
    fun `volume home and power stay with the system`() {
        listOf(24, 25, 164, 3, 26, 187).forEach {
            assertNull(KeyMap.pageKeyOf(it), "key $it")
            assertFalse(KeyMap.handles(it), "key $it")
        }
    }

    @Test
    fun `back is its own path and defaults to Escape`() {
        assertTrue(KeyMap.isBack(4))
        assertTrue(KeyMap.isBack(97))
        assertNull(KeyMap.pageKeyOf(4))
        assertTrue(KeyMap.handles(4))
        assertEquals("Escape", BackStyle.from(null).pageKey.key)
        assertEquals(27, BackStyle.from("nonsense").pageKey.keyCode)
        assertEquals("Backspace", BackStyle.from(" Backspace ").pageKey.key)
        assertEquals(8, BackStyle.from("BACKSPACE").pageKey.keyCode)
    }

    @Test
    fun `the script reports whether the page prevented the keydown`() {
        val js = KeyMap.script(BackStyle.ESCAPE.pageKey)
        assertTrue(js.contains("key:'Escape'"))
        assertTrue(js.contains("code:'Escape'"))
        assertTrue(js.contains("keyCode:27"))
        assertTrue(js.contains("cancelable:true"))
        assertTrue(js.contains("bubbles:true"))
        assertTrue(js.contains("return taken"))
        assertTrue(js.indexOf("'keydown'") < js.indexOf("'keyup'"))
    }

    @Test
    fun `the page is told it is inside the bridge`() {
        val js = KeyMap.marker("0.1.0")
        assertTrue(js.contains("window.keyBridge"))
        assertTrue(js.contains("data-key-bridge"))
        assertTrue(js.contains("'0.1.0'"))
    }

    @Test
    fun `back must be held for a deliberate moment to leave`() {
        assertTrue(KeyMap.BACK_HOLD_MS in 500..1500)
    }
}
