package com.example.ezbooster

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val TAG = "EZBoosterLog"
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var sbSystemVolume: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "== Додаток запущено ==")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sbSystemVolume = findViewById(R.id.sbSystemVolume)

        try {
            // Намагаємося підключитися до глобального аудіопотоку
            loudnessEnhancer = LoudnessEnhancer(0)
            Log.d(TAG, "LoudnessEnhancer успішно підключено до сесії 0")
        } catch (e: Exception) {
            Log.e(TAG, "Критична помилка ініціалізації звуку: ${e.message}")
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        sbSystemVolume.max = maxVolume
        sbSystemVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.d(TAG, "Системний максимум гучності: $maxVolume")

        sbSystemVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    Log.d(TAG, "Повзунок системи змінено на: $progress")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setupButtons(maxVolume)
    }

    private fun setupButtons(maxSystemVol: Int) {
        Log.d(TAG, "Прив'язка кнопок керування")

        findViewById<Button>(R.id.btnMute).setOnClickListener { setVolumeAndBoost(0, 0) }
        findViewById<Button>(R.id.btn30).setOnClickListener { setVolumeAndBoost((maxSystemVol * 0.3).toInt(), 0) }
        findViewById<Button>(R.id.btn60).setOnClickListener { setVolumeAndBoost((maxSystemVol * 0.6).toInt(), 0) }
        findViewById<Button>(R.id.btn100).setOnClickListener { setVolumeAndBoost(maxSystemVol, 0) }

        // Відчутні рівні бусту в mB (2000 mB = +2дБ, 4000 mB = +4дБ і т.д.)
        findViewById<Button>(R.id.btn125).setOnClickListener { setVolumeAndBoost(maxSystemVol, 1500) }
        findViewById<Button>(R.id.btn150).setOnClickListener { setVolumeAndBoost(maxSystemVol, 3000) }
        findViewById<Button>(R.id.btn175).setOnClickListener { setVolumeAndBoost(maxSystemVol, 4500) }
        findViewById<Button>(R.id.btnMax).setOnClickListener { setVolumeAndBoost(maxSystemVol, 6000) }
    }

    private fun setVolumeAndBoost(systemVolume: Int, boostmB: Int) {
        Log.d(TAG, "Запит на зміну: Гучність системи=$systemVolume, Буст=$boostmB mB")

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
        sbSystemVolume.progress = systemVolume

        try {
            loudnessEnhancer?.let { enhancer ->
                if (boostmB > 0) {
                    enhancer.setTargetGain(boostmB)
                    enhancer.enabled = true
                    Log.d(TAG, "=> Буст АКТИВОВАНО: +$boostmB mB (Поточний статус: ${enhancer.enabled})")
                } else {
                    enhancer.enabled = false
                    Log.d(TAG, "=> Буст ВИМКНЕНО (Стандартний звук)")
                }
            } ?: Log.e(TAG, "Не вдалося застосувати ефект: loudnessEnhancer є null!")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка при застосуванні бусту: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "== Закриття додатка: очищення ресурсів ==")
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            Log.d(TAG, "LoudnessEnhancer успішно звільнено")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка звільнення пам'яті: ${e.message}")
        }
    }
}
