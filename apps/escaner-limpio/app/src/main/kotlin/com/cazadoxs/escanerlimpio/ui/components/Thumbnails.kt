package com.cazadoxs.escanerlimpio.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cazadoxs.escanerlimpio.data.DocEntry
import com.cazadoxs.escanerlimpio.ui.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ThumbCache : LruCache<String, ImageBitmap>(48)

@Composable
fun DocThumbnail(doc: DocEntry, modifier: Modifier = Modifier) {
    val store = app().store
    val key = "${doc.id}:${doc.sizeBytes}:${doc.pageCount}"
    var image by remember(key) { mutableStateOf(ThumbCache.get(key)) }
    LaunchedEffect(key) {
        if (image == null) {
            image = withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(store.thumbFile(doc.id).path)?.asImageBitmap()
                }.getOrNull()
            }?.also { ThumbCache.put(key, it) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else {
            Icon(doc.category.icon, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(8.dp))
        }
    }
}
