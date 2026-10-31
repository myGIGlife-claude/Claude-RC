package life.mygig.clauderc.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import life.mygig.clauderc.ui.components.AttachFile
import life.mygig.clauderc.ui.components.Photo
import life.mygig.clauderc.ui.components.InsertDriveFile
import life.mygig.clauderc.ui.vm.AttachQueue
import life.mygig.clauderc.ui.vm.AttachState
import life.mygig.clauderc.ui.vm.Attachment
import life.mygig.clauderc.ui.vm.PHOTO_PICK_MAX

/** The composer's paperclip: a small menu with Photos (the photo picker, many at once) and Files (any type). */
@Composable
internal fun AttachButton(enabled: Boolean, onPhotos: () -> Unit, onFiles: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Attach photos or files")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { MenuText("Photos", "Up to $PHOTO_PICK_MAX at once") },
                leadingIcon = { Icon(Icons.Filled.Photo, contentDescription = null) },
                onClick = { open = false; onPhotos() },
            )
            DropdownMenuItem(
                text = { MenuText("Files", "Any type, up to 15 MB each") },
                leadingIcon = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null) },
                onClick = { open = false; onFiles() },
            )
        }
    }
}

@Composable
private fun MenuText(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The picked files above the text box: a thumbnail each with its upload state (spinner, check,
 * red "!" to tap for a retry) and a × to drop it. A line on top says "Uploading 2 of 5…" while
 * files are on their way, or sums the strip up once it is too long to count.
 */
@Composable
internal fun AttachmentStrip(items: List<Attachment>, onRemove: (Long) -> Unit, onRetry: (Long) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        val hint = AttachQueue.uploadHint(items) ?: AttachQueue.summary(items)
            ?: items.count { it.state == AttachState.Failed }.takeIf { it > 0 }?.let { "$it couldn't upload: tap to retry" }
        hint?.let {
            Text(
                it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(items, key = { _, a -> a.id }) { i, a -> AttachmentItem(a, i, items.size, onRemove, onRetry) }
        }
    }
}

private val THUMB = 64.dp

@Composable
private fun AttachmentItem(a: Attachment, index: Int, total: Int, onRemove: (Long) -> Unit, onRetry: (Long) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val failed = a.state == AttachState.Failed
    val desc = AttachQueue.describe(a, index, total)
    // 88dp cell: the 64dp picture sits bottom-left, the × (a 48dp touch target) is centred on its top-right corner.
    Box(Modifier.size(88.dp)) {
        Box(
            Modifier.align(Alignment.BottomStart).size(THUMB).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (failed) Modifier.clickable(onClickLabel = "Retry the upload") { onRetry(a.id) } else Modifier)
                .semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            if (a.image) Thumbnail(a) else FileTile(a)
            when (a.state) {
                AttachState.Queued, AttachState.Uploading -> {
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (a.state == AttachState.Queued) 0.45f else 0.3f)))
                    CircularProgressIndicator(
                        Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp,
                        trackColor = Color.White.copy(alpha = if (a.state == AttachState.Queued) 0.15f else 0.3f),
                    )
                }
                AttachState.Ready -> Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
                AttachState.Failed -> {
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.error.copy(alpha = 0.6f)))
                    Text("!", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                }
            }
        }
        val what = if (a.image) "photo ${index + 1}" else a.displayName
        IconButton(onClick = { onRemove(a.id) }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
            Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Remove $what", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Thumbnails already made, by URI, so scrolling the strip back doesn't decode them again (about 6 MB). */
private val thumbCache = object : android.util.LruCache<String, ImageBitmap>(6 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = maxOf(1, value.width * value.height * 4 / 1024)
}

@Composable
private fun Thumbnail(a: Attachment) {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val px = with(LocalDensity.current) { THUMB.roundToPx() }
    val bmp by produceState<ImageBitmap?>(thumbCache.get(a.uri), a.uri, px) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { ChatImage.thumbnail(resolver, Uri.parse(a.uri), px)?.asImageBitmap() }
                ?.also { thumbCache.put(a.uri, it) }
        }
    }
    val b = bmp
    if (b != null) {
        Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(THUMB))
    } else {
        Icon(Icons.Filled.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun FileTile(a: Attachment) {
    Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            a.displayName, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
