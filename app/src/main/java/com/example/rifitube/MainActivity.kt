package com.example.rifitube

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.snapshots.SnapshotStateList
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
data class DownloadItem(
    val title: String,
    val format: String,
    val thumbnail: String,
    val size: String,
    val url: String,

    val downloadId: Long = 0L,
    val fileName: String = "",

    val progress: Int = 0,
    val speed: String = "0 KB/s",
    val remaining: String = "Calculating...",
    val paused: Boolean = false
)
fun saveDownloads(context: Context, downloads: List<DownloadItem>) {
    val array = JSONArray()

    downloads.forEach { item ->
        val obj = JSONObject()
        obj.put("title", item.title)
        obj.put("format", item.format)
        obj.put("thumbnail", item.thumbnail)
        obj.put("size", item.size)
        obj.put("url", item.url)
        obj.put("downloadId", item.downloadId)
        obj.put("fileName", item.fileName)
        obj.put("progress", item.progress)
        obj.put("speed", item.speed)
        obj.put("remaining", item.remaining)
        array.put(obj)
    }

    context.getSharedPreferences("rifitube", Context.MODE_PRIVATE)
        .edit()
        .putString("downloads", array.toString())
        .apply()
}
fun loadDownloads(context: Context): MutableList<DownloadItem> {

    val saved =
        context.getSharedPreferences(
            "rifitube",
            Context.MODE_PRIVATE
        ).getString("downloads", null)

    val list = mutableListOf<DownloadItem>()

    if (saved != null) {

        val array = JSONArray(saved)

        for (i in 0 until array.length()) {

            val obj = array.getJSONObject(i)

            list.add(
                DownloadItem(
                    title = obj.getString("title"),
                    format = obj.getString("format"),
                    thumbnail = obj.getString("thumbnail"),
                    size = obj.getString("size"),
                    url = obj.getString("url"),

                    downloadId = obj.optLong("downloadId", 0L),
                    fileName = obj.optString("fileName", ""),

                    progress = obj.getInt("progress"),
                    speed = obj.optString("speed", "0 KB/s"),
                    remaining = obj.optString("remaining", "Calculating..."),
                            paused = obj.optBoolean("paused", false)
                )
            )
        }
    }

    return list
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SplashScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RifiTubeApp() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videoLink by remember { mutableStateOf("") }
    var showOptions by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf("Home") }
    var selectedFormat by remember { mutableStateOf("Classic MP3") }

    var thumbnail by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var prepareProgress by remember { mutableStateOf(0) }

    val downloads = remember {
        mutableStateListOf<DownloadItem>().apply {
            addAll(loadDownloads(context))
        }
    }

    var history by remember {
        mutableStateOf(listOf<String>())
    }

    val platform = detectPlatform(videoLink)

    var videoTitle by remember { mutableStateOf("RifiTube Video") }
    var thumbnailUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipText =
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?: ""

        if (
            clipText.startsWith("http://") ||
            clipText.startsWith("https://")
        ) {
            videoLink = clipText
        }
    }

    LaunchedEffect(videoLink) {

        if (videoLink.isBlank()) {
            videoTitle = "RifiTube Video"
            thumbnailUrl = ""
            thumbnail = ""
        } else {

            delay(800)

            val info = getVideoInfo(videoLink)

            videoTitle = info.first
            thumbnailUrl = info.second
            thumbnail = info.second
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050816),
                        Color(0xFF10112A),
                        Color(0xFF1A1240)
                    )
                )
            )
            .padding(18.dp)
    ) {

        if (currentPage == "Downloads") {

            DownloadsPage(downloads) { item ->
                downloads.remove(item)
                saveDownloads(context, downloads)
            }
        } else if (currentPage == "History") {

            HistoryPage(history)

        } else if (currentPage == "Settings") {

            SettingsPage(
                onClearHistory = {
                    history = emptyList()
                }
            )

        } else {
            HomePage(
                videoLink = videoLink,
                onLinkChange = { videoLink = it },
                platform = platform,
                title = videoTitle,
                thumbnail = thumbnailUrl,
                isLoading = isLoading,
                prepareProgress = prepareProgress,

                onDownloadClick = {

                    if (videoLink.isBlank()) {

                        Toast.makeText(
                            context,
                            "Paste link first",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        history = listOf(videoLink) + history

                        showOptions = true
                    }
                }
            )
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {

        BottomNav(
            currentPage = currentPage,
            onPageChange = { currentPage = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp
                )
        )
    }
    if (showOptions) {

        ModalBottomSheet(
            onDismissRequest = {
                showOptions = false
            },
            containerColor = Color(0xFF11152A)
        ) {

            DownloadVideoAsSheet(
                title = videoTitle,
                site = platform,
                thumbnail = thumbnailUrl,
                selected = selectedFormat,

                onSelect = {
                    selectedFormat = it
                },

                onDownload = {

                    showOptions = false
                    isLoading = true
                    prepareProgress = 10

                    scope.launch {
                        prepareProgress = 35

                        val finalUrl =
                            getDownloadUrl(videoLink, selectedFormat)
                        prepareProgress = 70
                        if (finalUrl.isBlank()) {

                            isLoading = false

                            Toast.makeText(
                                context,
                                "Download link not found",
                                Toast.LENGTH_SHORT
                            ).show()

                            return@launch
                        }

                        val extension =
                            if (selectedFormat.contains("MP3"))
                                "mp3"
                            else
                                "mp4"

                        val fileName =
                            "RifiTube_${System.currentTimeMillis()}.$extension"
                        prepareProgress= 90
                        val downloadId = startRealDownload(
                            context = context,
                            url = finalUrl,
                            fileName = fileName
                        )

                        prepareProgress = 100
                        downloads.add(

                            DownloadItem(
                                title = videoTitle,
                                format = selectedFormat,
                                thumbnail = thumbnailUrl,

                                size =
                                    if (selectedFormat.contains("720"))
                                        "13.9 MB"

                                    else if (selectedFormat.contains("360"))
                                        "5.0 MB"

                                    else if (selectedFormat.contains("Classic"))
                                        "5.2 MB"

                                    else
                                        "4.6 MB",

                                url = finalUrl,

                                downloadId = downloadId,
                                fileName = fileName,

                                progress = 0
                            )
                        )
                        val itemIndex = downloads.lastIndex

                        scope.launch {
                            trackDownloadProgress(
                                context = context,
                                downloadId = downloadId,
                                downloads = downloads,
                                itemIndex = itemIndex
                            )
                        }

                        saveDownloads(context, downloads)

                        isLoading = false
                        Toast.makeText(
                            context,
                            "Download started ✅",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
}
@Composable
fun HomePage(
    videoLink: String,
    onLinkChange: (String) -> Unit,
    platform: String,
    title: String,
    thumbnail: String,
    isLoading: Boolean,
    prepareProgress: Int,
    onDownloadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A).copy(alpha = 0.96f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFB84CFF), Color(0xFF3388FF))
                            ),
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Search anything",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "YouTube videos, MP3 songs,\nInstagram stories, Facebook videos",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "RifiTube",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFF4FC3F7),
                                Color(0xFFB84CFF)
                            )
                        )
                    )
                )

                Text(
                    text = "Your all-in-one downloader",
                    color = Color.Gray,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = videoLink,
                    onValueChange = onLinkChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    placeholder = {
                        Text("Paste direct link or API link...", color = Color.Gray)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = Color.LightGray)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFB84CFF),
                        unfocusedBorderColor = Color(0xFF4FC3F7),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Detected: $platform",
                    color = Color(0xFF4FC3F7),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (thumbnail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))

                    VideoPreviewCard(
                        title = title,
                        thumbnail = thumbnail
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                DownloadButton(onClick = onDownloadClick)

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SmallIconCard(Icons.Default.PlayCircle, "YouTube", Color.Red)
                    SmallIconCard(Icons.Default.MusicNote, "MP3", Color.Magenta)
                    SmallIconCard(Icons.Default.CameraAlt, "Instagram", Color(0xFFFF9800))
                    SmallIconCard(Icons.Default.Facebook, "Facebook", Color(0xFF1877F2))
                }
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C31))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "Preparing download... $prepareProgress%",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
@Composable
fun DownloadVideoAsSheet(
    title: String,
    site: String,
    thumbnail: String,
    selected: String,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(26.dp)) {

        Text("Download video as", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .width(120.dp)
                    .height(75.dp)
                    .background(Color.DarkGray, RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                Text(site.lowercase(), color = Color.Gray, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text("Music", color = Color.Gray, fontSize = 20.sp)

        DownloadFormatRow("🎵", "Fast MP3", "4.6 MB", selected) { onSelect("Fast MP3") }
        DownloadFormatRow("🎶", "Classic MP3", "5.2 MB", selected) { onSelect("Classic MP3") }

        Spacer(modifier = Modifier.height(18.dp))

        Text("Video", color = Color.Gray, fontSize = 20.sp)

        DownloadFormatRow("▶️", "Fast 360p", "5.0 MB", selected) { onSelect("Fast 360p") }
        DownloadFormatRow("🎬", "High quality 720p", "13.9 MB", selected) { onSelect("High quality 720p") }

        Divider(color = Color(0xFF2B2C44))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("More formats", color = Color.White, fontSize = 18.sp)
            Text("All  ›", color = Color.Gray, fontSize = 18.sp)
        }

        Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC928))
        ) {
            Text("Download", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DownloadFormatRow(
    icon: String,
    name: String,
    size: String,
    selected: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 26.sp)
        Spacer(modifier = Modifier.width(18.dp))
        Text(name, color = Color.White, fontSize = 19.sp, modifier = Modifier.weight(1f))
        Text(size, color = Color.Gray, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(12.dp))
        RadioButton(
            selected = selected == name,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFFFFC928),
                unselectedColor = Color.Gray
            )
        )
    }
}

@Composable
fun VideoPreviewCard(title: String, thumbnail: String) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1C31)
        )
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Ready to download",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun DownloadButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFB84CFF), Color(0xFF4F8CFF))),
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Download", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DownloadsPage(
    downloads: SnapshotStateList<DownloadItem>,
    onDelete: (DownloadItem) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 75.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(25.dp))

        Text("Downloads", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))

        if (downloads.isEmpty()) {
            Text("No downloads yet", color = Color.Gray, fontSize = 18.sp)
        }

        downloads.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15162A)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.width(80.dp).height(50.dp),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(item.format, color = Color(0xFF4FC3F7), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(item.size, color = Color.Gray, fontSize = 10.sp)

                        Spacer(modifier = Modifier.height(5.dp))

                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFC928),
                            trackColor = Color(0xFF2B2C44)
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = if (item.progress >= 100) "Completed ✅" else "${item.progress}%",
                            color = if (item.progress >= 100) Color(0xFF00E676) else Color(0xFFFFC928),
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "${item.speed} • ${item.remaining}",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (item.paused) "Resume" else "Pause",

                            color = if (item.paused)
                                Color(0xFF00E676)
                            else
                                Color(0xFFFFC928),

                            fontSize = 11.sp,

                            modifier = Modifier.clickable {

                                val index = downloads.indexOf(item)

                                if (index != -1) {

                                    downloads[index] =
                                        item.copy(
                                            paused = !item.paused
                                        )

                                    saveDownloads(context, downloads)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Open File",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {

                                try {

                                    val file =
                                        java.io.File(
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_MOVIES
                                            ),
                                            "RifiTube/${item.fileName}"
                                        )

                                    val uri =
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )

                                    val intent = Intent(Intent.ACTION_VIEW)

                                    intent.setDataAndType(
                                        uri,
                                        if (item.fileName.endsWith(".mp3"))
                                            "audio/*"
                                        else
                                            "video/*"
                                    )

                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                                    context.startActivity(intent)

                                } catch (e: Exception) {

                                    Toast.makeText(
                                        context,
                                        "File not found",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Share File",
                            color = Color(0xFF4FC3F7),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {

                                try {

                                    val file =
                                        java.io.File(
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_MOVIES
                                            ),
                                            "RifiTube/${item.fileName}"
                                        )

                                    val uri =
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )

                                    val shareIntent = Intent(Intent.ACTION_SEND)

                                    shareIntent.type =
                                        if (item.fileName.endsWith(".mp3"))
                                            "audio/*"
                                        else
                                            "video/*"

                                    shareIntent.putExtra(
                                        Intent.EXTRA_STREAM,
                                        uri
                                    )

                                    shareIntent.addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )

                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            "Share via"
                                        )
                                    )

                                } catch (e: Exception) {

                                    Toast.makeText(
                                        context,
                                        "Share failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        Text(
                            text = if (item.progress >= 100) "Delete" else "Cancel",
                            color = Color.Red,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {

                                try {
                                    if (item.downloadId != 0L && item.progress < 100) {
                                        val manager =
                                            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                                        manager.remove(item.downloadId)

                                        Toast.makeText(
                                            context,
                                            "Download cancelled",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    onDelete(item)

                                } catch (e: Exception) {
                                    onDelete(item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun HistoryPage(history: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 75.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(25.dp))

        Text(
            "Search History",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Text("No history yet", color = Color.Gray)
        }

        history.forEach { link ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15162A))
            ) {
                Text(
                    text = link,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun BottomNav(
    currentPage: String,
    onPageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().height(88.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15162A))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFB84CFF),
                                Color(0xFF4F8CFF)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        onPageChange("Home")
                    },

                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Home",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            BottomItem(Icons.Default.Download, "Downloads", if (currentPage == "Downloads") Color(0xFFB84CFF) else Color.Gray) {
                onPageChange("Downloads")
            }

            BottomItem(Icons.Default.History, "History", if (currentPage == "History") Color(0xFFB84CFF) else Color.Gray) {
                onPageChange("History")
            }

            BottomItem(Icons.Default.Settings, "Settings", if (currentPage == "Settings") Color(0xFFB84CFF) else Color.Gray) {
                onPageChange("Settings")
            }
        }
    }
}

@Composable
fun SmallIconCard(icon: ImageVector, title: String, iconColor: Color) {
    Card(
        modifier = Modifier.size(width = 72.dp, height = 78.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C31))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
fun BottomItem(
    icon: ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
            modifier = Modifier
                .scale(
                    if (color == Color(0xFFB84CFF)) 1.15f else 1f
                )
                .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, color = color, fontSize = 12.sp)
        }
}

fun startRealDownload(context: Context, url: String, fileName: String): Long {

    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setDescription("Downloading with RifiTube...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_MOVIES,
            "RifiTube/$fileName"
        )
        .setMimeType(if (fileName.endsWith(".mp3")) "audio/mpeg" else "video/mp4")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    return manager.enqueue(request)
}

suspend fun getDownloadUrl(originalLink: String, format: String): String {
    return withContext(Dispatchers.IO) {

        if (isDirectMediaLink(originalLink)) {
            return@withContext originalLink
        }

        try {
            val apiUrl =
                "http://10.0.2.2:3000/download?url=${Uri.encode(originalLink)}"


            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val video = json.optString("video", "")

            return@withContext video

        } catch (e: Exception) {
            return@withContext ""
        }
    }
}
suspend fun getVideoInfo(originalLink: String): Pair<String, String> {
    return withContext(Dispatchers.IO) {
        try {
            val apiUrl =
                "http://10.0.2.2:3000/download?url=${Uri.encode(originalLink)}"

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val response = connection.inputStream.bufferedReader().readText()

            val json = JSONObject(response)

            val title = json.optString("title", "RifiTube Video")
            val thumbnail = json.optString("thumbnail", "")

            Pair(title, thumbnail)

        } catch (e: Exception) {
            Pair("RifiTube Video", "")
            Pair("RifiTube Video", "")
        }
    }
}
fun trackDownloadProgress(
    context: Context,
    downloadId: Long,
    downloads: SnapshotStateList<DownloadItem>,
    itemIndex: Int
) {
    val manager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    Thread {
        var downloading = true

        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        while (downloading) {

            if (downloads[itemIndex].paused) {
                Thread.sleep(500)
                continue
            }

            val query = DownloadManager.Query()
                .setFilterById(downloadId)

            val cursor = manager.query(query)

            if (cursor.moveToFirst()) {

                val bytesDownloaded =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    )

                val bytesTotal =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                        )
                    )

                val status =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS
                        )
                    )

                if (bytesTotal > 0) {

                    val progress =
                        (bytesDownloaded * 100L / bytesTotal).toInt()
                    val currentTime = System.currentTimeMillis()

                    val timeDiff =
                        (currentTime - lastTime) / 1000f

                    if (timeDiff > 0) {

                        val speedBytes =
                            ((bytesDownloaded - lastBytes) / timeDiff)

                        val speedKB =
                            speedBytes / 1024f

                        val remainingBytes =
                            bytesTotal - bytesDownloaded

                        val remainingSeconds =
                            if (speedBytes > 0)
                                remainingBytes / speedBytes
                            else
                                0f

                        val remainingText =
                            "${remainingSeconds.toInt()} sec left"

                        val speedText =
                            "${speedKB.toInt()} KB/s"

                        if (downloads[itemIndex].paused) {
                            continue
                        }

                        downloads[itemIndex] =
                            downloads[itemIndex].copy(
                                progress = progress,
                                speed = speedText,
                                remaining = remainingText
                            )
                        showDownloadNotification(
                            context = context,
                            title = downloads[itemIndex].title,
                            progress = progress,
                            speed = speedText,
                            remaining = remainingText
                        )

                        lastTime = currentTime
                        lastBytes = bytesDownloaded.toLong()

                        saveDownloads(context, downloads)
                    }

                }

                if (
                    status == DownloadManager.STATUS_SUCCESSFUL ||
                    status == DownloadManager.STATUS_FAILED
                ) {
                    downloading = false
                }
            }

            cursor.close()

            Thread.sleep(500)
        }
    }.start()
}
fun isDirectMediaLink(link: String): Boolean {
    val l = link.lowercase()
    return l.endsWith(".mp4") ||
            l.endsWith(".mp3") ||
            l.endsWith(".m4a") ||
            l.endsWith(".webm") ||
            l.contains(".mp4?") ||
            l.contains(".mp3?") ||
            l.contains(".m4a?") ||
            l.contains(".webm?")
}

fun detectPlatform(link: String): String {
    val l = link.lowercase()
    return when {
        "youtube" in l || "youtu.be" in l -> "YouTube"
        "instagram" in l -> "Instagram"
        "facebook" in l || "fb.watch" in l -> "Facebook"
        "tiktok" in l -> "TikTok"
        link.isBlank() -> "Waiting for link..."
        else -> "Direct Link / Unknown"
    }
}

fun getFakeTitle(link: String): String {
    val l = link.lowercase()
    return when {
        "youtube" in l || "youtu.be" in l -> "YouTube Video"
        "instagram" in l -> "Instagram Reel"
        "facebook" in l || "fb.watch" in l -> "Facebook Video"
        "tiktok" in l -> "TikTok Video"
        else -> "RifiTube Media"
    }
}
@Composable
fun SettingsPage(
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 70.dp, start = 18.dp, end = 18.dp)
    ) {

        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {
                    Toast.makeText(
                        context,
                        "Language: English",
                        Toast.LENGTH_SHORT
                    ).show()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsItemContent("🌍", "Language", "English")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {
                    Toast.makeText(
                        context,
                        "Dark mode already enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsItemContent("🌙", "Dark Mode", "Enabled")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {
                    Toast.makeText(
                        context,
                        "Folder: Movies/RifiTube",
                        Toast.LENGTH_SHORT
                    ).show()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsItemContent("📁", "Download Folder", "Movies/RifiTube")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {
                    Toast.makeText(
                        context,
                        "Default quality: 720p",
                        Toast.LENGTH_SHORT
                    ).show()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsItemContent("🎬", "Default Quality", "720p")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {
                    Toast.makeText(
                        context,
                        "Notifications enabled",
                        Toast.LENGTH_SHORT
                    ).show()
                },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsItemContent("🔔", "Notifications", "Enabled")
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {
                    onClearHistory()

                    Toast.makeText(
                        context,

                        "History cleared",
                        Toast.LENGTH_SHORT
                    ).show()
                },

            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),

            shape = RoundedCornerShape(18.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(
                        text = "🧹",
                        fontSize = 24.sp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {

                        Text(
                            text = "Clear History",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Delete all history",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }

                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {

                    val shareIntent = Intent(Intent.ACTION_SEND)

                    shareIntent.type = "text/plain"

                    shareIntent.putExtra(
                        Intent.EXTRA_TEXT,
                        "Download RifiTube 🔥\nFast video & MP3 downloader"
                    )

                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            "Share App"
                        )
                    )
                },

            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),

            shape = RoundedCornerShape(18.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(
                        text = "📲",
                        fontSize = 24.sp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {

                        Text(
                            text = "Share App",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Invite friends",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }

                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable {

                    Toast.makeText(
                        context,
                        "RifiTube v1.0\nCreated by RifiTube Team 🚀",
                        Toast.LENGTH_LONG
                    ).show()
                },

            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF15162A)
            ),

            shape = RoundedCornerShape(18.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(
                        text = "ℹ️",
                        fontSize = 24.sp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {

                        Text(
                            text = "About App",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "RifiTube v1.0",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }

                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
        }
    }
}
@Composable
fun SettingsItemContent(
    emoji: String,
    title: String,
    value: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),

        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                text = emoji,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = value,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray
        )
    }
}
@Composable
fun SettingsItem(
    emoji: String,
    title: String,
    value: String
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF15162A)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),

            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(
                    text = emoji,
                    fontSize = 24.sp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = value,
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }

            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}
@Composable
fun SplashScreen() {

    var showSplash by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(Unit) {
        delay(2500)
        showSplash = false
    }

    if (showSplash) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF050816),
                            Color(0xFF10112A),
                            Color(0xFF1A1240)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFB84CFF),
                                    Color(0xFF4F8CFF)
                                )
                            ),
                            RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(70.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "RifiTube",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Ultimate Video Downloader",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }

    } else {

        RifiTubeApp()
    }
}
fun showDownloadNotification(
    context: Context,
    title: String,
    progress: Int,
    speed: String,
    remaining: String
) {
    val channelId = "rifitube_downloads"

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "RifiTube Downloads",
            NotificationManager.IMPORTANCE_LOW
        )

        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText("$speed • $remaining")
        .setProgress(100, progress, false)
        .setOngoing(progress < 100)
        .setOnlyAlertOnce(true)
        .build()

    notificationManager.notify(1001, notification)
}