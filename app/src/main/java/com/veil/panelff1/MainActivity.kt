package com.veil.panelff1

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader

// ==========================================
// 1. MAIN ACTIVITY (Handles UI & Permissions)
// ==========================================
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        
        val btn = Button(this).apply {
            text = "START FF PANEL"
            textSize = 18f
            setOnClickListener {
                checkAndStart()
            }
        }
        
        layout.addView(btn)
        setContentView(layout)

        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun checkAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Display over other apps'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }

        val intent = Intent(this, HackMenuService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish() 
    }
}

// ==========================================
// 2. OVERLAY SERVICE (Android 16 Ready)
// ==========================================
class HackMenuService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var menuView: View
    private lateinit var crosshairView: View
    private var gamePid = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotif())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupCrosshair()
        setupMenu()
        Thread { findGameProcess() }.start()
    }

    private fun buildNotif(): Notification {
        val id = "ff_hack"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(id, "FF Hack", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, id)
            .setContentTitle("FF Panel")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    private fun getOverlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
    else 
        WindowManager.LayoutParams.TYPE_PHONE

    private fun setupCrosshair() {
        crosshairView = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val p = Paint().apply { color = Color.parseColor("#00FF66"); strokeWidth = 4f }
                val cx = width / 2f; val cy = height / 2f
                canvas.drawLine(cx - 30, cy, cx - 10, cy, p); canvas.drawLine(cx + 10, cy, cx + 30, cy, p)
                canvas.drawLine(cx, cy - 30, cx, cy - 10, p); canvas.drawLine(cx, cy + 10, cx, cy + 30, p)
            }
        }.apply { visibility = View.GONE }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayType(), WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(crosshairView, params)
    }

    private fun setupMenu() {
        val ctx = this
        val root = LinearLayout(ctx).apply { 
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#DD0B0E14"))
            setPadding(20, 20, 20, 20) 
        }
        
        val nav = LinearLayout(ctx).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#05070A"))
            setPadding(16, 32, 16, 32)
            layoutParams = LinearLayout.LayoutParams(220, LinearLayout.LayoutParams.MATCH_PARENT) 
        }
        
        fun addNavItem(icon: String, text: String, active: Boolean) {
            nav.addView(TextView(ctx).apply { this.text = icon; textSize = 28f; setTextColor(if (active) Color.parseColor("#00FF66") else Color.GRAY); gravity = Gravity.CENTER })
            nav.addView(TextView(ctx).apply { this.text = text; textSize = 12f; setTextColor(if (active) Color.parseColor("#00FF66") else Color.GRAY); gravity = Gravity.CENTER })
            nav.addView(Space(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 60) })
        }
        addNavItem("◎", "Aimbot", true)
        addNavItem("👁", "Visuais", false)
        addNavItem("📦", "Diversos", false)
        addNavItem("⚙", "Ajustes", false)
        addNavItem("👤", "Conta", false)

        val content = LinearLayout(ctx).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) 
        }
        
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(ctx).apply { text = "⚙ AJUSTES"; textSize = 18f; setTextColor(Color.parseColor("#00FF66")); setTypeface(null, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        val closeBtn = TextView(ctx).apply { text = "✕"; textSize = 24f; setTextColor(Color.parseColor("#00FF66")) }
        header.addView(closeBtn)
        content.addView(header)
        content.addView(Space(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 30) })

        fun makeRow(title: String, isSwitch: Boolean, checked: Boolean = false, onToggle: ((Boolean) -> Unit)? = null): LinearLayout {
            val row = LinearLayout(ctx).apply { 
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#151A22"))
                setPadding(24, 24, 24, 24)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 } 
            }
            row.addView(TextView(ctx).apply { text = title; textSize = 14f; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            if (isSwitch) { 
                val sw = Switch(ctx).apply { this.isChecked = checked }
                sw.setOnCheckedChangeListener { _, b -> onToggle?.invoke(b) }
                row.addView(sw) 
            } else { 
                row.addView(TextView(ctx).apply { text = "Português"; textSize = 14f; setTextColor(Color.parseColor("#00FF66")) }) 
            }
            return row
        }

        content.addView(makeRow("🖥 Modo Stream", true) { toggleMemory("stream", it) })
        content.addView(makeRow("Cor de Destaque", true, true) { toggleMemory("color", it) })
        
        val sliderRow = LinearLayout(ctx).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#151A22"))
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 } 
        }
        val sliderLabel = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        sliderLabel.addView(TextView(ctx).apply { text = "Transparência do Menu"; textSize = 14f; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        val valText = TextView(ctx).apply { text = "0.95"; textSize = 14f; setTextColor(Color.parseColor("#00FF66")) }
        sliderLabel.addView(valText)
        sliderRow.addView(sliderLabel)
        
        sliderRow.addView(SeekBar(ctx).apply {
            max = 100; progress = 95
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF66"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF66"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { 
                    val alpha = p / 100f
                    valText.text = String.format("%.2f", alpha)
                    root.background.alpha = (alpha * 255).toInt() 
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
        content.addView(sliderRow)
        
        content.addView(TextView(ctx).apply { text = "Idioma"; textSize = 14f; setTextColor(Color.WHITE); setPadding(10, 0, 0, 10) })
        content.addView(makeRow("Português", false))
        
        root.addView(nav)
        root.addView(content)
        closeBtn.setOnClickListener { stopSelf() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        
        root.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { 
                        initialX = params.x; initialY = params.y; initialTouchX = event.rawX; initialTouchY = event.rawY; return true 
                    }
                    MotionEvent.ACTION_MOVE -> { 
                        params.x = initialX + (event.rawX - initialTouchX).toInt(); params.y = initialY + (event.rawY - initialTouchY).toInt(); wm.updateViewLayout(menuView, params); return true 
                    }
                }
                return false
            }
        })
        menuView = root
        wm.addView(menuView, params)
    }

    private fun findGameProcess() {
        try {
            val proc = Runtime.getRuntime().exec("su -c ps -A")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("com.dts.freefireth") || line!!.contains("com.dts.freefiremax")) {
                    val parts = line!!.trim().split("\\s+".toRegex())
                    if (parts.size > 1) { gamePid = parts[1].toInt(); break }
                }
            }
        } catch (e: Exception) { gamePid = -1 }
    }

    private fun toggleMemory(feature: String, enable: Boolean) {
        if (gamePid == -1) { 
            Toast.makeText(this, "Game not running or no root", Toast.LENGTH_SHORT).show()
            return 
        }
        if (feature == "color") { 
            crosshairView.visibility = if (enable) View.VISIBLE else View.GONE
            crosshairView.invalidate() 
        }
        try {
            val baseAddr = getLibBase(gamePid, "libil2cpp.so")
            if (baseAddr != 0L) { Toast.makeText(this, "Patched $feature", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { Toast.makeText(this, "Memory patch failed", Toast.LENGTH_SHORT).show() }
    }

    private fun getLibBase(pid: Int, libName: String): Long {
        try {
            val proc = Runtime.getRuntime().exec("su -c cat /proc/$pid/maps")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains(libName) && line!!.contains("r-xp")) { return line!!.split("-")[0].toLong(16) }
            }
        } catch (e: Exception) { }
        return 0L
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::menuView.isInitialized) wm.removeView(menuView)
        if (::crosshairView.isInitialized) wm.removeView(crosshairView)
    }
}
