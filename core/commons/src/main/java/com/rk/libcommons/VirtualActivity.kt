package com.rk.libcommons

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

interface VirtualActivityImpl {
    fun onCreate(state: Bundle?)

    fun onDestroy()

    fun onPause()

    fun onResume()
}

private val activityMap = HashMap<String, VirtualActivityImpl>()

fun launchActivity(
    context: Context,
    impl: VirtualActivityImpl,
    intent: Intent? = null,
    id: String,
) {
    var xintent = intent
    if (xintent == null) {
        xintent = Intent(context, VirtualActivity::class.java)
    }
    xintent.putExtra("id", id)
    activityMap[id] = impl
    context.startActivity(xintent)
}

class VirtualActivity : AppCompatActivity() {
    private var impl: VirtualActivityImpl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        impl = activityMap[intent.getStringExtra("id")]
        super.onCreate(savedInstanceState)
        impl?.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        impl?.onDestroy()
        super.onDestroy()
    }

    override fun onPause() {
        impl?.onPause()
        super.onPause()
    }

    override fun onResume() {
        impl?.onResume()
        super.onResume()
    }
}
