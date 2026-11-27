package com.kexora.soniclet.gestures

import com.kexora.soniclet.audio.*
import com.kexora.soniclet.service.*


import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

class SmartMotionManager(private val context: Context) : SensorEventListener, android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val prefs = context.getSharedPreferences("soniclet_audio_prefs", Context.MODE_PRIVATE)

    private var isListening = false
    private var isStarted = false

    // State trackers
    private var lastShakeTime = 0L
    private var lastProximityNearTime = 0L
    private var isProximityNear = false
    private var lastFlipTime = 0L
    private var lastWristTwistTime = 0L
    private var lastPocketModeTime = 0L

    fun start() {
        if (!isStarted) {
            isStarted = true
            prefs.registerOnSharedPreferenceChangeListener(this)
            evaluateRegistration()
        }
    }

    fun stop() {
        if (isStarted) {
            isStarted = false
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            unregisterSensors()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        if (key != null && key.startsWith("setting_")) {
            evaluateRegistration()
        }
    }

    private fun evaluateRegistration() {
        if (!isStarted) return

        val anyEnabled = prefs.getBoolean("setting_shake_enabled", false) ||
                prefs.getBoolean("setting_flip_to_pause_enabled", false) ||
                prefs.getBoolean("setting_wrist_twist_enabled", false) ||
                prefs.getBoolean("setting_pocket_mode_enabled", false) ||
                prefs.getBoolean("setting_air_wave_enabled", false) ||
                prefs.getBoolean("setting_pick_up_to_play_enabled", false)

        if (anyEnabled) {
            registerSensors()
        } else {
            unregisterSensors()
        }
    }

    private fun registerSensors() {
        if (!isListening) {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            isListening = true
        }
    }

    private fun unregisterSensors() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_PROXIMITY -> handleProximity(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()
        val now = System.currentTimeMillis()

        // 1. Shake Detection
        if (prefs.getBoolean("setting_shake_enabled", false)) {
            if (gForce > 2.2f) {
                if (now - lastShakeTime > 1000) {
                    lastShakeTime = now
                    val actionStr = prefs.getString("setting_shake_action", "Shuffle") ?: "Shuffle"
                    triggerAction(actionStr)
                }
            }
        }

        // 2. Flip Face-Down (z is near -1, face is pointing down)
        if (prefs.getBoolean("setting_flip_to_pause_enabled", false)) {
            if (gZ < -0.8f && gForce < 1.2f) {
                if (now - lastFlipTime > 2000) {
                    lastFlipTime = now
                    val actionStr = prefs.getString("setting_flip_to_pause_action", "Pause") ?: "Pause"
                    triggerAction(actionStr)
                }
            }
        }

        // 3. Wrist Twist (rapid x movement)
        if (prefs.getBoolean("setting_wrist_twist_enabled", false)) {
            if (kotlin.math.abs(gX) > 2.0f && gForce > 2.0f && gForce < 3.0f) {
                if (now - lastWristTwistTime > 1500) {
                    lastWristTwistTime = now
                    val actionStr = prefs.getString("setting_wrist_twist_action", "Favorite") ?: "Favorite"
                    triggerAction(actionStr)
                }
            }
        }

        // 4. Pocket Mode (proximity near + y is strongly negative, i.e., phone is upside down in pocket)
        if (prefs.getBoolean("setting_pocket_mode_enabled", false)) {
            if (isProximityNear && gY < -0.7f && gForce < 1.2f) {
                if (now - lastPocketModeTime > 2000) {
                    lastPocketModeTime = now
                    val actionStr = prefs.getString("setting_pocket_mode_action", "Auto-Play/Pause") ?: "Auto-Play/Pause"
                    triggerAction(actionStr)
                }
            }
        }
    }

    private fun handleProximity(event: SensorEvent) {
        val distance = event.values[0]
        val isNear = distance < (proximity?.maximumRange?.coerceAtMost(5f) ?: 5f)
        val now = System.currentTimeMillis()

        if (isNear && !isProximityNear) {
            isProximityNear = true
            lastProximityNearTime = now
        } else if (!isNear && isProximityNear) {
            isProximityNear = false
            val timeNear = now - lastProximityNearTime

            // Air Wave (Wave hand quickly over sensor)
            if (prefs.getBoolean("setting_air_wave_enabled", false)) {
                if (timeNear in 50..600) {
                    val actionStr = prefs.getString("setting_air_wave_action", "Play/Pause") ?: "Play/Pause"
                    triggerAction(actionStr)
                    return
                }
            }

            // Pick Up to Play (Was near for a long time, now picked up)
            if (prefs.getBoolean("setting_pick_up_to_play_enabled", false)) {
                if (timeNear > 1000) {
                    // Send Play action instead of toggle to ensure it resumes
                    val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_PLAY_PAUSE
                    }
                    context.sendBroadcast(intent)
                }
            }
        }
    }

    private fun triggerAction(actionString: String) {
        val intentAction = when (actionString) {
            "Play/Pause", "Auto-Play/Pause", "Pause" -> NotificationActionReceiver.ACTION_PLAY_PAUSE
            "Next", "Next Track", "Next/Previous" -> NotificationActionReceiver.ACTION_NEXT
            "Previous" -> NotificationActionReceiver.ACTION_PREVIOUS
            "Shuffle" -> NotificationActionReceiver.ACTION_SHUFFLE
            "Favorite" -> NotificationActionReceiver.ACTION_FAVORITE
            else -> NotificationActionReceiver.ACTION_PLAY_PAUSE
        }
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = intentAction
        }
        context.sendBroadcast(intent)
    }
}
