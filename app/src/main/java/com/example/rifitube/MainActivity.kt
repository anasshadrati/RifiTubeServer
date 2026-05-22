package com.rifitube.app

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
import android.app.AlertDialog
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.app.NotificationCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
private const val API_BASE_URL = "https://rifitubeserver.onrender.com"
data class DownloadItem(
    val title: String,
    val format: String,
    val thumbnail: String,
    val size: String,
    val duration: String = "00:00",
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
        obj.put("duration", item.duration)
        obj.put("url", item.url)
        obj.put("downloadId", item.downloadId)
        obj.put("fileName", item.fileName)
        obj.put("progress", item.progress)
        obj.put("speed", item.speed)
        obj.put("remaining", item.remaining)
        obj.put("paused", item.paused)
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
                    duration = obj.optString("duration", "00:00"),
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
fun checkForUpdates(context: Context) {

    CoroutineScope(Dispatchers.IO).launch {

        try {

            val url = URL("https://rifitubeserver.onrender.com/update")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"

            val response =
                connection.inputStream.bufferedReader().readText()

            val json = JSONObject(response)

            val latestVersion = json.getInt("versionCode")
            val apkUrl = json.getString("apkUrl")

            val currentVersion =
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionCode

            if (latestVersion > currentVersion) {

                withContext(Dispatchers.Main) {

                    AlertDialog.Builder(context)
                        .setTitle("New Update")
                        .setMessage("New version available")
                        .setPositiveButton("Update") { _, _ ->

                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setData(Uri.parse(apkUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            context.startActivity(intent)
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForUpdates(this)
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
    val prefs =
        context.getSharedPreferences(
            "rifitube_settings",
            Context.MODE_PRIVATE
        )
    var language by remember {
        mutableStateOf(
            prefs.getString("language", "English") ?: "English"
        )
    }
    var darkMode by remember {
        mutableStateOf(
            prefs.getBoolean("dark_mode", true)
        )
    }

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
    var videoDuration by remember { mutableStateOf("00:00") }
    var videoSize by remember { mutableStateOf("Unknown") }

    var videoInfo by remember {
        mutableStateOf(
            VideoInfo(
                title = "RifiTube Video",
                thumbnail = "",
                duration = "00:00",
                size = "Unknown"
            )
        )
    }
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

            videoInfo = getVideoInfo(videoLink)
            val info = videoInfo

            videoTitle = info.title
            thumbnailUrl = info.thumbnail
            thumbnail = info.thumbnail
            videoDuration = info.duration
            videoSize = info.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (darkMode)
                        listOf(
                            Color(0xFF050816),
                            Color(0xFF10112A),
                            Color(0xFF1A1240)
                        )
                    else
                        listOf(
                            Color(0xFFF5F7FF),
                            Color(0xFFE8ECFF),
                            Color(0xFFDDE5FF)
                        )
                )
            )
            .padding(18.dp)
    ) {

        if (currentPage == "Downloads") {

            DownloadsPage(downloads, language) { item ->
                downloads.remove(item)
                saveDownloads(context, downloads)
            }
        } else if (currentPage == "History") {

            HistoryPage(history, language)

        } else if (currentPage == "Settings") {

            SettingsPage(
                language = language,
                onLanguageChange = { language = it },
                darkMode = darkMode,
                onDarkModeChange = {

                    darkMode = it

                    prefs.edit()
                        .putBoolean("dark_mode", it)
                        .apply()
                },
                onClearHistory = {
                    history = emptyList()
                }
            )

        } else {
            HomePage(
                language = language,
                videoLink = videoLink,
                onLinkChange = { videoLink = it },
                platform = platform,
                title = videoTitle,
                thumbnail = thumbnailUrl,
                isLoading = isLoading,
                duration = videoDuration,
                size = videoSize,
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
                },

                onMp3Click = {
                    selectedFormat = "Classic MP3"
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
            language = language,
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
                duration = videoDuration,
                size = videoSize,
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

                                size = videoInfo.size,
                                duration = videoDuration,

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
    language: String,
    videoLink: String,
    onLinkChange: (String) -> Unit,
    platform: String,
    title: String,
    thumbnail: String,
    duration: String,
    size: String,
    isLoading: Boolean,
    prepareProgress: Int,
    onDownloadClick: () -> Unit,
    onMp3Click: () -> Unit
) {
    val context = LocalContext.current
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
                    tr(language, "search_anything"),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr(language, "subtitle"),
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
                val borderColor by animateColorAsState(
                    targetValue =
                        if (videoLink.isNotBlank())
                            Color(0xFFB84CFF)
                        else
                            Color(0xFF4FC3F7),
                    label = ""
                )
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
                        focusedBorderColor = borderColor,
                        unfocusedBorderColor = borderColor,
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
                        language = language,
                        title = title,
                        thumbnail = thumbnail,
                        duration = duration,
                        size = size,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                DownloadButton(
                    language = language,
                    onClick = onDownloadClick
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SmallIconCard(
                        Icons.Default.PlayCircle,
                        "YouTube",
                        Color.Red
                    ) {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://youtube.com")
                        )
                        context.startActivity(intent)
                    }
                    SmallIconCard(
                        Icons.Default.MusicNote,
                        "MP3",
                        Color.Magenta
                    ) {
                        onMp3Click()
                    }
                    SmallIconCard(
                        Icons.Default.CameraAlt,
                        "Instagram",
                        Color(0xFFFF9800)
                    ) {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://instagram.com")
                        )
                        context.startActivity(intent)
                    }
                    SmallIconCard(
                        Icons.Default.Facebook,
                        "Facebook",
                        Color(0xFF1877F2)
                    ) {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://facebook.com")
                        )
                        context.startActivity(intent)
                    }
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
    duration: String,
    size: String,
    selected: String,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 24.dp)
    ) {
        Text(
            text = "Download video as",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .width(120.dp)
                    .height(75.dp)
                    .background(Color.DarkGray, shape = RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = site.lowercase(),
                    color = Color.Gray,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "⏱ $duration • 💾 $size",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            "Music",
            color = Color.Gray,
            fontSize = 20.sp
        )

        DownloadFormatRow(icon = "🎵", name = "Fast MP3", size = "4.6 MB", selected = selected) {
            onSelect("Fast MP3")
        }
        DownloadFormatRow(icon = "🎵", name = "Classic MP3", size = "5.2 MB", selected = selected) {
            onSelect("Classic MP3")
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            "Video",
            color = Color.Gray,
            fontSize = 20.sp
        )

        DownloadFormatRow(icon = "▶️", name = "Fast 360p", size = "5.0 MB", selected = selected) {
            onSelect("Fast 360p")
        }
        DownloadFormatRow(icon = "📺", name = "High quality 720p", size = "13.9 MB", selected = selected) {
            onSelect("High quality 720p")
        }

        Divider(color = Color(0xFF2B2C44))

        Spacer(modifier = Modifier.height(15.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("More formats", color = Color.White, fontSize = 18.sp)
            Text("All >", color = Color.Gray, fontSize = 18.sp)
        }

        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFC928))
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
fun VideoPreviewCard(
    language: String,
    title: String,
    thumbnail: String,
    duration: String,
    size: String
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1C31)
        )
    ) {

        Column {

            Box {

                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(
                            Color.Black.copy(alpha = 0.75f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {

                    Text(
                        text = duration,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = size,
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadButton(
    language: String,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Button(
        onClick = {
            pressed = true
            onClick()

            android.os.Handler().postDelayed({
                pressed = false
            }, 120)
        },
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(if (pressed) 0.96f else 1f)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color(0xFFB84CFF),
                    spotColor = Color(0xFF4FC3F7)
                )
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFB84CFF), Color(0xFF4F8CFF))),
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text(tr(language, "Download"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsPage(
    downloads: SnapshotStateList<DownloadItem>,
    language: String,
    onDelete: (DownloadItem) -> Unit
) {
    val context = LocalContext.current

    var selectedItem by remember { mutableStateOf<DownloadItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 75.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(25.dp))

        Text("Downloads", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Search downloads...", color = Color.Gray)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = Color.Gray)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFB84CFF),
                unfocusedBorderColor = Color(0xFF4FC3F7),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("All", "MP3", "MP4", "Completed").forEach { filter ->

                Text(
                    text = filter,
                    color = if (selectedFilter == filter) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            if (selectedFilter == filter) Color(0xFFFFC928) else Color(0xFF15162A),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            selectedFilter = filter
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (downloads.isEmpty()) {
            Text(
                tr(language, "no_downloads"),
                color = Color.Gray,
                fontSize = 18.sp
            )
        }

        downloads.filter { item ->
            val matchesSearch = item.title.contains(searchText, ignoreCase = true) ||
                    item.format.contains(searchText, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "MP3" -> item.format.contains("MP3", ignoreCase = true)
                "MP4" -> item.format.contains("720p", ignoreCase = true) || item.format.contains("360p", ignoreCase = true)
                "Completed" -> item.progress >= 100
                else -> true
            }
            matchesSearch && matchesFilter
        }.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color(0xFF4FC3F7).copy(alpha = 0.25f),
                        spotColor = Color(0xFFB84CFF).copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        modifier =
                            if (item.format.contains("MP3"))
                                Modifier
                                    .size(58.dp)
                                    .background(
                                        Color(0xFF2B2C44),
                                        RoundedCornerShape(50.dp)
                                    )
                            else
                                Modifier
                                    .width(80.dp)
                                    .height(50.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box {

                        AsyncImage(
                            model = item.thumbnail,
                            contentDescription = null,
                            modifier =
                                if (item.format.contains("MP3"))
                                    Modifier
                                        .size(58.dp)
                                        .background(
                                            Color(0xFF2B2C44),
                                            RoundedCornerShape(50.dp)
                                        )
                                else
                                    Modifier
                                        .width(80.dp)
                                        .height(50.dp),
                            contentScale = ContentScale.Crop
                        )

                        Text(
                            text =
                                if (item.format.contains("MP3"))
                                    "MP3"
                                else
                                    "MP4",

                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,

                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(
                                    if (item.format.contains("MP3"))
                                        Color(0xFFB84CFF)
                                    else
                                        Color(0xFFFF9800),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(item.format, color = Color(0xFF4FC3F7), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.duration} • ${item.size}",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        LinearProgressIndicator(
                            progress = item.progress / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Color(0xFFB84CFF),
                            trackColor = Color(0xFF1B1C31),
                            strokeCap = StrokeCap.Round
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
if (item.progress >= 100)
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
                        if (item.progress >= 100) {
                            Text(
                                text = "Share File",
                                color = Color(0xFF4FC3F7),
                                fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    try {
                                        val file = java.io.File(
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_MOVIES
                                            ),
                                            "RifiTube/${item.fileName}"
                                        )

                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )

                                        val shareIntent = Intent(Intent.ACTION_SEND)
                                        shareIntent.type =
                                            if (item.fileName.endsWith(".mp3")) "audio/*" else "video/*"

                                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share via")
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
                        }
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
            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        selectedItem = item
                        showMenu = true
                    }
            )
        }
    }
    if (showMenu && selectedItem != null) {

        ModalBottomSheet(
            onDismissRequest = {
                showMenu = false
            },
            containerColor = Color(0xFF15162A)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {

                Text(
                    text = selectedItem!!.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Open File",
                    color = Color(0xFF4FC3F7),
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {

                            try {

                                val item = selectedItem!!

                                val file = java.io.File(
                                    Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_MOVIES
                                    ),
                                    "RifiTube/${item.fileName}"
                                )

                                val uri = androidx.core.content.FileProvider.getUriForFile(
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
                                    "Cannot open file",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            showMenu = false
                        }
                        .padding(vertical = 12.dp)
                )

                Text(
                    text = "Share",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val item = selectedItem!!

                            try {
                                val file = java.io.File(
                                    Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_MOVIES
                                    ),
                                    "RifiTube/${item.fileName}"
                                )

                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND)
                                shareIntent.type =
                                    if (item.fileName.endsWith(".mp3")) "audio/*" else "video/*"

                                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share via")
                                )

                            } catch (e: Exception) {
                                Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                            }

                            showMenu = false
                        }
                        .padding(vertical = 12.dp)
                )

                Text(
                    text = "Delete",
                    color = Color.Red,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDelete(selectedItem!!)
                            showMenu = false
                        }
                        .padding(vertical = 12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
@Composable
fun HistoryPage(history: List<String>,language: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 75.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(25.dp))

        Text(
        text =
            when (language) {
                "Arabic" -> "السجل"
                "French" -> "Historique"
                else -> "History"
            },
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
    language: String,
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
                            listOf(Color(0xFFB84CFF), Color(0xFF4F8CFF))
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable { onPageChange("Home") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Home", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            BottomItem(Icons.Default.Download, tr(language, "downloads"), if (currentPage == "Downloads") Color(0xFFB84CFF) else Color.Gray) {
                onPageChange("Downloads")
            }

            BottomItem(Icons.Default.History, tr(language, "history"), if (currentPage == "History") Color(0xFFB84CFF) else Color.Gray) {
                onPageChange("History")
            }

            BottomItem(Icons.Default.Settings, tr(language, "settings"), if (currentPage == "Settings") Color(0xFFB84CFF) else Color.Gray) {
                onPageChange("Settings")
            }
        }
    }
}

@Composable
fun SmallIconCard(
    icon: ImageVector,
    title: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .size(width = 72.dp, height = 78.dp)
            .scale(if (pressed) 0.95f else 1f)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = iconColor.copy(alpha = 0.5f),
                spotColor = iconColor
            )
            .clickable {
                pressed = true
                onClick()

                android.os.Handler().postDelayed({
                    pressed = false
                }, 100)
                }
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
                .shadow(
                    elevation = if (color == Color(0xFFB84CFF)) 12.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = color,
                    spotColor = color
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
    request.addRequestHeader(
        "User-Agent",
        "Mozilla/5.0"
    )

    request.addRequestHeader(
        "Accept",
        "*/*"
    )

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    return manager.enqueue(request)
}

suspend fun getDownloadUrl(originalLink: String, format: String): String {
    return withContext(Dispatchers.IO) {
        repeat(3) { attempt ->
            try {
                val apiUrl =
                    "$API_BASE_URL/download?url=${Uri.encode(originalLink)}"

                val connection =
                    URL(apiUrl).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 60000
                connection.readTimeout = 60000

                val response =
                    connection.inputStream.bufferedReader().readText()

                val json = JSONObject(response)
                val video = json.optString("video", "")

                if (video.isNotBlank()) {
                    return@withContext video
                }

            } catch (e: Exception) {
                delay(2000)
            }
        }

        ""
    }
}
data class VideoInfo(
    val title: String,
    val thumbnail: String,
    val duration: String,
    val size: String
)
suspend fun getVideoInfo(originalLink: String): VideoInfo {
    return withContext(Dispatchers.IO) {
        try {
            val encodedUrl = java.net.URLEncoder.encode(originalLink, "UTF-8")
            val apiUrl =
                "$API_BASE_URL/info?url=$encodedUrl"

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            VideoInfo(
                title = json.optString("title", "RifiTube Video"),
                thumbnail = json.optString("thumbnail", ""),
                duration = json.optString("duration", "00:00"),
                size = json.optString("size", "Unknown")
            )

        } catch (e: Exception) {
            VideoInfo(
                title = "RifiTube Video",
                thumbnail = "",
                duration = "00:00",
                size = "Unknown"
            )
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
                        val prefs = context.getSharedPreferences(
                            "rifitube_settings",
                            Context.MODE_PRIVATE
                        )

                        val notificationsEnabled =
                            prefs.getBoolean("notifications", true)

                        if (notificationsEnabled) {

                            showDownloadNotification(
                                context = context,
                                title = downloads[itemIndex].title,
                                progress = progress,
                                speed = speedText,
                                remaining = remainingText
                            )
                        }
                        lastTime = currentTime
                        lastBytes = bytesDownloaded.toLong()

                        saveDownloads(context, downloads)
                    }

                }

                if (
                    status == DownloadManager.STATUS_SUCCESSFUL ||
                    status == DownloadManager.STATUS_FAILED
                ) {
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {

                        downloads[itemIndex] =
                            downloads[itemIndex].copy(
                                progress = 100,
                                remaining = "Completed",
                                speed = "Done"
                            )

                        Toast.makeText(
                            context,
                            "Download completed ✅",
                            Toast.LENGTH_SHORT
                        ).show()

                        saveDownloads(context, downloads)
                    }
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
fun tr(language: String, key: String): String {
    return when (language) {
        "Arabic" -> when (key) {
            "search_anything" -> "بحث عن أي شيء"
            "subtitle" -> "فيديوهات YouTube، أغاني MP3، ستوريات Instagram، فيديوهات Facebook"
            "paste_link" -> "لسق الرابط هنا..."
            "download" -> "تحميل"
            "downloads" -> "التحميلات"
            "history" -> "السجل"
            "settings" -> "الإعدادات"
            "no_downloads" -> "لا توجد تحميلات بعد"
            "ready" -> "جاهز للتحميل"
            else -> key
        }

        "French" -> when (key) {
            "search_anything" -> "Rechercher"
            "subtitle" -> "Vidéos YouTube, musiques MP3, stories Instagram, vidéos Facebook"
            "paste_link" -> "Collez le lien ici..."
            "download" -> "Télécharger"
            "downloads" -> "Téléchargements"
            "history" -> "Historique"
            "settings" -> "Paramètres"
            "no_downloads" -> "Aucun téléchargement"
            "ready" -> "Prêt à télécharger"
            else -> key
        }

        else -> when (key) {
            "search_anything" -> "Search anything"
            "subtitle" -> "YouTube videos, MP3 songs,\nInstagram stories, Facebook videos"
            "paste_link" -> "Paste direct link or API link..."
            "download" -> "Download"
            "downloads" -> "Downloads"
            "history" -> "History"
            "settings" -> "Settings"
            "no_downloads" -> "No downloads yet"
            "ready" -> "Ready to download"
            else -> key
        }
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
    language: String,
    onLanguageChange: (String) -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current

    val prefs = context.getSharedPreferences(
        "rifitube_settings",
        Context.MODE_PRIVATE
    )

    var defaultQuality by remember {
        mutableStateOf(prefs.getString("default_quality", "720p") ?: "720p")
    }

    var notifications by remember {
        mutableStateOf(prefs.getBoolean("notifications", true))
    }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 70.dp, start = 18.dp, end = 18.dp, bottom = 120.dp)
    ) {

        Text(
            text =
                when (language) {
                    "Arabic" -> "الإعدادات"
                    "French" -> "Paramètres"
                    else -> "Settings"
                },
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsCard("🌍", "Language", language) {
            showLanguageDialog = true
        }

        SettingsCard("🌙", "Dark Mode", if (darkMode) "Enabled" else "Disabled") {
            onDarkModeChange(!darkMode)
            Toast.makeText(
                context,
                if (!darkMode) "Dark mode enabled" else "Dark mode disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        SettingsCard("📁", "Download Folder", "Movies/RifiTube") {
            Toast.makeText(context, "Files save in Movies/RifiTube", Toast.LENGTH_SHORT).show()
        }

        SettingsCard("🎬", "Default Quality", defaultQuality) {
            showQualityDialog = true
        }

        SettingsCard("🔔", "Notifications", if (notifications) "Enabled" else "Disabled") {

            val newValue = !notifications

            notifications = newValue

            prefs.edit()
                .putBoolean("notifications", newValue)
                .apply()

            Toast.makeText(
                context,
                if (newValue) "Notifications enabled"
                else "Notifications disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        SettingsCard("🧹", "Clear History", "Delete all history") {
            onClearHistory()
            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
        }

        SettingsCard("📲", "Share App", "Invite friends") {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                "Download RifiTube 🔥\nFast video & music downloader"
            )
            context.startActivity(Intent.createChooser(shareIntent, "Share App"))
        }

        SettingsCard("ℹ️", "About App", "RifiTube v1.0") {
            showAboutDialog = true
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = Color(0xFF15162A),
            title = { Text("Choose Language", color = Color.White) },
            text = {
                Column {
                    Text("English", color = Color.White, modifier = Modifier.clickable {

                        onLanguageChange("English")

                        prefs.edit()
                            .putString("language", "English")
                            .apply()

                        showLanguageDialog = false

                    }.padding(10.dp))

                    Text("العربية", color = Color.White, modifier = Modifier.clickable {

                        onLanguageChange("Arabic")

                        prefs.edit()
                            .putString("language", "Arabic")
                            .apply()

                        showLanguageDialog = false

                    }.padding(10.dp))

                    Text("Français", color = Color.White, modifier = Modifier.clickable {

                        onLanguageChange("French")

                        prefs.edit()
                            .putString("language", "French")
                            .apply()

                        showLanguageDialog = false

                    }.padding(10.dp))
                }
            },
            confirmButton = {}
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = Color(0xFF15162A),
            title = { Text("Default Quality", color = Color.White) },
            text = {
                Column {
                    listOf("360p", "720p", "MP3").forEach { quality ->
                        Text(
                            quality,
                            color = Color.White,
                            modifier = Modifier
                                .clickable {
                                    defaultQuality = quality

                                    prefs.edit()
                                        .putString("default_quality", quality)
                                        .apply()

                                    showQualityDialog = false
                                }
                                .padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = Color(0xFF15162A),
            title = {
                Text("RifiTube", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Version 1.0\n\nFast video & music downloader\n\nMade with ❤️ by\nAnass El Hadrati\n\n© 2026 RifiTube. All rights reserved.",
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = Color(0xFF4FC3F7))
                }
            }
        )
    }
}
@Composable
fun SettingsCard(
    emoji: String,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF15162A)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        SettingsItemContent(emoji, title, value)
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
    val scale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {

        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 900,
                easing = FastOutSlowInEasing
            )
        )

        delay(2500)

        showSplash = false
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
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(scale.value)
            ) {

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E6FF),
                                    Color(0xFF7A00FF)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "R",
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .size(28.dp)
                            .offset(x = 8.dp)
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
        .setContentText(
            if (progress >= 100)
                "Download completed ✅"
            else
                "$speed • $remaining"
        )
        .setProgress(100, progress, false)
        .setOngoing(progress < 100)
        .setOnlyAlertOnce(true)
        .setAutoCancel(progress >= 100)
        .build()

    notificationManager.notify(1001, notification)
}