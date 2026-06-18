# ==========================================
# Stage 1: Build Environment
# ==========================================
FROM eclipse-temurin:21-jdk AS builder

# Set environment variables for Android SDK
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    wget \
    unzip \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android SDK command-line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp && \
    mv /tmp/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept Android SDK licenses
RUN yes | sdkmanager --licenses || true

# Set the working directory
WORKDIR /app

# Copy the project files
# Note: Ensure submodules are checked out on host before building:
#   git submodule update --init --recursive
COPY . .

# Set up build arguments for release signing (optional)
# BUILD_TASK can be assembleDebug, assembleRelease, etc.
ARG BUILD_TASK=assembleDebug
ARG RELEASE_KEYSTORE_BASE64=""
ARG RELEASE_PROPERTIES_BASE64=""

# Set env to match the signing logic in app/build.gradle.kts
ENV GITHUB_ACTIONS=true

# Decode credentials (if provided), ensure submodules are present, and run the Gradle build task
RUN if [ -n "$RELEASE_KEYSTORE_BASE64" ] && [ -n "$RELEASE_PROPERTIES_BASE64" ]; then \
        echo "Decoding release keys..." && \
        echo "$RELEASE_KEYSTORE_BASE64" | base64 -d > /tmp/xed.keystore && \
        echo "$RELEASE_PROPERTIES_BASE64" | base64 -d > /tmp/signing.properties; \
    fi && \
    if [ ! -f soraX/editor/build.gradle.kts ]; then \
        echo "soraX submodule not found locally, cloning dynamically..." && \
        rm -rf soraX && \
        git clone --recursive https://github.com/RohitKushvaha01/soraX.git soraX; \
    fi && \
    chmod +x gradlew && \
    ./gradlew ${BUILD_TASK} --no-daemon

# ==========================================
# Stage 2: Export Built APKs (BuildKit friendly)
# ==========================================
FROM scratch AS export-stage
COPY --from=builder /app/app/build/outputs/apk/ /
