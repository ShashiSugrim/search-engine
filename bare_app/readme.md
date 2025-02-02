
1. build container
<!-- docker build --no-cache -t  search-engine-cs355 . -->
docker build -t  search-engine-cs355 .

2. run container
docker run -p 3000:3000 -v /mnt/f/fall2024/IsolatedTask9CS335/testJar:/app/testJar search-engine-cs355 /app/testJar

or 
docker run -p 3000:3000 search-engine-cs355


<!-- all in one -->
docker build -t search-engine-cs355 . && docker run -d --name search-engine -p 3000:3000 search-engine-cs355


to stop container
docker stop search-engine


not detached
docker build -t search-engine-cs355 . && docker run --name search-engine -p 3000:3000 search-engine-cs355