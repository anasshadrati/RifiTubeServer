const express = require("express");
const cors = require("cors");
const ytdlp = require("yt-dlp-exec");

const app = express();

const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

app.get("/", (req, res) => {
  res.json({
    status: "RifiTube server is running ✅"
  });
});

app.get("/download", async (req, res) => {

  const videoUrl = req.query.url;

  if (!videoUrl) {
    return res.status(400).json({
      error: "Missing url"
    });
  }

  try {

    const info = await ytdlp(videoUrl, {
      dumpSingleJson: true,
      noPlaylist: true
    });

    const directUrl = await ytdlp(videoUrl, {
      getUrl: true,
      format: "best[ext=mp4]/best",
      noPlaylist: true
    });

    res.json({

      title: info.title || "RifiTube Video",

      thumbnail: info.thumbnail || "",

      duration: info.duration_string || "00:00",

      size:
        info.filesize
          ? (info.filesize / 1024 / 1024).toFixed(1) + " MB"
          : "Unknown",

      video: directUrl.trim()
    });

  } catch (err) {

    res.status(500).json({
      error: "Download failed",
      details: String(err)
    });
  }
});

app.listen(PORT, () => {
  console.log(`RifiTube server running on port ${PORT}`);
});