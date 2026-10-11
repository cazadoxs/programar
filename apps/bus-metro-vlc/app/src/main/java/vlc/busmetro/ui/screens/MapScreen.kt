package vlc.busmetro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import vlc.busmetro.R
import vlc.busmetro.app
import vlc.busmetro.data.StopEntity
import vlc.busmetro.ui.Locator
import vlc.busmetro.ui.theme.Palette
import vlc.transit.core.util.Polyline

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val VALENCIA = LatLng(39.4699, -0.3763)


/**
 * Map of every stop, with the selected line drawn on top and live Valenbisi
 * stations. Tapping a stop opens its board.
 */
@Composable
fun MapScreen(onStop: (String) -> Unit, focusStop: String? = null, highlight: Pair<String, String>? = null) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settings by app.prefs.settings.collectAsState(initial = vlc.busmetro.data.Settings())
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var allStops by remember { mutableStateOf<List<StopEntity>>(emptyList()) }
    var zoomHint by remember { mutableStateOf(false) }
    val mapView = remember { MapLibre.getInstance(context); MapView(context) }

    DisposableEffect(lifecycle, mapView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        mapView.onCreate(null)
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose { lifecycle.removeObserver(obs); mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
    }

    fun stopsGeoJson(m: MapLibreMap): FeatureCollection {
        val b = m.projection.visibleRegion.latLngBounds
        val z = m.cameraPosition.zoom
        zoomHint = z < 13
        val feats = allStops.asSequence()
            .filter { s -> (z >= 13 || s.network != "bus") && s.lat in b.latitudeSouth..b.latitudeNorth && s.lon in b.longitudeWest..b.longitudeEast }
            .take(1500)
            .map { s -> Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat)).apply { addStringProperty("key", s.key); addStringProperty("net", s.network); addStringProperty("name", s.name) } }
            .toList()
        return FeatureCollection.fromFeatures(feats)
    }

    fun refreshSources(m: MapLibreMap) {
        m.getStyle { st ->
            (st.getSource("stops") as? GeoJsonSource)?.setGeoJson(stopsGeoJson(m))
            if (settings.showBikes && m.cameraPosition.zoom >= 13) scope.launch {
                val bikes = runCatching { app.repo.bikes().ifEmpty { app.repo.refreshBikes() } }.getOrDefault(emptyList())
                val fc = FeatureCollection.fromFeatures(bikes.map { b ->
                    Feature.fromGeometry(Point.fromLngLat(b.lon, b.lat)).apply { addStringProperty("key", "bike:${b.id}"); addNumberProperty("bikes", b.bikes) }
                })
                (st.getSource("bikes") as? GeoJsonSource)?.setGeoJson(fc)
            } else (st.getSource("bikes") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        }
    }

    LaunchedEffect(Unit) { allStops = app.repo.allStops() }
    LaunchedEffect(allStops, settings.showBikes) { map?.let { if (allStops.isNotEmpty()) refreshSources(it) } }

    // Line overlay
    LaunchedEffect(map, highlight) {
        val m = map ?: return@LaunchedEffect
        val h = highlight ?: return@LaunchedEffect
        val patterns = app.repo.patterns(h.first, h.second)
        val styleColor = app.repo.styles(h.first)[h.second]?.color
        val lines = patterns.mapNotNull { it.shape }.map { enc -> Feature.fromGeometry(LineString.fromLngLats(Polyline.decode(enc).map { (la, lo) -> Point.fromLngLat(lo, la) })) }
        m.getStyle { st ->
            (st.getSource("line") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(lines))
            st.getLayer("line-layer")?.setProperties(lineColor(styleColor ?: AColor.parseColor("#FF6B1A")))
        }
        val pts = patterns.flatMap { it.shape?.let(Polyline::decode).orEmpty() }
        if (pts.isNotEmpty()) {
            val bounds = org.maplibre.android.geometry.LatLngBounds.Builder().includes(pts.map { LatLng(it.first, it.second) }).build()
            m.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 600)
        }
    }

    LaunchedEffect(map, focusStop) {
        val m = map ?: return@LaunchedEffect
        val s = focusStop?.let { app.repo.stop(it) } ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lon), 16.0), 600)
    }

    var locGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val locate = {
        scope.launch {
            Locator.current(context)?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15.5), 700) }
        }
        Unit
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        locGranted = r.values.any { it }
        if (locGranted) locate()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = {
            mapView.apply {
                getMapAsync { m ->
                    map = m
                    m.uiSettings.isRotateGesturesEnabled = false
                    m.uiSettings.isLogoEnabled = false
                    m.cameraPosition = CameraPosition.Builder().target(VALENCIA).zoom(12.3).build()
                    m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st ->
                        st.addSource(GeoJsonSource("line", FeatureCollection.fromFeatures(emptyList<Feature>())))
                        st.addLayer(LineLayer("line-layer", "line").withProperties(lineWidth(5f), lineColor(AColor.parseColor("#FF6B1A")), lineOpacity(0.9f), org.maplibre.android.style.layers.PropertyFactory.lineCap("round"), org.maplibre.android.style.layers.PropertyFactory.lineJoin("round")))
                        st.addSource(GeoJsonSource("stops", FeatureCollection.fromFeatures(emptyList<Feature>())))
                        st.addLayer(CircleLayer("stops-layer", "stops").withProperties(
                            circleRadius(interpolate(linear(), zoom(), stop(12, 3.5f), stop(16, 8f))),
                            circleColor(match(get("net"), literal("#FF6B1A"),
                                stop("bus", literal("#E8482C")), stop("metro", literal("#1E88E5")), stop("rail", literal("#8E44AD")))),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(1.5f)))
                        st.addSource(GeoJsonSource("bikes", FeatureCollection.fromFeatures(emptyList<Feature>())))
                        st.addLayer(CircleLayer("bikes-layer", "bikes").withProperties(
                            circleRadius(interpolate(linear(), zoom(), stop(13, 5f), stop(17, 11f))),
                            circleColor(step(get("bikes"), literal("#FF5A5F"), stop(1, literal("#FFC857")), stop(4, literal("#2EA66B")))),
                            circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                        refreshSources(m)
                    }
                    m.addOnCameraIdleListener { refreshSources(m) }
                    m.addOnMapClickListener { ll ->
                        val px = m.projection.toScreenLocation(ll)
                        val hit = m.queryRenderedFeatures(px, "stops-layer", "bikes-layer").firstOrNull()
                        val key = hit?.getStringProperty("key")
                        if (key != null) { onStop(key); true } else false
                    }
                }
            }
        }, modifier = Modifier.fillMaxSize())

        if (zoomHint) {
            Surface(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)) {
                Text(stringResource(R.string.map_zoom_hint), Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        FloatingActionButton(
            onClick = { if (locGranted) locate() else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Palette.Orange, contentColor = androidx.compose.ui.graphics.Color.White,
        ) { Icon(Icons.Filled.MyLocation, stringResource(R.string.my_location)) }
    }
}
