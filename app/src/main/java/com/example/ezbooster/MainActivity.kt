package com.example.ezbooster

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlin.math.atan2

class MainActivity : AppCompatActivity() {

    private val TAG = "EZBoosterLog"
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private lateinit var audioManager: AudioManager
    
    private lateinit var sbSystemVolume: SeekBar
    private lateinit var circularProgress: CircularProgressIndicator
    private lateinit var tvVolumePercent: TextView
    private lateinit var tvStatusLabel: TextView
    private lateinit var diskContainer: FrameLayout

    private var maxSystemVolume = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "== EZ Booster Запущено ==")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        sbSystemVolume = findViewById(R.id.sbSystemVolume)
        circularProgress = findViewById(R.id.circularProgress)
        tvVolumePercent = findViewById(R.id.tvVolumePercent)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        diskContainer = findViewById(R.id.diskContainer)

        sbSystemVolume.max = maxSystemVolume
        
        // Ініціалізація нативного підсилювача звуку
        try {
            loudnessEnhancer = LoudnessEnhancer(0)
            Log.d(TAG, "LoudnessEnhancer успішно ініціалізовано.")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка аудіо-двигуна: ${e.message}")
        }

        // Оновлюємо початковий стан під поточний звук системи
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateAllComponents((currentVol.toFloat() / maxSystemVolume * 100).toInt(), false)

        // Обробка класичного повзунка системи
        sbSystemVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pct = (progress.toFloat() / maxSystemVolume * 100).toInt()
                    updateAllComponents(pct, false)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Робимо диск інтерактивною крутилкою за допомогою жестів
        setupRotationGesture()

        // Кнопки швидкого вибору
        setupPresetButtons()
    }

    private fun setupRotationGesture() {
        diskContainer.setOnTouchListener { view, event ->
            val centerX = view.width / 2f
            val centerY = view.height / 2f
            
            val x = event.x - centerX
            val y = event.y - centerY

            when (event.action) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                    // Рахуємо кут дотику в радіанах, переводимо в градуси (від -180 до 180)
                    var angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble()).toDouble()).toFloat()
                    
                    // Зсуваємо кут так, щоб 0 градусів було знизу
                    angle += 90
                    if (angle < 0) angle += 360

                    // Переводимо 360 градусів у шкалу від 0% до 200% гучності
                    val targetPercent = ((angle / 360f) * 200).toInt()
                    
                    if (targetPercent in 0..200) {
                        updateAllComponents(targetPercent, true)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateAllComponents(percent: Int, fromDisk: Boolean) {
        var pct = percent
        if (pct < 0) pct = 0
        if (pct > 200) pct = 200

        // 1. Оновлюємо графіку крутилки
        circularProgress.progress = pct
        tvVolumePercent.text = "$pct%"

        // 2. Розраховуємо гучність системи та буст
        if (pct <= 100) {
            // Стандартний режим: міняємо звичайний звук пристрою
            val sysVol = (pct.toFloat() / 100 * maxSystemVolume).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
            if (!fromDisk) sbSystemVolume.progress = sysVol
            
            // Вимикаємо буст
            setNativeBoost(0)
            tvStatusLabel.text = "STANDARD"
            tvStatusLabel.setTextColor(android.graphics.Color.parseColor("#888599"))
            circularProgress.setIndicatorColor(android.graphics.Color.parseColor("#00F2FE"))
        } else {
            // Режим Бустера (101% - 200%): звук системи на максимум + викручуємо дБ
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
            sbSystemVolume.progress = maxSystemVolume
            
            // Розрахунок посилення: кожні 1% понад сотню додають 45mB (Макс +4500 mB = дуже гучно!)
            val boostValue = (pct - 100) * 45
            setNativeBoost(boostValue)
            
            tvStatusLabel.text = "BOOST ACTIVE"
            tvStatusLabel.setTextColor(android.graphics.Color.parseColor("#FF007F"))
            circularProgress.setIndicatorColor(android.graphics.Color.parseColor("#FF007F"))
        }
    }

    private fun setNativeBoost(boostmB: Int) {
        try {
            loudnessEnhancer?.let { enhancer ->
                if (boostmB > 0) {
                    enhancer.setTargetGain(boostmB)
                    enhancer.enabled = true
                } else {
                    enhancer.enabled = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не вдалося змінити буст: ${e.message}")
        }
    }

    private fun setupPresetButtons() {
        findViewById<Button>(R.id.btnMute).setOnClickListener { updateAllComponents(0, false) }
        findViewById<Button>(R.id.btn30).setOnClickListener { updateAllComponents(30, false) }
        findViewById<Button>(R.id.btn100).setOnClickListener { updateAllComponents(100, false) }
        findViewById<Button>(R.id.btnMax).setOnClickListener { updateAllComponents(200, false) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            Log.e(TAG, "Помилка очищення пам'яті: ${e.message}")
        }
    }
}
