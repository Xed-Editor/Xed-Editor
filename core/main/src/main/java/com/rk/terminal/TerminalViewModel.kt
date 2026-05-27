package com.rk.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.termux.view.TerminalView
import com.rk.terminal.virtualkeys.VirtualKeysView
import java.lang.ref.WeakReference

var terminalView = WeakReference<TerminalView?>(null)

class TerminalViewModel : ViewModel() {
    var sessionBinder by mutableStateOf<SessionService.SessionBinder?>(null)
    var isBound by mutableStateOf(false)
    
    var terminalView: TerminalView?
        get() = com.rk.terminal.terminalView.get()
        set(value) { com.rk.terminal.terminalView = WeakReference(value) }

    private var _virtualKeysView = WeakReference<VirtualKeysView?>(null)
    var virtualKeysView: VirtualKeysView?
        get() = _virtualKeysView.get()
        set(value) { _virtualKeysView = WeakReference(value) }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sessionBinder = service as SessionService.SessionBinder
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            sessionBinder = null
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, SessionService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            sessionBinder = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Ensure references are cleared
        com.rk.terminal.terminalView = WeakReference(null)
        _virtualKeysView.clear()
    }
}
