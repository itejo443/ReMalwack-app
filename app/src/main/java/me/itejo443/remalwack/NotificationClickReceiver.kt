package me.itejo443.remalwack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class NotificationClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("NotificationClickReceiver", "Received click event")

        if (intent?.action == "com.itejo443.REMALWACK_CLICK") {
            Log.d("NotificationClickReceiver", "Notification click action matched")

            val serviceIntent = Intent(context, ReMalwackTileService::class.java).apply {
                action = "com.itejo443.REMALWACK_CLICK"
            }

            // Start service depending on SDK version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Log.d("NotificationClickReceiver", "Unknown action: ${intent?.action}")
        }
    }
}
