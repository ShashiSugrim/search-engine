const amqp = require('amqplib');
const { spawn } = require('child_process');
const fs = require('fs').promises;
const path = require('path');

const QUEUE = process.env.RABBITMQ_QUEUE || 'search_queries';
const RABBIT_URL = process.env.RABBITMQ_URL || 'amqp://localhost';
const CANCEL_EXCHANGE = process.env.RABBITMQ_CANCEL_EXCHANGE || 'search_cancels';
const dataDir = process.env.FILE_DIR ? path.resolve(process.env.FILE_DIR) : __dirname;
const jarPath = path.join(__dirname, 'IsolatedTask9CS335-all.jar');
const JOBS_DIR = path.join(__dirname, 'jobs');

async function ensureJobsDir() {
  try { await fs.mkdir(JOBS_DIR, { recursive: true }); } catch {}
}

async function writeJob(id, payload) {
  await ensureJobsDir();
  const file = path.join(JOBS_DIR, `${id}.json`);
  await fs.writeFile(file, JSON.stringify(payload, null, 2));
}

async function processQuery(query, corrId, canceledMap) {
  const outputPath = path.join(__dirname, `output_${process.pid}.txt`);
  try {
    try { await fs.unlink(outputPath); } catch (e) { if (e.code !== 'ENOENT') throw e; }
    const args = [
      '-Dfile.encoding=UTF-8',
      '-jar', jarPath,
      `-FILE_DIR=${dataDir}`,
      '-SEARCH=QUERY', query,
      '-GUI=false',
      `-output=${outputPath}`,
    ];
    return await new Promise((resolve, reject) => {
      const child = spawn('java', args, { cwd: __dirname, detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
      let stdoutBuf = '';
      let stderrBuf = '';
      child.stdout.on('data', (d) => { stdoutBuf += d.toString(); });
      child.stderr.on('data', (d) => { stderrBuf += d.toString(); });

      const killChild = () => {
        try {
          // Kill the entire process group
          process.kill(-child.pid, 'SIGTERM');
          setTimeout(() => { try { process.kill(-child.pid, 'SIGKILL'); } catch {} }, 5000);
        } catch {}
      };

      const cancelCheck = setInterval(() => {
        try {
          if (corrId && canceledMap && canceledMap.has(corrId)) {
            console.log(`[Worker ${process.pid}] killing Java for canceled ${corrId}`);
            clearInterval(cancelCheck);
            killChild();
            reject(new Error('canceled'));
          }
        } catch {}
      }, 250);

      child.on('error', (err) => {
        if (cancelCheck) clearInterval(cancelCheck);
        console.error('[Java spawn error]', err);
        reject(err);
      });
      child.on('close', async () => {
        if (cancelCheck) clearInterval(cancelCheck);
        if (stderrBuf) console.error(`[Java stderr] ${stderrBuf}`);
        try {
          await fs.access(outputPath);
          const data = await fs.readFile(outputPath, 'utf8');
          const out = stdoutBuf && stdoutBuf.trim().startsWith('{') ? stdoutBuf : data;
          resolve(out);
        } catch (readErr) {
          // Even if file missing, return stdout JSON if available
          if (stdoutBuf && stdoutBuf.trim().startsWith('{')) {
            resolve(stdoutBuf);
          } else {
            reject(readErr);
          }
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
  await ch.assertExchange(CANCEL_EXCHANGE, 'fanout', { durable: false });
  ch.prefetch(1); // one task at a time per worker
  console.log(`[Worker ${process.pid}] waiting for messages in ${QUEUE}`);

  // Track canceled correlationIds in-memory with TTL pruning
  const canceled = new Map(); // id -> timestamp
  const CANCEL_TTL_MS = Number(process.env.CANCEL_RETENTION_MS || 120_000);
  const { queue: cancelQ } = await ch.assertQueue('', { exclusive: true, autoDelete: true, durable: false });
  await ch.bindQueue(cancelQ, CANCEL_EXCHANGE, '');
  await ch.consume(cancelQ, (m) => {
    if (!m) return;
    const id = m.content.toString();
    canceled.set(id, Date.now());
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

      const result = await processQuery(query, corrId, canceled);
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
        ch.sendToQueue(replyTo, Buffer.from(result), { correlationId: corrId, contentType: 'application/json' });
      }
      if (jobId) {
        try {
          const text = String(result);
          let body;
          try { body = JSON.parse(text); } catch { body = { result: text }; }
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
