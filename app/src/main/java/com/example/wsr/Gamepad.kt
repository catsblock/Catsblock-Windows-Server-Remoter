package com.example.wsr

import android.view.KeyEvent
import android.view.MotionEvent

object Gamepad {
    var motion: ((MotionEvent) -> Boolean)? = null
    var key: ((KeyEvent) -> Boolean)? = null
}
