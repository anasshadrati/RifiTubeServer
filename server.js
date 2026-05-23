const express = require("express");
const cors = require("cors");
const ytdlp = require("yt-dlp-exec");

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

app.get("/", (req, res) => {
  res.json({ status: "RifiTube server is running ✅" });
});

async function getVideoData(videoUrl) {
  const info = await ytdlp(videoUrl, {
    dumpSingleJson: true,
    noPlaylist: true,
    skipDownload: true
  });

  const directUrl = await ytdlp(videoUrl, {
    getUrl: true,
    format: "18/22/best[ext=mp4]/best",
    noPlaylist: true,
    youtubeSkipDashManifest: true,
    forceIpv4: true
  });

  return {
    title: info.title || "RifiTube Video",
    thumbnail: info.thumbnail
      ? info.thumbnail.replace("maxresdefault", "hqdefault")
      : "",
    duration: info.duration_string || "00:00",
    size: info.filesize
      ? (info.filesize / 1024 / 1024).toFixed(1) + " MB"
      : "Unknown",
    video: String(directUrl).trim()
  };
}
app.get("/preview", async (req, res) => {

  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({
      error: "Missing url"
    });
  }

  try {

    const info = await ytdlp(videoUrl, {
      dumpSingleJson: true,
      noPlaylist: true,
      skipDownload: true
    });

    res.json({
      title: info.title || "RifiTube Video",
      thumbnail: info.thumbnail
        ? info.thumbnail.replace(
            "maxresdefault",
            "hqdefault"
          )
        : "",
      duration: info.duration_string || "00:00"
    });

  } catch (err) {

    res.status(500).json({
      error: "Preview failed",
      details: String(err)
    });

  }

});
app.get("/formats", async (req, res) => {

  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({
      error: "Missing url"
    });
  }

  try {

    const info = await ytdlp(videoUrl, {
      dumpSingleJson: true,
      noPlaylist: true,
      skipDownload: true
    });

    const formats =
      (info.formats || [])
        .filter(f =>
          f.ext === "mp4" &&
          f.height
        )
        .map(f => ({
          quality: `${f.height}p`,
          ext: f.ext,
          filesize:
            f.filesize
              ? `${(f.filesize / 1024 / 1024).toFixed(1)} MB`
              : "Unknown",
          url: f.url
        }))
        .filter((value, index, self) =>
          index === self.findIndex(
            f => f.quality === value.quality
          )
        )
        .filter(f =>
          ["360p", "480p", "720p", "1080p"].includes(f.quality)
        )

    formats.unshift({
      quality: "MP3",
      ext: "mp3",
      filesize: "Audio",
      url: videoUrl
    });

    res.json({
      title: info.title,
      thumbnail: info.thumbnail,
      formats
    });

  } catch (err) {

    res.status(500).json({
      error: "Formats failed",
      details: String(err)
    });

  }

});
app.get("/info", async (req, res) => {
  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({ error: "Missing url" });
  }

  try {
    const data = await getVideoData(videoUrl);
    res.json(data);
  } catch (err) {
    res.status(500).json({
      error: "Video info not found",
      details: String(err)
    });
  }
});

app.get("/download", async (req, res) => {
  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({ error: "Missing url" });
  }

  try {
    const data = await getVideoData(videoUrl);
    res.json(data);
  } catch (err) {
    res.status(500).json({
      error: "Download failed",
      details: String(err)
    });
  }
});
app.get("/update", (req, res) => {
  res.json({
    versionCode: 2,
    versionName: "2.0",
    apkUrl: "PUT_APK_LINK_HERE"
  });
});
app.listen(PORT, () => {
  console.log(`RifiTube server running on port ${PORT}`);
});