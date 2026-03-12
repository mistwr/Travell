package com.luminai.travel

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import androidx.preference.PreferenceManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.luminai.travel.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * LUMINAI Travel - MainActivity
 *
 * Orchestrates the map, LUMIN AI assistant, geocoding service, and
 * mock location service. Provides a futuristic dark interface for
 * teleporting your GPS to any location on Earth.
 */
class MainActivity : AppCompatActivity() {

    // ── View binding ──────────────────────────────────────────────────────────
    private lateinit var binding: ActivityMainBinding

    // ── Services ──────────────────────────────────────────────────────────────
    private var mockLocationService: MockLocationService? = null
    private var serviceBound = false
    private val geocodingService = GeocodingService()
    private val luminAI = LuminAIAssistant()

    // ── Map ───────────────────────────────────────────────────────────────────
    private var locationMarker: Marker? = null
    private var currentGeoPoint = GeoPoint(38.7169, -9.1399) // Default: Lisbon

    // ── Permission request codes ──────────────────────────────────────────────
    private val PERMISSION_REQUEST_CODE = 1001

    // ── Service connection ────────────────────────────────────────────────────
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MockLocationService.LocalBinder
            mockLocationService = localBinder.getService()
            serviceBound = true
            updateStatusIndicator(mockLocationService?.isRunning() == true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mockLocationService = null
            serviceBound = false
            updateStatusIndicator(false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init osmdroid configuration (must be before layout inflation)
        Configuration.getInstance().apply {
            load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
            userAgentValue = packageName
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        initMap()
        initUI()
        bindMockService()
        showWelcomeMessage()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
                Toast.makeText(this, "Location permissions required for mock GPS", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Map Initialization
    // ─────────────────────────────────────────────────────────────────────────

    private fun initMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0

            // Dark overlay filter — use ColorMatrixColorFilter to invert map tiles
            val matrix = android.graphics.ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f,  0f, 1f,   0f
            ))
            overlayManager.tilesOverlay.setColorFilter(
                android.graphics.ColorMatrixColorFilter(matrix)
            )

            controller.apply {
                setZoom(13.0)
                setCenter(currentGeoPoint)
            }
        }

        // Place initial marker
        placeMarker(currentGeoPoint, "LUMIN AI — Ready to travel")

        // Allow tapping map to set location
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { onMapTapped(it) }
                return true
            }
            override fun longPressHelper(p: GeoPoint?) = false
        }
        binding.mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))
    }

    private fun placeMarker(geoPoint: GeoPoint, title: String) {
        // Remove old marker
        locationMarker?.let { binding.mapView.overlays.remove(it) }

        // Create new futuristic marker
        locationMarker = Marker(binding.mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location_marker)
        }
        binding.mapView.overlays.add(locationMarker)
        binding.mapView.invalidate()
    }

    private fun animateMapToLocation(geoPoint: GeoPoint) {
        binding.mapView.controller.animateTo(geoPoint, 15.0, 1200L)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Initialization
    // ─────────────────────────────────────────────────────────────────────────

    private fun initUI() {
        // Search bar: trigger on keyboard "Done" / "Go"
        binding.searchEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                processLocationInput()
                true
            } else false
        }

        // Search button
        binding.btnSearch.setOnClickListener { processLocationInput() }

        // Travel / Teleport button
        binding.btnTravel.setOnClickListener { teleportToCurrentLocation() }

        // Stop mock location
        binding.btnStop.setOnClickListener { stopMockLocation() }

        // LUMIN AI assistant panel toggle
        binding.btnLumin.setOnClickListener { toggleLuminPanel() }

        // Mock location helper button
        binding.btnMockHelper.setOnClickListener { showMockLocationHelper() }
    }

    private fun showWelcomeMessage() {
        appendAIMessage(
            "🌐 LUMIN AI online.\n\n" +
            "Enter any city, address, or coordinates to teleport your GPS.\n\n" +
            "Examples:\n" +
            "• \"Eiffel Tower, Paris\"\n" +
            "• \"Times Square, New York\"\n" +
            "• \"48.8566, 2.3522\"\n\n" +
            "⚠ Enable Mock Location in Developer Options first."
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Location Input Processing
    // ─────────────────────────────────────────────────────────────────────────

    private fun processLocationInput() {
        val input = binding.searchEditText.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter a location first", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        showLoading(true)
        appendAIMessage("🔍 Processing: \"$input\"")

        lifecycleScope.launch {
            try {
                // Let LUMIN AI interpret the input
                val result = luminAI.interpretLocation(input, geocodingService)

                if (result != null) {
                    currentGeoPoint = GeoPoint(result.latitude, result.longitude)

                    // Update map
                    placeMarker(currentGeoPoint, result.displayName)
                    animateMapToLocation(currentGeoPoint)

                    // Update coordinates display
                    updateCoordinatesDisplay(result.latitude, result.longitude)

                    // Start mock location automatically
                    startMockLocation(result.latitude, result.longitude)

                    // AI response
                    appendAIMessage(
                        "✅ Location set to:\n${result.displayName}\n\n" +
                        "📍 ${formatCoords(result.latitude, result.longitude)}\n\n" +
                        "🛰 Mock GPS active. Marker updated on map."
                    )

                    binding.btnTravel.visibility = View.VISIBLE
                    binding.btnStop.visibility = View.VISIBLE
                } else {
                    appendAIMessage(
                        "❌ Could not locate \"$input\".\n\n" +
                        "Try a different format:\n" +
                        "• City name: \"Tokyo\"\n" +
                        "• Full address: \"1 Infinite Loop, Cupertino\"\n" +
                        "• Coordinates: \"35.6762, 139.6503\""
                    )
                }
            } catch (e: Exception) {
                appendAIMessage("⚠ Error: ${e.message}\n\nCheck internet connection.")
            } finally {
                showLoading(false)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Map Tap Handler
    // ─────────────────────────────────────────────────────────────────────────

    private fun onMapTapped(geoPoint: GeoPoint) {
        currentGeoPoint = geoPoint
        val lat = geoPoint.latitude
        val lon = geoPoint.longitude

        placeMarker(geoPoint, "Selected: ${formatCoords(lat, lon)}")
        updateCoordinatesDisplay(lat, lon)

        // Reverse geocode for display
        lifecycleScope.launch {
            val name = geocodingService.reverseGeocode(lat, lon) ?: formatCoords(lat, lon)
            placeMarker(geoPoint, name)
            appendAIMessage("📌 Map tap detected.\n$name\n${formatCoords(lat, lon)}\n\nPress 'TRAVEL' to teleport GPS here.")
        }

        binding.btnTravel.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock Location Control
    // ─────────────────────────────────────────────────────────────────────────

    private fun startMockLocation(lat: Double, lon: Double) {
        if (!isMockLocationEnabled()) {
            showMockLocationHelper()
            return
        }

        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra(MockLocationService.EXTRA_LATITUDE, lat)
            putExtra(MockLocationService.EXTRA_LONGITUDE, lon)
            action = MockLocationService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)

        if (serviceBound) {
            mockLocationService?.updateLocation(lat, lon)
        }

        updateStatusIndicator(true)
    }

    private fun teleportToCurrentLocation() {
        startMockLocation(currentGeoPoint.latitude, currentGeoPoint.longitude)
        appendAIMessage(
            "🚀 Teleporting GPS to:\n${formatCoords(currentGeoPoint.latitude, currentGeoPoint.longitude)}\n\n" +
            "Mock location is now active. Your device GPS reports this position."
        )
    }

    private fun stopMockLocation() {
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        startService(intent)
        updateStatusIndicator(false)
        binding.btnStop.visibility = View.GONE
        appendAIMessage("⏹ Mock GPS deactivated. Returning to real location.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock Location Check
    // ─────────────────────────────────────────────────────────────────────────

    private fun isMockLocationEnabled(): Boolean {
        return try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // On Android 6+, app must be set as mock location app in dev settings
            // We attempt a test — if it throws SecurityException, we're not set
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            ) != "0"
        } catch (e: Exception) {
            true // Assume enabled and let the service handle errors
        }
    }

    private fun showMockLocationHelper() {
        AlertDialog.Builder(this)
            .setTitle("⚙ Mock Location Setup")
            .setMessage(
                "To use LUMINAI Travel, you need to:\n\n" +
                "1. Enable Developer Options:\n" +
                "   Settings → About Phone → tap 'Build Number' 7 times\n\n" +
                "2. Set Mock Location App:\n" +
                "   Settings → Developer Options → Select mock location app → Choose 'LUMINAI Travel'\n\n" +
                "3. Return here and search for a location."
            )
            .setPositiveButton("Open Developer Options") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Open Settings → Developer Options manually", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Got it", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service Binding
    // ─────────────────────────────────────────────────────────────────────────

    private fun bindMockService() {
        val intent = Intent(this, MockLocationService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun appendAIMessage(message: String) {
        runOnUiThread {
            val current = binding.tvAiResponse.text.toString()
            binding.tvAiResponse.text = if (current.isEmpty()) message else "$message\n\n─────\n\n$current"
            binding.scrollAiResponse.post {
                binding.scrollAiResponse.smoothScrollTo(0, 0)
            }
            // Show AI panel if hidden
            if (binding.cardAiPanel.visibility != View.VISIBLE) {
                binding.cardAiPanel.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCoordinatesDisplay(lat: Double, lon: Double) {
        runOnUiThread {
            binding.tvCoordinates.text = formatCoords(lat, lon)
            binding.tvCoordinates.visibility = View.VISIBLE
        }
    }

    private fun updateStatusIndicator(active: Boolean) {
        runOnUiThread {
            if (active) {
                binding.statusIndicator.setColorFilter(Color.parseColor("#00E5FF"))
                binding.tvStatus.text = "MOCK GPS ACTIVE"
                binding.tvStatus.setTextColor(Color.parseColor("#00E5FF"))
            } else {
                binding.statusIndicator.setColorFilter(Color.parseColor("#444444"))
                binding.tvStatus.text = "STANDBY"
                binding.tvStatus.setTextColor(Color.parseColor("#888888"))
            }
        }
    }

    private fun showLoading(show: Boolean) {
        runOnUiThread {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnSearch.isEnabled = !show
        }
    }

    private fun toggleLuminPanel() {
        binding.cardAiPanel.visibility = if (binding.cardAiPanel.visibility == View.VISIBLE)
            View.GONE else View.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun formatCoords(lat: Double, lon: Double): String {
        return "%.6f, %.6f".format(lat, lon)
    }
}
