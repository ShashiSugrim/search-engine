FROM node:20.14.0-bookworm

# Install Java and configure locales
RUN apt-get update && \
    apt-get install -y \
    openjdk-17-jre-headless \
    locales && \
    rm -rf /var/lib/apt/lists/*

# Configure UTF-8 locale
RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen
ENV LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

# Verify environment
RUN java -version && \
    node -v && \
    locale

WORKDIR /app

COPY package*.json ./
RUN npm install
COPY . .

CMD ["node", "js_backend.js"]