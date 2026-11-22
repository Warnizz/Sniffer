package io.github.snifferapp.sniffer.ar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.snifferapp.sniffer.engine.SnifferEngine
import io.github.snifferapp.sniffer.engine.SnifferState
import io.github.snifferapp.sniffer.geo.bearingDeg
import io.github.snifferapp.sniffer.geo.distanceM
import io.github.snifferapp.sniffer.geo.elevationDeg
import io.github.snifferapp.sniffer.settings.Settings
import io.github.snifferapp.sniffer.settings.SettingsStore
import io.github.snifferapp.sniffer.towers.DbStatus
import io.github.snifferapp.sniffer.towers.Radio
import io.github.snifferapp.sniffer.towers.TowerSite
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.hypot

private const val MAX_BEACONS = 60

fun radioColor(r: Radio): Color = when (r) {
    Radio.GSM, Radio.CDMA -> Color(0xFFFFC107)
    Radio.UMTS -> Color(0xFFFF7043)
    Radio.LTE -> Color(0xFF26C6DA)
    Radio.NR -> Color(0xFFE040FB)
    Radio.UNKNOWN -> Color(0xFFB0BEC5)
}

fun formatDistance(m: Double): String = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000)

private class DrawnHolder { var items: List<Drawn> = emptyList() }

private class FovCache {
    private var key: Pair<Int, Int>? = null
    private var value = 60.0
    fun get(w: Int, h: Int, compute: () -> Double): Double {
        if (key != w to h) { value = compute(); key = w to h }
        return value
    }
}

private data class Drawn(val site: TowerSite, val x: Float, val y: Float, val dist: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArScreen(engine: SnifferEngine, settingsStore: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }

    DisposableEffect(Unit) {
        engine.setAr(true)
        onDispose { engine.setAr(false) }
    }

    val state by engine.state.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(Settings())
    val orientation = remember { OrientationSource(context) }
    val rotation by orientation.rotation().collectAsStateWithLifecycle(null)
    var selected by remember { mutableStateOf<TowerSite?>(null) }
    val drawn = remember { DrawnHolder() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraGranted) CameraPreview() else {
            Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }, Modifier.align(Alignment.Center)) {
                Text("Allow camera to see towers")
            }
        }
        BeaconOverlay(state, rotation, onDrawn = { drawn.items = it }, modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { tap ->
                selected = drawn.items.minByOrNull { hypot(it.x - tap.x, it.y - tap.y) }
                    ?.takeIf { hypot(it.x - tap.x, it.y - tap.y) < 80f }?.site
            }
        })
        Column(Modifier.safeDrawingPadding().fillMaxWidth().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back", color = Color.White) }
                Chip(statusText(state))
                TextButton(onClick = { scope.launch { settingsStore.update { it.copy(arMuted = !it.arMuted) } } }) {
                    Text(if (settings.arMuted) "Unmute" else "Mute", color = Color.White)
                }
            }
            if (state.user == null) Chip("Waiting for GPS…")
            if (rotation == null) Chip("No orientation sensor")
        }
    }

    selected?.let { site ->
        ModalBottomSheet(onDismissRequest = { selected = null }) { SiteDetails(site, state) }
    }
}

private fun statusText(s: SnifferState): String = when (s.dbStatus) {
    DbStatus.OK -> "${s.sites.size} towers nearby"
    DbStatus.NO_KEY -> "No OpenCelliD key"
    DbStatus.INVALID_KEY -> "OpenCelliD key rejected"
    DbStatus.RATE_LIMITED -> "OpenCelliD daily limit reached"
    DbStatus.OFFLINE_PARTIAL -> "${s.sites.size} towers (offline, cached)"
}

@Composable
private fun Chip(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.background(Color(0x99000000), MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun CameraPreview() {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    provider.unbindAll()
                    runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview) }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}

@Composable
private fun BeaconOverlay(state: SnifferState, rotation: FloatArray?, onDrawn: (List<Drawn>) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.6f, 1.4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    val labelPaint = remember { Paint().apply { color = android.graphics.Color.WHITE; textSize = 34f; isAntiAlias = true; setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK) } }
    val fovCache = remember { FovCache() }

    Canvas(modifier) {
        val user = state.user
        if (user == null || rotation == null) { onDrawn(emptyList()); return@Canvas }
        val w = size.width.toInt(); val h = size.height.toInt()
        val hFov = fovCache.get(w, h) { portraitHorizontalFovDeg(context, w, h) }
        val out = mutableListOf<Drawn>()
        state.sites
            .map { it to distanceM(user, it.pos) }
            .sortedBy { it.second }
            .take(MAX_BEACONS)
            .asReversed() // draw far first so near beacons end up on top
            .forEach { (site, d) ->
                val p = ArProjector.project(rotation, bearingDeg(user, site.pos), elevationDeg(d), w, h, hFov) ?: return@forEach
                val own = state.ownMccMnc?.let { it == site.mcc to site.mnc } ?: true
                val radio = site.cells.maxByOrNull { it.radio.ordinal }?.radio ?: Radio.UNKNOWN
                val near = exp(-d / 700.0).toFloat()
                val radius = 14f + 46f * near
                val alpha = (if (own) 1f else 0.35f) * (0.55f + 0.45f * near)
                val c = radioColor(radio)
                val center = Offset(p.x, p.y)
                drawCircle(c.copy(alpha = alpha * 0.35f), radius * 1.8f, center)
                drawCircle(c.copy(alpha = alpha), radius, center)
                if (state.matches.containsKey(site.id)) drawCircle(Color.White.copy(alpha = alpha), radius * 1.35f, center, style = Stroke(4f))
                if (state.servingSiteId == site.id) drawCircle(Color.White, radius * 1.6f * pulse, center, style = Stroke(6f))
                labelPaint.alpha = (alpha * 255).toInt()
                drawContext.canvas.nativeCanvas.drawText("${formatDistance(d)} · ${radio.label()}", p.x + radius + 8f, p.y + 12f, labelPaint)
                out += Drawn(site, p.x, p.y, d)
            }
        onDrawn(out)
    }
}

fun Radio.label() = when (this) { Radio.GSM -> "2G"; Radio.CDMA -> "2G CDMA"; Radio.UMTS -> "3G"; Radio.LTE -> "4G"; Radio.NR -> "5G"; Radio.UNKNOWN -> "?" }

@Composable
private fun SiteDetails(site: TowerSite, state: SnifferState) {
    val match = state.matches[site.id]
    val dist = state.user?.let { distanceM(it, site.pos) }
    Column(Modifier.padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tower · MCC ${site.mcc} / MNC ${site.mnc}", style = MaterialTheme.typography.titleLarge)
        dist?.let { Text("Distance: ${formatDistance(it)}") }
        if (state.servingSiteId == site.id) Text("Your phone is connected to this tower")
        if (match != null) Text(if (match.probable) "Heard live (probable match)" else "Heard live")
        val liveDbm = (listOfNotNull(state.live?.serving) + state.live?.neighbours.orEmpty())
            .firstOrNull { c -> site.cells.any { it.cellId == c.cellId && it.area == c.area } }?.dbm
        liveDbm?.let { Text("Live signal: $it dBm") }
        site.cells.forEach { c ->
            Text("${c.radio.label()} · ${if (c.radio == Radio.LTE || c.radio == Radio.NR) "TAC" else "LAC"} ${c.area} · cell ${c.cellId} · range ~${formatDistance(c.rangeM.toDouble())}",
                style = MaterialTheme.typography.bodySmall)
        }
        Text("Source: OpenCelliD (positions are estimates)", style = MaterialTheme.typography.labelSmall)
    }
}
