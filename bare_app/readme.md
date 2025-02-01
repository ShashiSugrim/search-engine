
1. build container
docker build -t search-engine-cs355 .
2. run container
docker run -p 3000:3000 -v /mnt/f/fall2024/IsolatedTask9CS335/testJar:/app/testJar search-engine-cs355 /app/testJar