package dev.pixelforge.mobile.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

@Composable fun PixelForgeApp(vm: PixelForgeViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::install) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.reference(it) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(22.dp))
            Text("PixelForge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Photorealistic images, privately on your phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            if (!s.modelReady) ModelSetup(s, { modelPicker.launch(arrayOf("application/zip", "application/octet-stream")) })
            else {
                s.reference?.let { uri ->
                    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Image(loadBitmap(context, uri).asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton({ vm.reference(null) }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Outlined.Close, "Remove reference") }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(s.prompt, vm::prompt, Modifier.fillMaxWidth(), minLines = 4,
                    placeholder = { Text(if (s.reference == null) "Describe the photo you want…" else "Describe how the new photo should look…") },
                    shape = RoundedCornerShape(20.dp), enabled = !s.busy)
                Spacer(Modifier.height(12.dp))
                OutlinedButton({ imagePicker.launch("image/*") }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(if (s.reference == null) "Add reference photo" else "Change reference photo")
                }
                Spacer(Modifier.height(12.dp))
                Button(vm::generate, enabled = !s.busy && s.prompt.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (s.busy) "Creating…" else "Generate")
                }
                if (s.busy) { Spacer(Modifier.height(16.dp)); LinearProgressIndicator({ s.progress }, Modifier.fillMaxWidth()); Text(s.message.orEmpty(), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                s.image?.let { image -> Result(image, vm::generate, { save(context, image) }, { share(context, image) }) }
                s.message?.takeIf { !s.busy }?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                Spacer(Modifier.height(30.dp))
                Text("Offline after model setup • ${s.modelInfo?.name.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable private fun ModelSetup(s: ForgeState, choose: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(22.dp)) {
        Icon(Icons.Outlined.DownloadForOffline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp)); Text("One-time model setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Import the PixelForge ONNX model pack. It stays on this device; generation works without internet afterward.", modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(choose, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Choose model pack") }
        if (s.busy) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator({ s.progress }, Modifier.fillMaxWidth()) }
        s.message?.let { Text(it, Modifier.padding(top = 8.dp), color = if (s.busy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
    } }
}

@Composable private fun Result(image: Bitmap, again: () -> Unit, save: () -> Unit, share: () -> Unit) {
    Spacer(Modifier.height(24.dp)); Image(image.asImageBitmap(), "Generated image", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(save, Modifier.weight(1f)) { Icon(Icons.Outlined.SaveAlt, null); Text(" Save") }
        OutlinedButton(share, Modifier.weight(1f)) { Icon(Icons.Outlined.Share, null); Text(" Share") }
    }
    TextButton(again, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null); Text(" Generate again") }
}

private fun loadBitmap(context: android.content.Context, uri: android.net.Uri): Bitmap = context.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) }
private fun save(context: android.content.Context, bitmap: Bitmap) {
    val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "PixelForge-${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PixelForge") }
    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri -> context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
}
private fun share(context: android.content.Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }; val file = File(dir, "PixelForge.png"); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share image"))
}
