package me.itejo443.remalwack

import android.app.ActivityManager
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReMalwackTileService : TileService() {

    private val prefsFileName = "tile_prefs"
    private val tileStateKey = "tile_state"
    private val channelId = "ReMalwack_Status_Channel"

    override fun onStartListening() {
        super.onStartListening()
        try {
            checkBlockingAndToast()
        } catch (e: Exception) {
            Log.e("onStartListening", "Failed to start listening and restore tile state", e)
        }
    }    

    override fun onClick() {
        try {
            toggleTileState()
        } catch (e: Exception) {
            Log.e("onClick", "Failed to toggle tile state", e)
        }
    }

    private fun stateStatus() {
        val tileState = loadTileState()
        updateTileState(tileState)
        updateNotification(getLastToastMessage(), getLastModifiedDate())
    }

    private fun toggleTileState() {
        try {
            if (qsTile.state == Tile.STATE_INACTIVE) {
                updateScript()
            } else {
                resetScript()
            }
        } catch (e: Exception) {
            Log.e("toggleTileState", "Error toggling tile state", e)
        }
    }

    fun resetScript() {
        runCommand("su -c sh /data/adb/modules/Re-Malwack/rmlwk.sh --reset")
    }
	
	fun updateScript() {
        runCommand("su -c sh /data/adb/modules/Re-Malwack/rmlwk.sh --update-hosts")
    }
	
	fun checkBlockingAndToast() { 
    	try {
        	val process = ProcessBuilder(
            	"su", "-c",
            	"grep -q \"0.0.0.0\" /system/etc/hosts && echo \"Blocking is working\" || echo \"Blocking is NOT working\""
        	).start()
        	val reader = process.inputStream.bufferedReader()
        	val errorReader = process.errorStream.bufferedReader()
        	val output = reader.readText().trim()
        	val errorOutput = errorReader.readText().trim()
        	process.waitFor()
        	when {
            	output.contains("Blocking is working") -> {
                	showToast("Blocking is working")
                	saveTileState(Tile.STATE_ACTIVE)
                	updateTileState(Tile.STATE_ACTIVE)
                	saveLastModifiedDate("/data/adb/modules/Re-Malwack/system/etc/hosts")
                	updateNotification(getLastToastMessage(), getLastModifiedDate())
            	}
            	output.contains("Blocking is NOT working") -> {
                	showToast("Blocking is NOT working")
                	saveTileState(Tile.STATE_INACTIVE)
                	updateTileState(Tile.STATE_INACTIVE)
                	saveLastModifiedDate("/data/adb/modules/Re-Malwack/system/etc/hosts")
                	updateNotification(getLastToastMessage(), getLastModifiedDate())
            	}
            	errorOutput.isNotEmpty() -> {
                	showToast("Error: $errorOutput")
            	}
            	else -> {
                	checkRootStatus()
            	}
        	}
    	} catch (e: Exception) {
        	e.printStackTrace()
        	checkRootStatus()
    	}
    }
	
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val TAG = "ReMalwackTileService"
    return try {
        if (intent == null) {
            Log.w(TAG, "onStartCommand: Received null intent")
        } else {
            when (intent.action) {
                "com.itejo443.REMALWACK_CLICK" -> {
                    Log.d(TAG, "Action: REMALWACK_NOTIFICATION_CLICK — Checking blocking status…")
                    checkBlockingAndToast()
                }
                "com.itejo443.REMALWACK_UPDATE" -> {
                    Log.d(TAG, "Action: REMALWACK_UPDATE — Running updateScript()")
                    updateScript()
                }
                "com.itejo443.REMALWACK_WEBUI" -> {
                    Log.d(TAG, "Action: REMALWACK_WEBUI — Checking root and launching Web UI")
                    checkRootStatus()
                    launchWebUI()
                }
                "com.itejo443.REMALWACK_RESET" -> {
                    Log.d(TAG, "Action: REMALWACK_RESET — Resetting script")
                    resetScript()
                }
                else -> {
                    Log.w(TAG, "onStartCommand: Unknown action: ${intent.action}")
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
    } catch (e: Exception) {
        Log.e(TAG, "Exception in onStartCommand", e)
        super.onStartCommand(intent, flags, startId)
    }
    }
		
    private var isRunning = false

    fun runCommand(script: String) {
        if (isRunning) {
            showToast("Execution in progress...")
            return
        }

        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                showToast("Executing ...")
            }

            try {
                val process = ProcessBuilder("su", "-c", script).start()

                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.d("RunCommand", "Output: $output")
                Log.e("RunCommand", "Error: $error")
                Log.d("RunCommand", "Exit Code: $exitCode")

                withContext(Dispatchers.Main) {
                    if (exitCode == 0) {
                        checkBlockingAndToast()
                    } else {
                        showToast("Execution Failed ...")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("RunCommand", "Exception: ${e.message}", e)
                    showToast("Execution error: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRunning = false
                }
            }
        }
    }


    fun checkRootStatus() {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readLine()

            output?.contains("uid=0(root)")
        } catch (e: Exception) {
            showToast("Please grant root")
            saveTileState(Tile.STATE_INACTIVE)
            updateTileState(Tile.STATE_INACTIVE)
       	    updateNotification(getLastToastMessage(), getLastModifiedDate())
        }
    }

    fun launchWebUI() {
        try {
            val command = "su -c am start -n me.itejo443.remalwack/.WebUIActivity -e id Re-Malwack"
            val process = Runtime.getRuntime().exec(command)
        
            process.waitFor()

            Toast.makeText(this, "Re-Malwack WebUI launched", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to launch WebUI", Toast.LENGTH_SHORT).show()
            checkRootStatus()
        }
    }

    private fun saveTileState(state: Int) {
        val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt(tileStateKey, state).apply()  // Save state to SharedPreferences
    }

    private fun loadTileState(): Int {
        val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(tileStateKey, Tile.STATE_INACTIVE)  // Load state from SharedPreferences
    }

    private fun updateTileState(state: Int) {
        qsTile?.apply {
            this.state = state
            updateTile()
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("last_toast_message", message).apply()
            updateNotification(getLastToastMessage(), getLastModifiedDate())
        } catch (e: Exception) {
            Log.e("ShowToastError", "Failed to show toast message: $message", e)
        }
    }

    private fun getLastToastMessage(): String {
        return try {
            getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
                .getString("last_toast_message", "") ?: ""
        } catch (e: Exception) {
            Log.e("getLastToastMessage", "Error fetching last toast message", e)
            ""
            }
    }
    
    private fun saveLastModifiedDate(filePath: String) {
        // Command to get the last modified time of the file
        val command = "su -c stat -c %y $filePath"

        try {
            // Run the command to get the file's last modified time
            val process = Runtime.getRuntime().exec(command)
            val reader = process.inputStream.bufferedReader()
            val output = reader.readLine().trim()

            // If output is not empty, process and save it
            if (output.isNotEmpty()) {
                // Save the date with timezone to SharedPreferences
                val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
                sharedPreferences.edit().putString("last_modified_date", output).apply()

            }
        } catch (e: Exception) {
            Log.e("saveLastModifiedDate", "Error fetching last modified date", e)
        }
    }

    private fun getLastModifiedDate(): String {
        val sharedPreferences = getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
        return sharedPreferences.getString("last_modified_date", "") ?: ""
    }

    private fun updateNotification(lastToastMessage: String, lastModifiedDate: String) {
        createNotificationChannel()

        val statusText = "$lastToastMessage\n$lastModifiedDate"

        // Create an Intent for the BroadcastReceiver
        val clickIntent = Intent(this, NotificationClickReceiver::class.java).apply {
            action = "com.itejo443.REMALWACK_CLICK"
        }
    
        // Create a PendingIntent that wraps the intent
        val pendingIntent = PendingIntent.getBroadcast(this, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        // update
        val updateIntent = Intent(this, ReMalwackTileService::class.java).apply {
            action = "com.itejo443.REMALWACK_UPDATE"
        }

        val updatePendingIntent = PendingIntent.getService(this, 1, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // webui
        val webuiIntent = Intent(this, ReMalwackTileService::class.java).apply {
            action = "com.itejo443.REMALWACK_WEBUI"
        }

        val webuiPendingIntent = PendingIntent.getService(this, 2, webuiIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        // reset
        val resetIntent = Intent(this, ReMalwackTileService::class.java).apply {
            action = "com.itejo443.REMALWACK_RESET"
        }

        val resetPendingIntent = PendingIntent.getService(this, 3, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // post icon
        val isLightStatusBar = isLightStatusBar(this)
        val notificationIcon = if (isLightStatusBar) R.drawable.ic_launcher_dark else R.drawable.ic_launcher_light

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Re-Malwack Status")
            .setContentText(statusText)
            .setSmallIcon(notificationIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_noty_update, "Update", updatePendingIntent)
            .addAction(R.drawable.ic_noty_webui, "WebUI", webuiPendingIntent)
            .addAction(R.drawable.ic_noty_reset, "Reset", resetPendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "Re-Malwack Status", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shows the current Re-Malwack status" }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ReMalwackTileService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("DEPRECATION")
    private fun isLightStatusBar(context: Context): Boolean {
        return try {
            val activity = context as? Activity ?: return false
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val window = activity.window
                    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                    windowInsetsController.isAppearanceLightStatusBars == true
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("isLightStatusBar", "Error checking light status bar", e)
            false
        }
    }
}
