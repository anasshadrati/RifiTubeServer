const express = require("express");
const cors = require("cors");
const { execFile } = require("child_process");

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json());

function runYtDlp(args) {
  return new Promise((resolve, reject) => {
    execFile("python", ["-m", "yt_dlp", ...args], { timeout: 90000 }, (error, stdout, stderr) => {
      if (error) reject(stderr || error.message);
      else resolve(stdout);
    });
  });
}

app.get("/", (req, res) => {
  res.json({ status: "RifiTube server is running" });
});

app.get("/info", async (req, res) => {
  const videoUrl = req.query.url;
  if (!videoUrl) return res.status(400).json({ error: "Missing url" });

  try {
    const infoText = await runYtDlp(["-j", "--no-playlist", videoUrl]);
    const info = JSON.parse(infoText);

    res.json({
      title: info.title || "RifiTube Video",
      thumbnail: info.thumbnail || "",
      duration: info.duration || 0,
      uploader: info.uploader || ""
    });
  } catch (err) {
    res.status(500).json({ error: "Video info not found", details: String(err) });
  }
});

app.get("/download", async (req, res) => {
  const videoUrl = req.query.url;
  if (!videoUrl) return res.status(400).json({ error: "Missing url" });

  try {
    const infoText = await runYtDlp(["-j", "--no-playlist", videoUrl]);
    const info = JSON.parse(infoText);

    const videoText = await runYtDlp([
      "-f",
      "best[ext=mp4]/best",
      "--get-url",
      "--no-playlist",
      videoUrl
    ]);

    const links = videoText.split("\n").map(x => x.trim()).filter(Boolean);

    if (links.length === 0) {
      return res.status(404).json({ error: "No video link found" });
    }

    res.json({
      title: info.title || "RifiTube Video",
      thumbnail: info.thumbnail || "",
      video: links[0]
    });

  } catch (err) {
    res.status(500).json({ error: "Download link not found", details: String(err) });
  }
});

app.listen(PORT, () => {
  console.log(`RifiTube server running on http://localhost:${PORT}`);
});