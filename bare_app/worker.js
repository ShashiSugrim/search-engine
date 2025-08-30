const amqp = require('amqplib');
const { spawn } = require('child_process');
const fs = require('fs').promises;
const path = require('path');

const QUEUE = process.env.RABBITMQ_QUEUE || 'search_queries';
const RABBIT_URL = process.env.RABBITMQ_URL || 'amqp://localhost';
const CANCEL_EXCHANGE = process.env.RABBITMQ_CANCEL_EXCHANGE || 'search_cancels';
const dataDir = process.env.FILE_DIR ? path.resolve(process.env.FILE_DIR) : __dirname;
const jarPath = path.join(__dirname, 'search-engine-all.jar');
const JOBS_DIR = path.join(__dirname, 'jobs');

async function ensureJobsDir() {
  try { await fs.mkdir(JOBS_DIR, { recursive: true }); } catch {}
}

async function writeJob(id, payload) {
  await ensureJobsDir();
  const file = path.join(JOBS_DIR, `${id}.json`);
  await fs.writeFile(file, JSON.stringify(payload, null, 2));
}

class WarmJava {
  constructor(jarPath, cwd) {
    this.jarPath = jarPath;
    this.cwd = cwd;
    this.child = null;
    this.buffer = '';
  this.inflight = null; // { resolve, reject, corrId }
    this.start();
  }

  start() {
    const args = ['-Dfile.encoding=UTF-8', '-jar', this.jarPath, '.'];
    this.child = spawn('java', args, { cwd: this.cwd, stdio: ['pipe', 'pipe', 'pipe'] });
    this.child.stdout.setEncoding('utf8');
    this.child.stderr.setEncoding('utf8');
    this.child.stdout.on('data', (d) => this.onStdout(d));
    this.child.stderr.on('data', (d) => console.error(`[Java stderr ${process.pid}]`, d));
    this.child.on('exit', (code, signal) => {
      console.error(`[WarmJava] exited code=${code} signal=${signal} (pid ${process.pid}), restarting...`);
      // Reject current inflight if any
      if (this.inflight) {
        this.inflight.reject(new Error('java_exited'));
        this.inflight = null;
      }
      setTimeout(() => this.start(), 1000);
    });
  }

  onStdout(chunk) {
    this.buffer += chunk;
    // Try to extract one JSON object from buffer using brace counting
    const start = this.buffer.indexOf('{');
    if (start === -1) return;
    let count = 0;
    let endIndex = -1;
    for (let i = start; i < this.buffer.length; i++) {
      const c = this.buffer[i];
      if (c === '{') count++;
      else if (c === '}') {
        count--;
        if (count === 0) { endIndex = i; break; }
      }
    }
    if (endIndex !== -1) {
      const jsonText = this.buffer.slice(start, endIndex + 1);
      const rest = this.buffer.slice(endIndex + 1);
      this.buffer = rest; // keep any trailing output for next round
      if (this.inflight) {
        this.inflight.resolve(jsonText);
        this.inflight = null;
      }
    }
  }

  async request(query, corrId) {
    // Only one at a time expected; ensure no overlap
    if (this.inflight) throw new Error('inflight_request');
    return new Promise((resolve, reject) => {
      this.inflight = { resolve, reject, corrId };
      try {
        this.child.stdin.write(String(query).trim() + '\n');
      } catch (e) {
        this.inflight = null;
        reject(e);
      }
    });
  }

  // Best-effort cancel current request by sending a special line the Java
  // side understands as termination, or closing stdin if unsupported.
  cancel(corrId) {
    if (!this.inflight || (corrId && this.inflight.corrId !== corrId)) return;
    // Reset state so next request can proceed
    const inflight = this.inflight;
    this.inflight = null;
    try {
      // Try graceful termination first
      this.child.kill('SIGTERM');
    } catch {}
    // Force kill if it doesn't exit promptly
    setTimeout(() => {
      try { this.child.kill('SIGKILL'); } catch {}
    }, 1500);
    // Reject the waiting promise so caller can mark canceled
    inflight.reject(new Error('canceled'));
  }
}

function amqpOptionsWithName(urlString, name) {
  try {
    const u = new URL(urlString);
    const protocol = (u.protocol || 'amqp:').replace(':', '');
    const vhostPath = u.pathname || '/';
    const vhost = decodeURIComponent(vhostPath === '/' ? '/' : vhostPath.replace(/^\//, ''));
    const opts = {
      protocol,
      hostname: u.hostname || 'localhost',
      port: u.port ? Number(u.port) : (protocol === 'amqps' ? 5671 : 5672),
      username: decodeURIComponent(u.username || 'guest'),
      password: decodeURIComponent(u.password || 'guest'),
      vhost,
      locale: u.searchParams.get('locale') || 'en_US',
      frameMax: u.searchParams.get('frameMax') ? Number(u.searchParams.get('frameMax')) : undefined,
      heartbeat: u.searchParams.get('heartbeat') ? Number(u.searchParams.get('heartbeat')) : undefined,
      channelMax: u.searchParams.get('channelMax') ? Number(u.searchParams.get('channelMax')) : undefined,
      clientProperties: { connection_name: name },
    };
    // Remove undefined keys
    Object.keys(opts).forEach((k) => opts[k] === undefined && delete opts[k]);
    return opts;
  } catch {
    return { protocol: 'amqp', hostname: 'localhost', port: 5672, clientProperties: { connection_name: name } };
  }
}

async function start() {
  const conn = await amqp.connect(amqpOptionsWithName(RABBIT_URL, `worker-${process.pid}`));
  const ch = await conn.createChannel();
  await ch.assertQueue(QUEUE, { durable: true });
  await ch.assertExchange(CANCEL_EXCHANGE, 'fanout', { durable: false });
  ch.prefetch(1); // one task at a time per worker
  console.log(`[Worker ${process.pid}] waiting for messages in ${QUEUE}`);

  // Start warm Java process rooted at dataDir
  const engine = new WarmJava(jarPath, dataDir);

  // Track canceled correlationIds in-memory with TTL pruning
  const canceled = new Map(); // id -> timestamp
  const CANCEL_TTL_MS = Number(process.env.CANCEL_RETENTION_MS || 120_000);
  const { queue: cancelQ } = await ch.assertQueue('', { exclusive: true, autoDelete: true, durable: false });
  await ch.bindQueue(cancelQ, CANCEL_EXCHANGE, '');
  await ch.consume(cancelQ, (m) => {
    if (!m) return;
    const id = m.content.toString();
    canceled.set(id, Date.now());
  // Actively cancel in-flight Java task if it matches
  try { engine.cancel(id); } catch {}
  }, { noAck: true });

  // Periodic prune
  setInterval(() => {
    const now = Date.now();
    for (const [id, ts] of canceled.entries()) {
      if (now - ts > CANCEL_TTL_MS) {
        canceled.delete(id);
      }
    }
  }, Math.min(CANCEL_TTL_MS, 60_000));
  ch.consume(QUEUE, async (msg) => {
    if (!msg) return;
    const content = msg.content.toString();
    let payload;
    try {
      payload = JSON.parse(content);
    } catch {
      payload = { query: content };
    }
    const { query, jobId } = payload;
    const corrId = msg.properties.correlationId;
    const replyTo = msg.properties.replyTo;

    try {
      // If canceled before starting, ack and drop
      if (corrId && canceled.has(corrId)) {
        console.log(`[Worker ${process.pid}] skipped canceled job ${corrId}`);
        ch.ack(msg);
        return;
      }

      // If this is an async job, mark running
      if (jobId) {
        try {
          await writeJob(jobId, { id: jobId, status: 'running', query, startedAt: Date.now() });
        } catch (e) { console.error('writeJob running error', e); }
      }

      // Send to warm Java and wait for JSON
      const jsonText = await engine.request(query, corrId);
      // If canceled while running, we still ack and do not reply
      if (corrId && canceled.has(corrId)) {
        console.log(`[Worker ${process.pid}] finished but client canceled ${corrId}`);
        if (jobId) {
          try { await writeJob(jobId, { id: jobId, status: 'canceled', query, finishedAt: Date.now() }); } catch {}
        }
        ch.ack(msg);
        return;
      }
      if (replyTo && corrId) {
        ch.sendToQueue(replyTo, Buffer.from(jsonText), { correlationId: corrId, contentType: 'application/json' });
      }
      if (jobId) {
        try {
          const text = String(jsonText);
          let body; try { body = JSON.parse(text); } catch { body = { result: text }; }
          await writeJob(jobId, { id: jobId, status: 'done', query, result: body, finishedAt: Date.now() });
        } catch (e) { console.error('writeJob done error', e); }
      }
      ch.ack(msg);
    } catch (err) {
      console.error(`[Worker ${process.pid}] processing failed:`, err);
      // If canceled, do not reply
      if (String(err && err.message).toLowerCase().includes('canceled')) {
        if (jobId) {
          try { await writeJob(jobId, { id: jobId, status: 'canceled', query, finishedAt: Date.now() }); } catch {}
        }
        ch.ack(msg);
        return;
      }
      if (String(err && err.message).toLowerCase().includes('java_exited')) {
        // Java restarted; mark failed for this job
        if (jobId) {
          try { await writeJob(jobId, { id: jobId, status: 'failed', query, finishedAt: Date.now(), error: 'java_exited' }); } catch {}
        }
      }
      // optional: dead-letter or requeue. We'll ack and return an error once.
      if (replyTo && corrId) {
        const errorBody = JSON.stringify({ error: 'processing_failed', details: String(err) });
        ch.sendToQueue(replyTo, Buffer.from(errorBody), { correlationId: corrId, contentType: 'application/json' });
      }
      if (jobId) {
        try { await writeJob(jobId, { id: jobId, status: 'failed', query, finishedAt: Date.now(), error: String(err) }); } catch {}
      }
      ch.ack(msg);
    }
  }, { noAck: false });
}

start().catch((e) => {
  console.error('Worker fatal error:', e);
  process.exit(1);
});
