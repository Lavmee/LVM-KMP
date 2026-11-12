package tech.annexflow.lvm.decompose

import com.arkivanov.essenty.statekeeper.SerializableContainer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import platform.Foundation.NSCoder
import platform.Foundation.NSString
import platform.Foundation.decodeObjectOfClass
import platform.Foundation.encodeObject

/** The key of the saved state in an `NSCoder`. */
internal const val SAVED_STATE_KEY: String = "tech.annexflow.lvm.decompose.SavedState"

/** Writes [state], for example from [AppComponentRoot.saveState], into this coder, for UIKit state restoration. */
public fun NSCoder.encodeSavedState(state: SerializableContainer) {
    encodeObject(Json.encodeToString(SerializableContainer.serializer(), state), forKey = SAVED_STATE_KEY)
}

/**
 * The state that [encodeSavedState] wrote into this coder, or `null` if there is none or it can't be read, so the app
 * starts without it. It works with coders that require secure coding.
 */
// NSString as a class object, which secure decoding requires, is Beta interop API.
@OptIn(BetaInteropApi::class)
public fun NSCoder.decodeSavedState(): SerializableContainer? {
    val text = decodeObjectOfClass(NSString, forKey = SAVED_STATE_KEY) as? String ?: return null
    return try {
        Json.decodeFromString(SerializableContainer.serializer(), text)
    } catch (_: SerializationException) {
        null
    }
}
