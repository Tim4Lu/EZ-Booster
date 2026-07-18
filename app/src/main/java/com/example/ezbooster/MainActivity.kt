package com.example.ezbooster

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
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
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    
    private lateinit var audioManager: AudioManager
    private lateinit var sbSystemVolume: SeekBar
    private lateinit var circularProgress: CircularProgressIndicator
    private lateinit var tvVolumePercent: TextView
    private lateinit var tvStatusLabel: TextView
    private lateinit var diskContainer: FrameLayout

    private var maxSystemVolume = 15
    private var activeEqButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "== EZ Booster з Еквалайзером запущено ==")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        sbSystemVolume = findViewById(R.id.sbSystemVolume)
        circularProgress = findViewById(R.id.circularProgress)
        tvVolumePercent = findViewById(R.id.tvVolumePercent)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        diskContainer = findViewById(R.id.diskContainer)

        sbSystemVolume.max = maxSystemVolume
        
        // Ініціалізація аудіо-рушіїв
        try {
            loudnessEnhancer = LoudnessEnhancer(0)
            equalizer = Equalizer(0, 0).apply { enabled = true }
            bassBoost = BassBoost(0, 0).apply { enabled = true }
            Log.d(TAG, "Усі аудіо-ефекти (Booster, EQ, Bass) успішно підключені.")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка ініціалізації аудіо-ефектів: ${e.message}")
        }

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateAllComponents((currentVol.toFloat() / maxSystemVolume * 100).toInt(), false)

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

        setupRotationGesture()
        setupPresetButtons()
        setupEqualizerButtons()
    }

    private fun setupRotationGesture() {
        diskContainer.setOnTouchListener { view, event ->
            val centerX = view.width / 2f
            val centerY = view.height / 2f
            val x = event.x - centerX
            val y = event.y - centerY

            when (event.action) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                    var angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble()).toDouble()).toFloat()
                    angle += 90
                    if (angle < 0) angle += 360

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

        circularProgress.progress = pct
        tvVolumePercent.text = "$pct%"

        if (pct <= 100) {
            val sysVol = (pct.toFloat() / 100 * maxSystemVolume).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
            if (!fromDisk) sbSystemVolume.progress = sysVol
            
            setNativeBoost(0)
            tvStatusLabel.text = "STANDARD"
            tvStatusLabel.setTextColor(android.graphics.Color.parseColor("#888599"))
            circularProgress.setIndicatorColor(android.graphics.Color.parseColor("#00F2FE"))
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
            sbSystemVolume.progress = maxSystemVolume
            
            // Наш безпечний ліміт пом'якшеного бусту
            val boostValue = (pct - 100) * 15
            setNativeBoost(boostValue)
            
            tvStatusLabel.text = "BOOST ACTIVE"
            tvStatusLabel.setTextColor(android.graphics.Color.parseColor("#FF007F"))
            circularProgress.setIndicatorColor(android.graphics.Color.parseColor("#FF007F"))
        }
    }

    private fun setNativeBoost(boostmB: Int) {
        try {
            loudnessEnhancer?.let {
                if (boostmB > 0) {
                    it.setTargetGain(boostmB)
                    it.enabled = true
                } else {
                    it.enabled = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не вдалося застосувати буст: ${e.message}")
        }
    }

    private fun setupEqualizerButtons() {
        val btnNormal = findViewById<Button>(R.id.btnEqNormal)
        val btnBass = findViewById<Button>(R.id.btnEqBass)
        val btnRock = findViewById<Button>(R.id.btnEqRock)
        val btnVoice = findViewById<Button>(R.id.btnEqVoice)

        activeEqButton = btnNormal

        btnNormal.setOnClickListener { applyPreset(btnNormal, "normal") }
        btnBass.setOnClickListener { applyPreset(btnBass, "bass") }
        btnRock.setOnClickListener { applyPreset(btnRock, "rock") }
        btnVoice.setOnClickListener { applyPreset(btnVoice, "voice") }
    }

    private fun applyPreset(clickedButton: Button, preset: String) {
        Log.d(TAG, "Перемикання еквалайзера на пресет: $preset")
        
        // Візуальний фокус на активній кнопці
        activeEqButton?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        clickedButton.setTextColor(android.graphics.Color.parseColor("#00F2FE"))
        activeEqButton = clickedButton

        try {
            val eq = equalizer ?: return
            val bass = bassBoost ?: return

            // Вимикаємо супер-бас за замовчуванням
            bass.setRoundedStrength(0)

            when (preset) {
                "normal" -> {
                    // Скидаємо всі смуги частот в 0
                    for (i in 0 until eq.numberOfBands) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
                "bass" -> {
                    // Робимо акцент на низьких частотах + вмикаємо hardware BassBoost
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, 800) // 60Hz
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, 600) // 230Hz
                    bass.setRoundedStrength(1000) // Максимальний нативний бас
                }
                "rock" -> {
                    // Класична W-подібна крива (високі й низькі вгору, середина трохи вниз)
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, 600)
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, 300)
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, -200)
                    if (eq.numberOfBands > 3) eq.setBandLevel(3, 400)
                    if (eq.numberOfBands > 4) eq.setBandLevel(4, 700)
                }
                "voice" -> {
                    // Зрізаємо низькі частоти, піднімаємо середні частоти мови (1кГц - 4кГц)
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, -600)
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, -200)
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, 800)
                    if (eq.numberOfBands > 3) eq.setBandLevel(3, 600)
                }
            }
            Log.d(TAG, "Пресет успішно застосовано!")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка застосування еквалайзера: ${e.message}")
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
            loudnessEnhancer?.release()
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Помилка очищення ефектів: ${e.message}")
        }
    }
}
