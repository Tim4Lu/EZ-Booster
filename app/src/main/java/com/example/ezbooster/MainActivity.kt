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
    private var currentPreset = "normal"
    private var lastPercent = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "== EZ Booster: Запуск системи фокусу звуку ==")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        sbSystemVolume = findViewById(R.id.sbSystemVolume)
        circularProgress = findViewById(R.id.circularProgress)
        tvVolumePercent = findViewById(R.id.tvVolumePercent)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        diskContainer = findViewById(R.id.diskContainer)

        sbSystemVolume.max = maxSystemVolume
        
        initAudioEffects()

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
        
        findViewById<Button>(R.id.btnEqNormal).performClick()
    }

    private fun initAudioEffects() {
        try {
            // Звільняємо старі ресурси, якщо вони були
            releaseAudioEffects()

            loudnessEnhancer = LoudnessEnhancer(0).apply { enabled = true }
            equalizer = Equalizer(0, 0).apply { enabled = true }
            bassBoost = BassBoost(0, 0).apply { enabled = true }
            Log.d(TAG, "Нативні ефекти успішно ініціалізовані для сесії 0.")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка ініціалізації ефектів: ${e.message}")
        }
    }

    private fun releaseAudioEffects() {
        try {
            loudnessEnhancer?.release(); loudnessEnhancer = null
            equalizer?.release(); equalizer = null
            bassBoost?.release(); bassBoost = null
        } catch (e: Exception) {
            Log.e(TAG, "Помилка звільнення ефектів: ${e.message}")
        }
    }

    private fun triggerAudioRoutingUpdate() {
        // Хак: робимо мікро-запит фокусу аудіо без призупинення іншої музики (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        // Це змушує аудіо-сервер Android перерахувати підключені ефекти сесії 0 прямо під час відтворення
        audioManager.requestAudioFocus(
            { }, 
            AudioManager.STREAM_MUSIC, 
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    private fun setupRotationGesture() {
        diskContainer.setOnTouchListener { view, event ->
            val centerX = view.width / 2f
            val centerY = view.height / 2f
            val x = event.x - centerX
            val y = event.y - centerY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // При першому дотику примусово «пінгуємо» аудіосистему
                    triggerAudioRoutingUpdate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
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
        lastPercent = percent
        var pct = percent
        if (pct < 0) pct = 0
        if (pct > 200) pct = 200

        circularProgress.progress = pct
        tvVolumePercent.text = "$pct%"

        // Перевіряємо працездатність ефектів. Якщо вони «злетіли» у фоні — перевизначаємо
        if (loudnessEnhancer == null) initAudioEffects()

        if (pct <= 100) {
            val sysVol = (pct.toFloat() / 100 * maxSystemVolume).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0)
            if (!fromDisk) sbSystemVolume.progress = sysVol
            
            setNativeBoost(800)
            
            tvStatusLabel.text = "STANDARD"
            tvStatusLabel.setTextColor(android.graphics.Color.parseColor("#888599"))
            circularProgress.setIndicatorColor(android.graphics.Color.parseColor("#00F2FE"))
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
            sbSystemVolume.progress = maxSystemVolume
            
            val boostValue = 800 + ((pct - 100) * 15)
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
                    if (!it.enabled) it.enabled = true
                } else {
                    it.enabled = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не вдалося змінити буст: ${e.message}")
            // Якщо вилетів ексЕпшн (сесія закрилася системою), пробуємо підняти заново
            initAudioEffects()
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
        currentPreset = preset
        activeEqButton?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        clickedButton.setTextColor(android.graphics.Color.parseColor("#00F2FE"))
        activeEqButton = clickedButton

        triggerAudioRoutingUpdate()
        if (equalizer == null) initAudioEffects()

        try {
            val eq = equalizer ?: return
            val bass = bassBoost ?: return

            bass.setStrength(0.toShort())

            when (preset) {
                "normal" -> {
                    for (i in 0 until eq.numberOfBands) {
                        eq.setBandLevel(i.toShort(), 200)
                    }
                }
                "bass" -> {
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, 900)
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, 700)
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, 200)
                    bass.setStrength(1000.toShort())
                }
                "rock" -> {
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, 700)
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, 400)
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, 100)
                    if (eq.numberOfBands > 3) eq.setBandLevel(3, 500)
                    if (eq.numberOfBands > 4) eq.setBandLevel(4, 800)
                }
                "voice" -> {
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, -200)
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, 100)
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, 900)
                    if (eq.numberOfBands > 3) eq.setBandLevel(3, 700)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка еквалайзера: ${e.message}")
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
        releaseAudioEffects()
    }
}
