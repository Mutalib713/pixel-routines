@file:OptIn(ExperimentalMaterial3Api::class)

package com.mosman.routines

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.roundToInt

data class Place(val name: String, val lat: Double, val lng: Double)

/**
 * Full-screen place picker: search by name, tap the map, or use your current location —
 * so you never have to go copy coordinates out of Google Maps.
 *
 * Tiles come from OpenStreetMap via osmdroid, which needs no API key.
 */
@SuppressLint("MissingPermission")
@Composable
fun PlacePicker(
    initial: Trigger.Location?,
    onBack: () -> Unit,
    onDone: (place: String, lat: Double, lng: Double, radius: Float) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var point by remember { mutableStateOf(GeoPoint(initial?.lat ?: 6.6745, initial?.lng ?: -1.5716)) }
    var placeName by remember { mutableStateOf(initial?.place ?: "") }
    var radius by remember { mutableFloatStateOf(initial?.radius ?: 200f) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    fun moveTo(p: GeoPoint) { point = p; mapView?.controller?.animateTo(p) }

    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useCurrentLocation(ctx) { p -> moveTo(p); if (placeName.isBlank()) placeName = "My location" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a place") },
                navigationIcon = { IconButton(onClick = onBack) { BackIcon() } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = placeName, onValueChange = { placeName = it },
                        label = { Text("Name this place") },
                        placeholder = { Text("e.g. Campus") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Radius", fontSize = 14.sp)
                        Text("${radius.roundToInt()} m", fontWeight = FontWeight.SemiBold)
                    }
                    Slider(value = radius, onValueChange = { radius = it }, valueRange = 100f..1000f)
                    Button(
                        onClick = { onDone(placeName.ifBlank { "Place" }, point.latitude, point.longitude, radius) },
                        enabled = placeName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Use this place", fontWeight = FontWeight.Bold) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Search
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Search for a place") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = {
                    if (query.isBlank()) return@FilledTonalIconButton
                    searching = true
                    scope.launch {
                        results = geocode(ctx, query)
                        searching = false
                    }
                }) { Icon(Icons.Filled.Search, "Search") }
                FilledTonalIconButton(onClick = {
                    if (Permissions.hasFineLocation(ctx))
                        useCurrentLocation(ctx) { p -> moveTo(p); if (placeName.isBlank()) placeName = "My location" }
                    else locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Icon(Icons.Filled.MyLocation, "Use my location") }
            }

            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (results.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    items(results) { p ->
                        ListItem(
                            headlineContent = { Text(p.name, maxLines = 2) },
                            leadingContent = { Icon(Icons.Filled.LocationOn, null) },
                            modifier = Modifier.clickable {
                                moveTo(GeoPoint(p.lat, p.lng))
                                if (placeName.isBlank()) placeName = p.name.substringBefore(",")
                                results = emptyList(); query = ""
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }

            Text("Tap the map to drop the pin exactly where you want it.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Map
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { c ->
                    Configuration.getInstance().apply {
                        load(c, c.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                        userAgentValue = c.packageName   // OSM blocks default agents
                    }
                    MapView(c).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(point)
                        overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p?.let { point = it }
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint?) = false
                        }))
                        mapView = this
                    }
                },
                update = { map ->
                    // Redraw pin + radius circle for the current selection.
                    map.overlays.removeAll { it is Marker || it is Polygon }
                    map.overlays.add(Marker(map).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = placeName
                    })
                    map.overlays.add(Polygon().apply {
                        points = Polygon.pointsAsCircle(point, radius.toDouble())
                        fillPaint.color = 0x333F51B5
                        outlinePaint.color = 0xFF3F51B5.toInt()
                        outlinePaint.strokeWidth = 3f
                    })
                    map.invalidate()
                },
            )
        }
    }
}

/** Name → coordinates, using Android's built-in geocoder (no API key). */
private suspend fun geocode(ctx: Context, query: String): List<Place> = withContext(Dispatchers.IO) {
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(ctx).getFromLocationName(query, 6).orEmpty().map { a ->
            val label = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }
                .joinToString(", ").ifBlank { a.featureName ?: query }
            Place(label, a.latitude, a.longitude)
        }
    }.getOrDefault(emptyList())
}

@SuppressLint("MissingPermission")
private fun useCurrentLocation(ctx: Context, onFound: (GeoPoint) -> Unit) {
    runCatching {
        LocationServices.getFusedLocationProviderClient(ctx).lastLocation
            .addOnSuccessListener { loc -> loc?.let { onFound(GeoPoint(it.latitude, it.longitude)) } }
    }
}
