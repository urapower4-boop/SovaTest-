package ru.sova.testsolver

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Проверяем разрешения
        checkPermissions()

        val btnStart = findViewById<Button>(R.id.btn_start)
        btnStart.setOnClickListener {
            startTestSolver()
        }
    }

    private fun checkPermissions() {
        // Проверяем SYSTEM_ALERT_WINDOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Разреши приложению выводить окна поверх других приложений",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun startTestSolver() {
        // Запускаем сервис с оверлеем
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "SovaTest запущена", Toast.LENGTH_SHORT).show()
    }
}
