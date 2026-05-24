const express = require("express");
const cors = require("cors");
const ytdlp = require("yt-dlp-exec");

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
const cache = new Map();

function getCache(key) {
  const item = cache.get(key);

  if (!item) return null;

  if (Date.now() - item.time > 10 * 60 * 1000) {
    cache.delete(key);
    return null;
  }

  return item.data;
}

function setCache(key, data) {
  cache.set(key, {
    data,
    time: Date.now()
  });
}
app.get("/", (req, res) => {
  res.json({ status: "RifiTube server is running ✅" });
});
async function safeYtDlp(url, options, retries = 3) {
  for (let i = 0; i < retries; i++) {
    try {
      return await ytdlp(url, {
        noWarnings: true,
        preferFreeFormats: true,
        youtubeSkipDashManifest: true,
        extractorRetries: 3,
        fragmentRetries: 3,
        socketTimeout: 30000,
        forceIpv4: true,
        ...options
      });
    } catch (e) {
      if (i === retries - 1) throw e;

      await new Promise(r => setTimeout(r, 2000));
    }
  }
}
async function getVideoData(videoUrl) {
  const cacheKey = "video:" + videoUrl;

  const cached = getCache(cacheKey);
  if (cached) return cached;

  const info = await safeYtDlp(videoUrl, {
    dumpSingleJson: true,
    noPlaylist: true,
    skipDownload: true,
    forceIpv4: true
  });

  const directUrl = await safeYtDlp(videoUrl, {
    getUrl: true,
    format: "18/22/best[ext=mp4]/best",
    noPlaylist: true,
    youtubeSkipDashManifest: true,
    forceIpv4: true
  });

  const data = {
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

  setCache(cacheKey, data);

  return data;
}
app.get("/health", (req, res) => {
  res.json({
    ok: true,
    server: "RifiTube",
    time: new Date().toISOString()
  });
});
app.get("/preview", async (req, res) => {
  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({
      error: "Missing url"
    });
  }

  try {
    const cacheKey = "preview:" + videoUrl;

    const cached = getCache(cacheKey);
    if (cached) {
      return res.json(cached);
    }

    const info = await safeYtDlp(videoUrl, {
      dumpSingleJson: true,
      noPlaylist: true,
      skipDownload: true,
      forceIpv4: true
    });

    const data = {
      title: info.title || "RifiTube Video",
      thumbnail: info.thumbnail
        ? info.thumbnail.replace("maxresdefault", "hqdefault")
        : "",
      duration: info.duration_string || "00:00"
    };

    setCache(cacheKey, data);

    res.json(data);

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
    const cacheKey = "formats:" + videoUrl;

    const cached = getCache(cacheKey);
    if (cached) {
      return res.json(cached);
    }

    const info = await safeYtDlp(videoUrl, {
      dumpSingleJson: true,
      noPlaylist: true,
      skipDownload: true,
      forceIpv4: true
    });

    const formats = (info.formats || [])
      .filter(f =>
        f.ext === "mp4" &&
        f.height &&
        f.url
      )
      .map(f => ({
        quality: `${f.height}p`,
        ext: "mp4",
        filesize: f.filesize
          ? `${(f.filesize / 1024 / 1024).toFixed(1)} MB`
          : f.filesize_approx
            ? `${(f.filesize_approx / 1024 / 1024).toFixed(1)} MB`
            : "Unknown",
        url: f.url
      }))
      .filter(f =>
        ["360p", "480p", "720p", "1080p"].includes(f.quality)
      )
      .filter((value, index, self) =>
        index === self.findIndex(f => f.quality === value.quality)
      )
      .sort((a, b) =>
        parseInt(a.quality) - parseInt(b.quality)
      );

    formats.unshift({
      quality: "MP3",
      ext: "mp3",
      filesize: "Audio",
      url: videoUrl
    });

    const data = {
      title: info.title || "RifiTube Video",
      thumbnail: info.thumbnail || "",
      formats
    };

    setCache(cacheKey, data);

    res.json(data);

  } catch (err) {
    res.status(500).json({
      error: "Formats failed",
      details: String(err)
    });
  }
});
app.get("/mp3", async (req, res) => {
  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({
      error: "Missing url"
    });
  }

  try {
    const cacheKey = "mp3:" + videoUrl;

    const cached = getCache(cacheKey);
    if (cached) {
      return res.json(cached);
    }

    const audioUrl = await safeYtDlp(videoUrl, {
      getUrl: true,
      format: "bestaudio/best",
      noPlaylist: true,
      forceIpv4: true
    });

    const data = {
      quality: "MP3",
      ext: "mp3",
      url: String(audioUrl).trim()
    };

    setCache(cacheKey, data);

    res.json(data);

  } catch (err) {
    res.status(500).json({
      error: "MP3 failed",
      details: String(err)
    });
  }
});
app.set("trust proxy", true);
app.get("/direct", async (req, res) => {

  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({
      error: "Missing url"
    });
  }

  try {

    const info = await safeYtDlp(videoUrl, {
      dumpSingleJson: true,
      noPlaylist: true,
      skipDownload: true
    });

    const bestVideo = await safeYtDlp(videoUrl, {
      getUrl: true,
      format: "bestvideo+bestaudio/best",
      noPlaylist: true
    });

    res.json({
      title: info.title || "RifiTube Video",
      thumbnail: info.thumbnail || "",
      duration: info.duration_string || "00:00",
      url: String(bestVideo).trim()
    });

  } catch (err) {

    res.status(500).json({
      error: "Direct failed",
      details: String(err)
    });

  }

});
app.listen(PORT, () => {
  console.log(`RifiTube server running on port ${PORT}`);
});