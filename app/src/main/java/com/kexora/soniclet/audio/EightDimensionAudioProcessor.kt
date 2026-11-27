package com.kexora.soniclet.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.sin

class EightDimensionAudioProcessor : BaseAudioProcessor() {

    var is8dEnabled = false
        set(value) {
            field = value
            if (!value) pan = 0f
        }

    private var pan: Float = 0f // -1.0 to 1.0
    private var angle: Double = 0.0
    
    // 8D Audio cycles every ~10 seconds.
    // Assuming 44100 Hz, 10 seconds is 441000 frames.
    // 2 * PI radians / 441000 frames = step size.
    private var angleStep: Double = (2 * Math.PI) / (44100.0 * 10.0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET // Only support stereo for simplicity
        }
        angleStep = (2 * Math.PI) / (inputAudioFormat.sampleRate.toDouble() * 10.0)
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return super.isActive() && is8dEnabled
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!is8dEnabled || inputAudioFormat.channelCount != 2) {
            val remaining = inputBuffer.remaining()
            if (remaining > 0) {
                val buffer = replaceOutputBuffer(remaining)
                buffer.put(inputBuffer)
                buffer.flip()
            }
            return
        }

        val limit = inputBuffer.limit()
        val remaining = inputBuffer.remaining()
        val numFrames = remaining / inputAudioFormat.bytesPerFrame
        
        val buffer = replaceOutputBuffer(remaining)

        when (inputAudioFormat.encoding) {
            androidx.media3.common.C.ENCODING_PCM_16BIT -> {
                for (i in 0 until numFrames) {
                    val leftMultiplier = if (pan > 0) 1f - pan else 1f
                    val rightMultiplier = if (pan < 0) 1f + pan else 1f

                    val left = inputBuffer.short
                    val right = inputBuffer.short
                    buffer.putShort((left * leftMultiplier).toInt().toShort())
                    buffer.putShort((right * rightMultiplier).toInt().toShort())
                    
                    angle += angleStep
                    if (angle > 2 * Math.PI) angle -= 2 * Math.PI
                    pan = sin(angle).toFloat()
                }
            }
            androidx.media3.common.C.ENCODING_PCM_FLOAT -> {
                for (i in 0 until numFrames) {
                    val leftMultiplier = if (pan > 0) 1f - pan else 1f
                    val rightMultiplier = if (pan < 0) 1f + pan else 1f

                    val left = inputBuffer.float
                    val right = inputBuffer.float
                    buffer.putFloat(left * leftMultiplier)
                    buffer.putFloat(right * rightMultiplier)
                    
                    angle += angleStep
                    if (angle > 2 * Math.PI) angle -= 2 * Math.PI
                    pan = sin(angle).toFloat()
                }
            }
            else -> {
                buffer.put(inputBuffer)
            }
        }
        
        inputBuffer.position(limit)
        buffer.flip()
    }
}
