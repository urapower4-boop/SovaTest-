package ru.sova.testsolver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayFrameView? = null
    private var controlView: ControlView? = null
    private var isRunning = false
    private var solverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "sova_channel")
            .setContentTitle("SovaTest работает")
            .setContentText("Оверлей готов")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)

        showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        // Чёрный оверлей
        overlayView = OverlayFrameView(this)
        val overlayParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            width = 400
            height = 600
            x = 100
            y = 200
        }
        windowManager.addView(overlayView, overlayParams)

        // Контролёр сверху (кнопка Пуск/Стоп)
        controlView = ControlView(
            this,
            onStartClicked = { startSolver() },
            onStopClicked = { stopSolver() }
        )
        val controlParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = 100
            y = 50
        }
        windowManager.addView(controlView, controlParams)

        overlayView?.setStatusCallback { status ->
            controlView?.updateStatus(status)
        }
    }

    private fun startSolver() {
        if (isRunning) return
        isRunning = true
        Toast.makeText(this, "🟢 Бот начал работать", Toast.LENGTH_SHORT).show()

        solverJob = scope.launch(Dispatchers.IO) {
            try {
                val solver = TestSolver(this@OverlayService)
                val result = solver.solveTest()

                launch(Dispatchers.Main) {
                    overlayView?.addLog("✅ Результат: $result")
                    isRunning = false
                    controlView?.setRunning(false)
                }
            } catch (e: Exception) {
                Log.e("SovaTest", "Error", e)
                launch(Dispatchers.Main) {
                    overlayView?.addLog("❌ Ошибка: ${e.message}")
                    isRunning = false
                    controlView?.setRunning(false)
                }
            }
        }

        controlView?.setRunning(true)
    }

    private fun stopSolver() {
        isRunning = false
        solverJob?.cancel()
        controlView?.setRunning(false)
        overlayView?.addLog("🔴 Бот остановлен")
        Toast.makeText(this, "🔴 Бот остановлен", Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sova_channel",
                "SovaTest",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
        if (controlView != null) {
            windowManager.removeView(controlView)
        }
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
}

// ========== OVERLAY VIEW (Чёрный квадрат) ==========

class OverlayFrameView(context: Context) : View(context) {
    private val logs = mutableListOf<String>()
    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = 14f
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#1a1a1a")
    }
    private var statusCallback: ((String) -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setOnTouchListener(DragTouchListener(this))
    }

    fun addLog(message: String) {
        logs.add(message)
        if (logs.size > 15) logs.removeAt(0)
        invalidate()
    }

    fun setStatusCallback(cb: (String) -> Unit) {
        statusCallback = cb
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Чёрный фон с границей
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val borderPaint = Paint().apply {
            color = Color.CYAN
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        // Логи
        var y = 30f
        for (log in logs) {
            canvas.drawText(log, 10f, y, paint)
            y += 20f
        }
    }
}

// ========== CONTROL VIEW (Кнопка Пуск/Стоп + крест + минус) ==========

class ControlView(
    context: Context,
    val onStartClicked: () -> Unit,
    val onStopClicked: () -> Unit
) : FrameLayout(context) {

    private val btnStart: Button
    private val btnClose: Button
    private val btnMinimize: Button
    private var isRunning = false

    init {
        setBackgroundColor(Color.TRANSPARENT)

        btnStart = Button(context).apply {
            text = "▶ ПУСК"
            setBackgroundColor(Color.parseColor("#00aa00"))
            setTextColor(Color.WHITE)
            textSize = 12f
            setOnClickListener {
                if (!isRunning) {
                    onStartClicked()
                } else {
                    onStopClicked()
                }
            }
        }
        addView(btnStart, LayoutParams(150, 60))

        btnClose = Button(context).apply {
            text = "✕"
            setBackgroundColor(Color.parseColor("#aa0000"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setOnClickListener {
                (context as? Service)?.stopSelf()
            }
        }
        addView(btnClose, LayoutParams(50, 60).apply {
            leftMargin = 160
        })

        btnMinimize = Button(context).apply {
            text = "−"
            setBackgroundColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 20f
            setOnClickListener {
                // Просто скрываем логи, но сервис работает
                alpha = if (alpha == 1f) 0.3f else 1f
            }
        }
        addView(btnMinimize, LayoutParams(50, 60).apply {
            leftMargin = 220
        })

        setOnTouchListener(DragTouchListener(this))
    }

    fun setRunning(running: Boolean) {
        isRunning = running
        btnStart.apply {
            text = if (running) "◼ СТОП" else "▶ ПУСК"
            setBackgroundColor(if (running) Color.parseColor("#aa0000") else Color.parseColor("#00aa00"))
        }
    }

    fun updateStatus(status: String) {
        // Можно обновить текст статуса в реальном времени
    }
}

// ========== DRAG LISTENER ==========

class DragTouchListener(val view: View) : View.OnTouchListener {
    private var dX = 0f
    private var dY = 0f
    private var startX = 0f
    private var startY = 0f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = v.x - event.rawX
                dY = v.y - event.rawY
                startX = event.rawX
                startY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val newX = event.rawX + dX
                val newY = event.rawY + dY
                val lp = v.layoutParams as WindowManager.LayoutParams
                lp.x = newX.toInt()
                lp.y = newY.toInt()
                (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, lp)
                return true
            }
            MotionEvent.ACTION_UP -> {
                // Проверяем, был ли это клик (движение меньше 10px)
                val dist = kotlin.math.sqrt(
                    (event.rawX - startX) * (event.rawX - startX) +
                            (event.rawY - startY) * (event.rawY - startY)
                )
                return dist > 10
            }
        }
        return false
    }
}
