package life.mygig.clauderc.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import life.mygig.clauderc.api.ChatMessage
import life.mygig.clauderc.ui.MainViewModel

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private fun mimeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"
    "pdf" -> "application/pdf"; "apk" -> "application/vnd.android.package-archive"
    "txt", "md", "log" -> "text/plain"; "json" -> "application/json"; "csv" -> "text/csv"; "zip" -> "application/zip"
    else -> "application/octet-stream"
}

/** A file Claude sent to the Claude app: its caption, images as thumbnails, other files to save. */
@Composable
fun FileCard(vm: MainViewModel, m: ChatMessage) {
    Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 340.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (m.text.isNotBlank()) SelectionContainer { Text(linkify(m.text), style = MaterialTheme.typography.bodyMedium) }
            m.files.forEach { FileItem(vm, it) }
        }
    }
}

@Composable
private fun FileItem(vm: MainViewModel, path: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = path.substringAfterLast('/')
    val mime = mimeOf(name)
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val bytes = pending; pending = null
        if (uri != null && bytes != null) {
            scope.launch {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                vm.say("Saved $name")
            }
        }
    }
    var big by remember { mutableStateOf(false) }
    val isImage = name.substringAfterLast('.', "").lowercase() in IMAGE_EXT
    // null = still loading; Result.failure = couldn't get it
    val image by produceState<Result<Bitmap>?>(null, path) {
        if (!isImage) return@produceState
        value = runCatching {
            val bytes = vm.chatFileBytes(path)
            withContext(Dispatchers.Default) { decode(bytes, 1024) ?: error("not an image") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isImage) {
            val r = image
            when {
                r == null -> Text("Loading $name…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.isSuccess -> Image(
                    r.getOrThrow().asImageBitmap(), contentDescription = name, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { big = true },
                )
                else -> Text("Couldn't load $name: ${r.exceptionOrNull()?.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    runCatching { vm.chatFileBytes(path) }
                        .onSuccess { pending = it; save.launch(name) }
                        .onFailure { vm.say("Couldn't get $name: ${it.message}") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save $name", maxLines = 1) }
    }
    if (big) ImageViewer(vm, path) { big = false }
}

/** Full-screen, pinch to zoom, drag to move; tap Close to leave. */
@Composable
private fun ImageViewer(vm: MainViewModel, path: String, onClose: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, path) {
        value = runCatching { vm.chatFileBytes(path).let { withContext(Dispatchers.Default) { decode(it, 2560) } } }.getOrNull()
    }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Box(Modifier.fillMaxSize()) {
                bmp?.let {
                    Image(
                        it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offsetX = if (scale == 1f) 0f else offsetX + pan.x
                                    offsetY = if (scale == 1f) 0f else offsetY + pan.y
                                }
                            }
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                    )
                } ?: Text("Loading…", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center))
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text("Close", color = androidx.compose.ui.graphics.Color.White) }
            }
        }
    }
}

/** Decoded no bigger than about [max] px on its long side. */
private fun decode(bytes: ByteArray, max: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
