package com.kexora.soniclet.utils

import android.content.Context
import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Coil Transformation that blurs images using pure-Kotlin StackBlur.
 * Perfect for older Android devices (< Android 12) where Jetpack Compose
 * Modifier.blur() is an unsupported no-op.
 */
class CompatBlurTransformation(
    private val context: Context,
    private val radius: Int = 16,
    private val sampling: Float = 4f
) : Transformation {

    override val cacheKey: String = "${CompatBlurTransformation::class.java.name}_${radius}_${sampling}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val blurred = FastBlurUtil.blurBitmap(input, radius = radius, sampling = sampling)
        return blurred ?: input
    }
}
