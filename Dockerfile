# ==============================================================================
# OMS-WEB Automated Testing Container
# Environment: Ubuntu Noble + Java 21 + Maven 3.10 + Google Chrome Headless
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21-jammy

# 1. Install Google Chrome Stable & Required System Dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    curl \
    unzip \
    fonts-liberation \
    libasound2 \
    libnss3 \
    libxss1 \
    libappindicator3-1 \
    libgbm1 \
    xdg-utils \
    && wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# 2. Set container working directory
WORKDIR /app

# 3. Copy Maven descriptor and source code
COPY pom.xml .
COPY "OMS WEB" "OMS WEB"

# 4. Pre-compile the entire automation suite (caches dependencies in image)
RUN mvn clean compile -Dfile.encoding=UTF-8

# 5. Environment configuration for Headless Automated Container Execution
ENV CI=true
ENV HEADLESS=true
ENV MAVEN_OPTS="-Dfile.encoding=UTF-8"

# 6. Mounted directory for exported trade files (XML & CSV)
VOLUME /root/Downloads

# 7. Default command: Execute OMSwebmain in automated mode
CMD ["mvn", "exec:java", "-Dexec.mainClass=OMSwebmain"]
