package me.itejo443.remalwack

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.nio.FileSystemManager

data class Module(
    val name: String,
    val id: String,
    val description: String,
    val author: String,
    val version: String
)

class MainActivity : AppCompatActivity(), FileSystemService.Listener {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webViewButton: Button = findViewById(R.id.webViewButton)
        webViewButton.setOnClickListener { FileSystemService.start(this) }

        val rootStatusButton: Button = findViewById(R.id.rootStatusButton)
        rootStatusButton.setOnClickListener { checkRootStatus() }

        val notificationButton: Button = findViewById(R.id.notificationButton)
        notificationButton.setOnClickListener { requestNotificationPermission() }

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Notification Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            AlertDialog.Builder(this)
                .setTitle("Notification Permission Required")
                .setMessage("This app needs notification permissions to function correctly.")
                .setPositiveButton("OK") { _, _ -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onServiceAvailable(fs: FileSystemManager) {
        App.executor.submit {
            val targetModule = findModule(fs, "Re-Malwack")

            runOnUiThread {
                if (targetModule != null) {
                    launchWebView(targetModule)
                } else {
                    Toast.makeText(this, "Module 're-malwack' not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onLaunchFailed() {
        runOnUiThread {
            Toast.makeText(this, "Failed to launch FileSystemService", Toast.LENGTH_LONG).show()
        }
    }

    private fun findModule(fs: FileSystemManager, moduleId: String): Module? {
        fs.getFile("/data/adb/modules").listFiles()?.forEach { f ->
            if (!f.isDirectory) return@forEach
            if (!fs.getFile(f, "webroot").isDirectory) return@forEach
            if (fs.getFile(f, "disable").exists()) return@forEach

            var name = f.name
            val id = f.name
            var author = "?"
            var version = "?"
            var desc = ""

            fs.getFile(f, "module.prop").newInputStream().bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    val ls = line.split("=", limit = 2)
                    if (ls.size == 2) {
                        when (ls[0]) {
                            "name" -> name = ls[1]
                            "description" -> desc = ls[1]
                            "author" -> author = ls[1]
                            "version" -> version = ls[1]
                        }
                    }
                }
            }

            if (id == moduleId) {
                return Module(name, id, desc, author, version)
            }
        }
        return null
    }

    private fun launchWebView(module: Module) {
        if (module.id != "Re-Malwack") {
            runOnUiThread {
                Toast.makeText(this, "Error: Expected 'Re-Malwack' module to be launched!", Toast.LENGTH_LONG).show()
            }
            return
        }

        val intent = Intent(this, WebUIActivity::class.java)
            .setData(Uri.parse("remalwack://webui/${module.id}"))
            .putExtra("id", module.id)
            .putExtra("name", module.name)

        startActivity(intent)
    }

    private fun checkRootStatus() {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readLine()

            if (output?.contains("uid=0(root)") == true) {
                showKernelSUDialog()
            }
        } catch (e: Exception) {
            showRootRequestDialog()
        }
    }

    private fun showRootRequestDialog() {
        val message = """
            For KernelSU requires Capabilities:
            1. DAC_OVERRIDE
            2. DAC_READ_SEARCH
            3. NET_RAW
            4. NET_BIND_SERVICE
        """.trimIndent()

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Please grant root permission")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun showKernelSUDialog() {
        val message = """
            Make sure KernelSU Capabilities for the APP:
            1. DAC_OVERRIDE
            2. DAC_READ_SEARCH
            3. NET_RAW
            4. NET_BIND_SERVICE
        """.trimIndent()

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Root Granted:")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}
