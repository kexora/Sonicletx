package com.kexora.soniclet

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.kexora.soniclet.database.DatabaseProvider
import com.kexora.soniclet.library.AudioRepositoryImpl
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.worker.AudioScannerWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Custom Application class configured for ultra-low memory footprints.
 * Sets up Coil with conservative memory caching, RGB_565 bitmap decoding, and handles trim memory signals.
 */
@HiltAndroidApp
class SonicletApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    val musicViewModel: MusicViewModel by lazy {
        MusicViewModel(
            AudioRepositoryImpl(DatabaseProvider.getDatabase(this).songDao(), this),
            this
        )
    }

    private var globalImageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        scheduleAudioScanner()
        com.kexora.soniclet.worker.SpecialDateNotificationWorker.schedule(this)
    }

    private fun scheduleAudioScanner() {
        val workRequest = PeriodicWorkRequestBuilder<AudioScannerWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AudioScannerWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun newImageLoader(): ImageLoader {
        val loader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Reserve 15% of available heap for smooth scrolling on 1GB RAM devices
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("soniclet_art_cache"))
                    .maxSizeBytes(25L * 1024 * 1024) // 25 MB max disk cache
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // Halves RAM consumption compared to ARGB_8888
            .allowRgb565(true)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isEmulatorEnvironment())
            .crossfade(false) // Disable heavy crossfade transitions on budget GPUs
            .respectCacheHeaders(false)
            .components {
                add(com.kexora.soniclet.utils.AudioArtFetcher.Factory(this@SonicletApplication))
            }
            .build()
        globalImageLoader = loader
        return loader
    }

    private fun isEmulatorEnvironment(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("cutf") ||
            product.contains("sdk_gphone") ||
            product.contains("emulator") ||
            product.contains("cuttlefish")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // Evict bitmap caches when the device experiences memory pressure
            globalImageLoader?.memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        globalImageLoader?.memoryCache?.clear()
        System.gc()
    }
}
