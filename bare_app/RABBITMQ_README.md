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


## Bugs
1. Too many connections left open
- send 1000s of requests and they handle them but leave too many open connections

2. too many queues get created but in search-queries queue, we already dumped a lot of tasks
 - this means a user cancelled their requests, so i ended a load test early, but it seems that the server still is processing them because 1000s of queues were created, but in search_queries queue, all of the items were already cleared from the quueue, so we are wasting time processing the queries. 


## todo

1. keep load testing the backend with k6 and python script to see it handle multiple requests but it should also be able to handle if we cancel the requests, and shouldnt open or leave too many connections open after they handled many requests