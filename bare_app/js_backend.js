const express = require('express');
const { exec } = require('child_process');
const cors = require('cors');
const fs = require('fs').promises;
const path = require('path');

const app = express();
const port = 3003;

app.use(express.json());
app.use(cors());

// Add middleware to get IP address for all requests
app.use((req, res, next) => {
  console.log(req.headers)
  const ip = req.headers['CF-Connecting-IP']?.split(',')[0] || req.socket.remoteAddress;
  console.log(`[Request] Client IP: ${ip}`);
  next();
});

const dataDir = process.argv[2] ? path.resolve(process.argv[2]) : __dirname;
const outputPath = path.join(__dirname, 'output.txt');

console.log(`[Init] Data directory: ${dataDir}`);
console.log(`[Init] Output path: ${outputPath}`);

app.post('/search', async (req, res) => {
  try {
    const clientIP = req.headers['x-forwarded-for']?.split(',')[0] || req.socket.remoteAddress;
    console.log(`[Search] Request from IP: ${clientIP}`);
    
    const { query } = req.body;
    if (!query) {
      return res.status(400).json({ error: 'Query required' });
    }

    // Clean previous output
    try {
      await fs.unlink(outputPath);
      console.log(`[Cleanup] Removed old ${outputPath}`);
    } catch (err) {
      if (err.code !== 'ENOENT') console.error(`Cleanup error: ${err}`);
    }

    // Build Java command
    const jarPath = path.join(__dirname, 'IsolatedTask9CS335-all.jar');
    const command = `java -Dfile.encoding=UTF-8 -jar "${jarPath}" -FILE_DIR="${dataDir}" -SEARCH=QUERY "${query}" -GUI=false -output="${outputPath}"`;    
    console.log(`[Execution] Command: ${command}`);

    exec(command, { cwd: __dirname }, async (error, stdout, stderr) => {
      // Log execution details
      // console.log(`[Java] stdout: ${stdout}`);
      console.error(`[Java] stderr: ${stderr}`);
      if (error) console.error(`[Java] error: ${error}`);

      try {
        // Verify file exists
        await fs.access(outputPath);
        const stats = await fs.stat(outputPath);
        console.log(`[File] ${outputPath} modified at: ${stats.mtime}`);

        // Read and return results
        const data = await fs.readFile(outputPath, 'utf8');
        const jsonData = JSON.parse(data);
        res.json(JSON.parse(stdout));
      } catch (readError) {
        console.error(`[Error] File handling: ${readError}`);
        res.status(500).json({ error: 'Result processing failed' });
      }
    });
  } catch (err) {
    console.error(`[Critical] Server error: ${err}`);
    res.status(500).json({ error: 'Internal failure' });
  }
});

app.listen(port, () => console.log(`Server running on port ${port}`));