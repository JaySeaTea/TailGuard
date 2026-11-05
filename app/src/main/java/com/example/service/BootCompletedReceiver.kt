package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.TailGuardApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        val app = context.applicationContext as? TailGuardApplication
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = app?.container?.repository?.getSettingsSnapshot()?.autoConnectEnabled ?: false
                if (enabled) {
                    NetworkMonitorService.startPassiveMonitor(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
