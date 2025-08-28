const express = require('express');
const { exec } = require('child_process');
const cors = require('cors');
const fs = require('fs').promises;
const path = require('path');
const amqp = require('amqplib');
const crypto = require('crypto');

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

const dataDir = process.env.FILE_DIR ? path.resolve(process.env.FILE_DIR) : (process.argv[2] ? path.resolve(process.argv[2]) : __dirname);
const outputPath = path.join(__dirname, 'output.txt');
const RABBIT_URL = process.env.RABBITMQ_URL || 'amqp://localhost';
const QUEUE = process.env.RABBITMQ_QUEUE || 'search_queries';

console.log(`[Init] Data directory: ${dataDir}`);
console.log(`[Init] Output path: ${outputPath}`);

let amqpConn;
let amqpChannel;

async function ensureAmqp() {
  if (amqpChannel) return amqpChannel;
  amqpConn = await amqp.connect(RABBIT_URL);
  amqpChannel = await amqpConn.createChannel();
  await amqpChannel.assertQueue(QUEUE, { durable: true });
  return amqpChannel;
}

app.post('/search', async (req, res) => {
  try {
    const clientIP = req.headers['x-forwarded-for']?.split(',')[0] || req.socket.remoteAddress;
    console.log(`[Search] Request from IP: ${clientIP}`);
    
    const { query } = req.body;
    if (!query) {
      return res.status(400).json({ error: 'Query required' });
    }

    // Send RPC to RabbitMQ and await response from one of the workers
    const ch = await ensureAmqp();
    const corrId = crypto.randomUUID();
    const { queue: replyQueue } = await ch.assertQueue('', { exclusive: true, durable: false, autoDelete: true });
    let settled = false;

    const cleanup = () => {
      try { ch.deleteQueue(replyQueue).catch(() => {}); } catch {}
    };

    const timer = setTimeout(() => {
      if (!settled) {
        settled = true;
        cleanup();
        return res.status(504).json({ error: 'Search timed out' });
      }
    }, 60_000);

    await ch.consume(replyQueue, (msg) => {
      if (!msg) return;
      if (msg.properties.correlationId !== corrId) return;
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        const text = msg.content.toString();
        let json;
        try {
          json = JSON.parse(text);
        } catch {
          json = { result: text };
        }
        res.json(json);
      } catch (err) {
        res.status(500).json({ error: 'Invalid worker response' });
      } finally {
        cleanup();
      }
    }, { noAck: true });

    const body = JSON.stringify({ query, meta: { ip: clientIP, ts: Date.now() } });
    ch.sendToQueue(QUEUE, Buffer.from(body), {
      correlationId: corrId,
      replyTo: replyQueue,
      persistent: true,
      contentType: 'application/json',
    });
  } catch (err) {
    console.error(`[Critical] Server error: ${err}`);
    res.status(500).json({ error: 'Internal failure' });
  }
});

app.listen(port, () => console.log(`Server running on port ${port}`));