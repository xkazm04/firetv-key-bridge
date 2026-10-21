package dev.xkazm04.keybridge

/**
 * What the page receives for one remote button: a standard DOM KeyboardEvent's `key`, `code` and legacy `keyCode`.
 */
data class PageKey(val key: String, val code: String, val keyCode: Int)

/** Which key Back is sent as. Escape is the default: unlike Backspace it never deletes a character in a text field. */
enum class BackStyle(val pageKey: PageKey) {
    ESCAPE(PageKey("Escape", "Escape", 27)),
    BACKSPACE(PageKey("Backspace", "Backspace", 8));

    companion object {
        fun from(name: String?): BackStyle = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: ESCAPE
    }
}

/**
 * The key mapping table. Android key codes are plain ints (android.view.KeyEvent's values) so the table runs on the JVM.
 * The README prints this same table; KeyMapTest pins it, so the two cannot drift apart unnoticed.
 *
 * Anything not in the table (volume, Home, power) stays with the system.
 */
object KeyMap {
    const val KEYCODE_BACK = 4
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_ENTER = 66
    const val KEYCODE_MENU = 82
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_STOP = 86
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_MEDIA_REWIND = 89
    const val KEYCODE_MEDIA_FAST_FORWARD = 90
    const val KEYCODE_BUTTON_A = 96
    const val KEYCODE_BUTTON_B = 97
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
    const val KEYCODE_NUMPAD_ENTER = 160

    /** How long Back is held before it leaves the app whatever the page does. */
    const val BACK_HOLD_MS = 800L

    private val ARROW_UP = PageKey("ArrowUp", "ArrowUp", 38)
    private val ARROW_DOWN = PageKey("ArrowDown", "ArrowDown", 40)
    private val ARROW_LEFT = PageKey("ArrowLeft", "ArrowLeft", 37)
    private val ARROW_RIGHT = PageKey("ArrowRight", "ArrowRight", 39)
    private val ENTER = PageKey("Enter", "Enter", 13)

    private val TABLE: Map<Int, PageKey> = mapOf(
        KEYCODE_DPAD_UP to ARROW_UP,
        KEYCODE_DPAD_DOWN to ARROW_DOWN,
        KEYCODE_DPAD_LEFT to ARROW_LEFT,
        KEYCODE_DPAD_RIGHT to ARROW_RIGHT,
        KEYCODE_DPAD_CENTER to ENTER,
        KEYCODE_ENTER to ENTER,
        KEYCODE_NUMPAD_ENTER to ENTER,
        KEYCODE_BUTTON_A to ENTER,
        KEYCODE_MENU to PageKey("ContextMenu", "ContextMenu", 93),
        KEYCODE_MEDIA_PLAY_PAUSE to PageKey("MediaPlayPause", "MediaPlayPause", 179),
        KEYCODE_MEDIA_PLAY to PageKey("MediaPlay", "MediaPlay", 415),
        KEYCODE_MEDIA_PAUSE to PageKey("MediaPause", "MediaPause", 19),
        KEYCODE_MEDIA_STOP to PageKey("MediaStop", "MediaStop", 178),
        KEYCODE_MEDIA_NEXT to PageKey("MediaTrackNext", "MediaTrackNext", 176),
        KEYCODE_MEDIA_PREVIOUS to PageKey("MediaTrackPrevious", "MediaTrackPrevious", 177),
        KEYCODE_MEDIA_REWIND to PageKey("MediaRewind", "MediaRewind", 412),
        KEYCODE_MEDIA_FAST_FORWARD to PageKey("MediaFastForward", "MediaFastForward", 417),
    )

    /** Back buttons: the remote's Back and a gamepad's B. They take the Back path (page first, hold leaves). */
    fun isBack(androidKeyCode: Int) = androidKeyCode == KEYCODE_BACK || androidKeyCode == KEYCODE_BUTTON_B

    /** The page key for a non-Back button, or null when the system keeps it. */
    fun pageKeyOf(androidKeyCode: Int): PageKey? = TABLE[androidKeyCode]

    /** Every non-Back mapping, for documentation and tests. */
    fun table(): Map<Int, PageKey> = TABLE

    /** Whether the bridge handles this button at all (mapped, or Back). */
    fun handles(androidKeyCode: Int) = isBack(androidKeyCode) || TABLE.containsKey(androidKeyCode)

    /**
     * Runs in the page: a keydown then a keyup on the focused element (or `body`), bubbling up to `document` and
     * `window` where TV apps listen. The script's value is `true` when the page took the keydown, that is, called
     * preventDefault. `keyCode` and `which` are redefined because some engines leave them 0 for synthetic events.
     */
    fun script(k: PageKey): String {
        val init = "{key:'${k.key}',code:'${k.code}',keyCode:${k.keyCode},which:${k.keyCode},bubbles:true,cancelable:true,composed:true}"
        val fix = "try{Object.defineProperty(%s,'keyCode',{get:function(){return o.keyCode}});" +
            "Object.defineProperty(%s,'which',{get:function(){return o.keyCode}});}catch(e){}"
        return "(function(){var a=document.activeElement;" +
            "var t=(a&&a!==document.documentElement)?a:(document.body||document.documentElement);" +
            "var o=$init;var d=new KeyboardEvent('keydown',o);" + fix.format("d", "d") +
            "var taken=!t.dispatchEvent(d);" +
            "var u=new KeyboardEvent('keyup',o);" + fix.format("u", "u") +
            "t.dispatchEvent(u);return taken;})()"
    }

    /** Runs on every page load: lets a page know it is inside the bridge (`window.keyBridge`, `<html data-key-bridge>`). */
    fun marker(version: String) =
        "(function(){window.keyBridge={version:'$version'};document.documentElement.setAttribute('data-key-bridge','$version');})()"
}
