const express = require('express');
const { exec } = require('child_process');
const cors = require('cors');
const fs = require('fs').promises;
const path = require('path');
const amqp = require('amqplib');
const crypto = require('crypto');
const os = require('os');

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
const CANCEL_EXCHANGE = process.env.RABBITMQ_CANCEL_EXCHANGE || 'search_cancels';
const JOBS_DIR = path.join(__dirname, 'jobs');

console.log(`[Init] Data directory: ${dataDir}`);
console.log(`[Init] Output path: ${outputPath}`);

let amqpConn;
let amqpChannel;

async function ensureAmqp() {
  if (amqpChannel) return amqpChannel;
  amqpConn = await amqp.connect(RABBIT_URL);
  amqpChannel = await amqpConn.createChannel();
  await amqpChannel.assertQueue(QUEUE, { durable: true });
  await amqpChannel.assertExchange(CANCEL_EXCHANGE, 'fanout', { durable: false });
  return amqpChannel;
}

async function ensureJobsDir() {
  try {
    await fs.mkdir(JOBS_DIR, { recursive: true });
  } catch {}
}

async function writeJob(id, payload) {
  await ensureJobsDir();
  const file = path.join(JOBS_DIR, `${id}.json`);
  await fs.writeFile(file, JSON.stringify(payload, null, 2));
}

async function readJob(id) {
  const file = path.join(JOBS_DIR, `${id}.json`);
  const txt = await fs.readFile(file, 'utf8');
  return JSON.parse(txt);
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

    const REQUEST_TTL_MS = Number(process.env.SEARCH_REQUEST_TTL_MS || 600000);
    const timer = setTimeout(() => {
      if (!settled) {
        // Publish cancel before responding so workers can drop/kill work
        try { ch.publish(CANCEL_EXCHANGE, '', Buffer.from(corrId), { contentType: 'text/plain' }); } catch {}
        settled = true;
        cleanup();
        return res.status(504).json({ error: 'Search timed out' });
      }
    }, REQUEST_TTL_MS);

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

    const publishCancel = () => {
      try {
        ch.publish(CANCEL_EXCHANGE, '', Buffer.from(corrId), { contentType: 'text/plain' });
      } catch (e) {
        console.error('[Cancel] publish failed:', e);
      }
    };

    // If the client disconnects before we respond, cancel the job
    const onClose = () => {
      if (!settled) {
        settled = true;
        clearTimeout(timer);
        publishCancel();
        cleanup();
      }
    };
    req.on('aborted', onClose);
    res.on('close', onClose);

    const body = JSON.stringify({ query, meta: { ip: clientIP, ts: Date.now() } });
    ch.sendToQueue(QUEUE, Buffer.from(body), {
      correlationId: corrId,
      replyTo: replyQueue,
      persistent: true,
      contentType: 'application/json',
      // Per-message expiration: auto-drop if not consumed in time
      expiration: String(REQUEST_TTL_MS + 5000),
    });
  } catch (err) {
    console.error(`[Critical] Server error: ${err}`);
    res.status(500).json({ error: 'Internal failure' });
  }
});

// Async job API for massive fan-in without HTTP timeouts
app.post('/jobs', async (req, res) => {
  try {
    const clientIP = req.headers['x-forwarded-for']?.split(',')[0] || req.socket.remoteAddress;
    const { query } = req.body || {};
    if (!query) return res.status(400).json({ error: 'Query required' });

    const ch = await ensureAmqp();
    const id = crypto.randomUUID();
    const now = Date.now();
    await writeJob(id, { id, status: 'queued', query, meta: { ip: clientIP, queuedAt: now } });

    const body = JSON.stringify({ query, jobId: id, meta: { ip: clientIP, ts: now } });
    ch.sendToQueue(QUEUE, Buffer.from(body), {
      correlationId: id,
      persistent: true,
      contentType: 'application/json',
    });
    res.status(202).json({ jobId: id });
  } catch (e) {
    console.error('POST /jobs error', e);
    res.status(500).json({ error: 'Internal failure' });
  }
});

app.get('/jobs/:id', async (req, res) => {
  try {
    const job = await readJob(req.params.id);
    res.json(job);
  } catch (e) {
    if (e.code === 'ENOENT') return res.status(404).json({ error: 'Not found' });
    console.error('GET /jobs error', e);
    res.status(500).json({ error: 'Internal failure' });
  }
});

app.delete('/jobs/:id', async (req, res) => {
  try {
    const ch = await ensureAmqp();
    const id = req.params.id;
    ch.publish(CANCEL_EXCHANGE, '', Buffer.from(id), { contentType: 'text/plain' });
    // Best-effort mark as canceled; worker will enforce if not yet finished
    try {
      const job = await readJob(id);
      await writeJob(id, { ...job, status: 'canceled', canceledAt: Date.now() });
    } catch {}
    res.status(202).json({ jobId: id, status: 'canceled' });
  } catch (e) {
    console.error('DELETE /jobs error', e);
    res.status(500).json({ error: 'Internal failure' });
  }
});

app.listen(port, () => console.log(`Server running on port ${port}`));