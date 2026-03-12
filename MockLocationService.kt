package com.luminai.travel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * MockLocationService
 *
 * A foreground service that continuously pushes a fake GPS location
 * to the Android LocationManager at 1Hz. This follows the same approach
 * as FakeTraveler but with cleaner architecture and Kotlin coroutines.
 *
 * Requirements:
 *  - App must be selected as "Mock location app" in Developer Options
 *  - ACCESS_FINE_LOCATION permission
 *  - FOREGROUND_SERVICE_LOCATION (Android 14+)
 */
class MockLocationService : Service() {

    companion object {
        private const val TAG = "MockLocationService"

        // Notification
        private const val CHANNEL_ID = "luminai_mock_gps"
        private const val NOTIFICATION_ID = 7001

        // Actions
        const val ACTION_START = "com.luminai.travel.START_MOCK"
        const val ACTION_STOP  = "com.luminai.travel.STOP_MOCK"
        const val ACTION_UPDATE = "com.luminai.travel.UPDATE_MOCK"

        // Extras
        const val EXTRA_LATITUDE  = "extra_lat"
        const val EXTRA_LONGITUDE = "extra_lon"

        // Providers to spoof
        private val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        // Update interval in milliseconds
        private const val UPDATE_INTERVAL_MS = 500L
    }

    // ── Binder for bound clients (MainActivity) ───────────────────────────────
    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }

    private val binder = LocalBinder()

    // ── State ─────────────────────────────────────────────────────────────────
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var isRunning = false

    // ── Scheduler for periodic location updates ───────────────────────────────
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var scheduledTask: ScheduledFuture<*>? = null

    // ── Wake lock to keep service alive ───────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    // ── LocationManager ───────────────────────────────────────────────────────
    private lateinit var locationManager: LocationManager

    // ─────────────────────────────────────────────────────────────────────────
    // Service Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                startMockLocation(lat, lon)
            }
            ACTION_STOP -> {
                stopMockLocation()
                stopSelf()
            }
            ACTION_UPDATE -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, currentLat)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, currentLon)
                updateLocation(lat, lon)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopMockLocation()
        scheduler.shutdown()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock Location Control
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start spoofing GPS to [lat], [lon].
     * Registers test providers and starts the periodic push loop.
     */
    fun startMockLocation(lat: Double, lon: Double) {
        currentLat = lat
        currentLon = lon

        // Register test providers
        PROVIDERS.forEach { provider -> registerTestProvider(provider) }

        // Acquire wake lock
        acquireWakeLock()

        // Start foreground notification
        startForeground(NOTIFICATION_ID, buildNotification(lat, lon))

        // Cancel any existing task
        scheduledTask?.cancel(false)

        // Schedule periodic location pushes
        scheduledTask = scheduler.scheduleAtFixedRate(
            { pushLocation(currentLat, currentLon) },
            0L,
            UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )

        isRunning = true
        Log.i(TAG, "Mock location started: $lat, $lon")
    }

    /**
     * Update the spoofed coordinates without restarting everything.
     */
    fun updateLocation(lat: Double, lon: Double) {
        currentLat = lat
        currentLon = lon

        // Update notification
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(lat, lon))

        Log.d(TAG, "Location updated: $lat, $lon")
    }

    /**
     * Stop spoofing and clean up providers.
     */
    fun stopMockLocation() {
        scheduledTask?.cancel(false)
        scheduledTask = null
        isRunning = false

        PROVIDERS.forEach { provider -> removeTestProvider(provider) }
        releaseWakeLock()
        if (android.os.Build.VERSION.SDK_INT >= 24) { stopForeground(STOP_FOREGROUND_REMOVE) } else { @Suppress("DEPRECATION") stopForeground(true) }

        Log.i(TAG, "Mock location stopped")
    }

    fun isRunning() = isRunning

    fun getCurrentLat() = currentLat
    fun getCurrentLon() = currentLon

    // ─────────────────────────────────────────────────────────────────────────
    // Provider Management
    // ─────────────────────────────────────────────────────────────────────────

    private fun registerTestProvider(provider: String) {
        try {
            // Remove if already exists
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ API
                locationManager.addTestProvider(
                    provider,
                    /* requiresNetwork= */ false,
                    /* requiresSatellite= */ false,
                    /* requiresCell= */ false,
                    /* hasMonetaryCost= */ false,
                    /* supportsAltitude= */ true,
                    /* supportsSpeed= */ true,
                    /* supportsBearing= */ true,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false,
                    true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
            }

            locationManager.setTestProviderEnabled(provider, true)
            Log.d(TAG, "Test provider registered: $provider")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registering $provider — is mock location app set?", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering test provider $provider", e)
        }
    }

    private fun removeTestProvider(provider: String) {
        try {
            locationManager.setTestProviderEnabled(provider, false)
            locationManager.removeTestProvider(provider)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove provider $provider: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Location Push
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Push a single fake location to all registered providers.
     * Called on the scheduler thread at [UPDATE_INTERVAL_MS] Hz.
     */
    private fun pushLocation(lat: Double, lon: Double) {
        PROVIDERS.forEach { provider ->
            try {
                val location = Location(provider).apply {
                    latitude = lat
                    longitude = lon
                    altitude = 10.0
                    accuracy = 1.0f
                    speed = 0.0f
                    bearing = 0.0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = 1.0f
                        speedAccuracyMetersPerSecond = 0.1f
                        bearingAccuracyDegrees = 0.1f
                    }
                }
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: Exception) {
                // Provider may not be registered yet — silently ignore
                Log.v(TAG, "Push location failed for $provider: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LUMINAI Mock GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while mock GPS is active"
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(lat: Double, lon: Double): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛰 LUMINAI Travel — Mock GPS Active")
            .setContentText("📍 %.6f, %.6f".format(lat, lon))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wake Lock
    // ─────────────────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LUMINAITravel::MockGPSWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
