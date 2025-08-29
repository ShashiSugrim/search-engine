# RabbitMQ integration (search_queries)

## to run
```
pm2 delete api worker
SEARCH_REQUEST_TTL_MS=600000 RABBITMQ_URL="amqp://guest:guest@localhost:5672" pm2 start ecosystem.config.js --update-env
```

# clean queue
docker exec -it rabbitmq rabbitmqctl purge_queue search_queries

This backend offloads heavy search requests to RabbitMQ so only 4 concurrent searches are processed.

- Queue: `search_queries`
- API publishes an RPC request and waits for a reply.
- 4 workers (pm2) consume with `prefetch(1)` so one job per worker.

## Prereqs
- RabbitMQ running on localhost (`amqp://localhost`)
- Java (for `IsolatedTask9CS335-all.jar`)
- Node.js 18+

## Run
- Install deps: npm install
- Start with pm2: pm2 start ecosystem.config.js
- API at http://localhost:3003

## Env
- `RABBITMQ_URL` (default `amqp://localhost`)
- `RABBITMQ_QUEUE` (default `search_queries`)
- `FILE_DIR` to override data directory

## Test
POST /search with `{ "query": "your text" }`. API enqueues and waits up to 60s for a worker response.
