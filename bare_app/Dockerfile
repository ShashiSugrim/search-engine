FROM node:20.14.0-slim

# Install OpenJDK 17 runtime
RUN apt-get update && \
    apt-get install -y openjdk-17-jre-headless && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy package files and install dependencies
COPY package*.json ./
RUN npm install

# Copy the rest of the app including js_backend.js and the jar file
COPY . .

EXPOSE 3000

# Use ENTRYPOINT so that additional parameters are passed to the command
ENTRYPOINT ["node", "js_backend.js"]