package com.boardapp.annotate

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.boardapp.annotate.ink.InkSurfaceView

class MainActivity : AppCompatActivity() {

    private lateinit var ink: InkSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ink = findViewById(R.id.inkView)

        // tools
        findViewById<Button>(R.id.btnPen).setOnClickListener { ink.eraserMode = false }
        findViewById<Button>(R.id.btnEraser).setOnClickListener { ink.eraserMode = true }

        // colors (selecting a color also switches back to pen)
        findViewById<View>(R.id.colBlack).setOnClickListener { pick(Color.BLACK) }
        findViewById<View>(R.id.colRed).setOnClickListener { pick(Color.parseColor("#E53935")) }
        findViewById<View>(R.id.colBlue).setOnClickListener { pick(Color.parseColor("#1E88E5")) }
        findViewById<View>(R.id.colGreen).setOnClickListener { pick(Color.parseColor("#43A047")) }

        // widths
        findViewById<Button>(R.id.btnThin).setOnClickListener { ink.toolWidth = 4f }
        findViewById<Button>(R.id.btnMedium).setOnClickListener { ink.toolWidth = 8f }
        findViewById<Button>(R.id.btnThick).setOnClickListener { ink.toolWidth = 16f }

        // history
        findViewById<Button>(R.id.btnUndo).setOnClickListener { ink.undo() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { ink.redo() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { ink.clearAll() }
    }

    private fun pick(color: Int) {
        ink.toolColor = color
        ink.eraserMode = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }
}
