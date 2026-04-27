package me.rerere.rikkahub.data.event

import android.net.Uri

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data class TakePhoto(val onResult: (Uri?) -> Unit) : AppEvent()
}
