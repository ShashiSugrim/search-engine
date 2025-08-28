const amqp = require('amqplib');
const { exec } = require('child_process');
const fs = require('fs').promises;
const path = require('path');

const QUEUE = process.env.RABBITMQ_QUEUE || 'search_queries';
const RABBIT_URL = process.env.RABBITMQ_URL || 'amqp://localhost';
const dataDir = process.env.FILE_DIR ? path.resolve(process.env.FILE_DIR) : __dirname;
const jarPath = path.join(__dirname, 'IsolatedTask9CS335-all.jar');

async function processQuery(query) {
  const outputPath = path.join(__dirname, `output_${process.pid}.txt`);
  try {
    try { await fs.unlink(outputPath); } catch (e) { if (e.code !== 'ENOENT') throw e; }
    const command = `java -Dfile.encoding=UTF-8 -jar "${jarPath}" -FILE_DIR="${dataDir}" -SEARCH=QUERY "${query}" -GUI=false -output="${outputPath}"`;
    return await new Promise((resolve, reject) => {
      exec(command, { cwd: __dirname, maxBuffer: 1024 * 1024 * 20 }, async (error, stdout, stderr) => {
        if (stderr) console.error(`[Java stderr] ${stderr}`);
        if (error) {
          console.error(`[Java error] ${error}`);
        }
        try {
          await fs.access(outputPath);
          const data = await fs.readFile(outputPath, 'utf8');
          // Prefer stdout JSON if present, otherwise file contents
          const payload = stdout && stdout.trim().startsWith('{') ? stdout : data;
          resolve(payload);
        } catch (readErr) {
          reject(readErr);
        }
      });
    });
  } catch (err) {
    throw err;
  }
}

async function start() {
  const conn = await amqp.connect(RABBIT_URL);
  const ch = await conn.createChannel();
  await ch.assertQueue(QUEUE, { durable: true });
  ch.prefetch(1); // one task at a time per worker
  console.log(`[Worker ${process.pid}] waiting for messages in ${QUEUE}`);
  ch.consume(QUEUE, async (msg) => {
    if (!msg) return;
    const content = msg.content.toString();
    let payload;
    try {
      payload = JSON.parse(content);
    } catch {
      payload = { query: content };
    }
    const { query } = payload;
    const corrId = msg.properties.correlationId;
    const replyTo = msg.properties.replyTo;

    try {
      const result = await processQuery(query);
      if (replyTo && corrId) {
        ch.sendToQueue(replyTo, Buffer.from(result), { correlationId: corrId, contentType: 'application/json' });
      }
      ch.ack(msg);
    } catch (err) {
      console.error(`[Worker ${process.pid}] processing failed:`, err);
      // optional: dead-letter or requeue. We'll ack to avoid poison loop
      if (replyTo && corrId) {
        const errorBody = JSON.stringify({ error: 'processing_failed', details: String(err) });
        ch.sendToQueue(replyTo, Buffer.from(errorBody), { correlationId: corrId, contentType: 'application/json' });
      }
      ch.ack(msg);
    }
  }, { noAck: false });
}

start().catch((e) => {
  console.error('Worker fatal error:', e);
  process.exit(1);
});
