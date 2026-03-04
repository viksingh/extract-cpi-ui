FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Install Java 17, Maven, JavaFX runtime dependencies, VNC, and noVNC
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    maven \
    # JavaFX native dependencies
    libgtk-3-0 \
    libglib2.0-0 \
    libx11-6 \
    libxxf86vm1 \
    libgl1-mesa-glx \
    libgl1-mesa-dri \
    libxtst6 \
    libxi6 \
    libasound2 \
    libfreetype6 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libpangoft2-1.0-0 \
    libcairo2 \
    libatk1.0-0 \
    libgdk-pixbuf-2.0-0 \
    # VNC + noVNC
    tigervnc-standalone-server \
    tigervnc-common \
    novnc \
    websockify \
    # Window manager (lightweight)
    fluxbox \
    # Utilities
    git \
    curl \
    dbus-x11 \
    xfonts-base \
    && rm -rf /var/lib/apt/lists/*

# Set Java home
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# VNC/noVNC configuration
ENV DISPLAY=:1
ENV VNC_PORT=5901
ENV NOVNC_PORT=6080
ENV VNC_RESOLUTION=1280x800

# Create VNC startup script
RUN mkdir -p /root/.vnc && \
    echo "#!/bin/bash" > /root/.vnc/xstartup && \
    echo "fluxbox &" >> /root/.vnc/xstartup && \
    chmod +x /root/.vnc/xstartup

# Set a default VNC password (change as needed)
RUN mkdir -p /root/.vnc && \
    echo "cpiextract" | vncpasswd -f > /root/.vnc/passwd && \
    chmod 600 /root/.vnc/passwd

# Create startup script for VNC + noVNC
COPY start-vnc.sh /usr/local/bin/start-vnc.sh
RUN chmod +x /usr/local/bin/start-vnc.sh

WORKDIR /workspace

EXPOSE 5901 6080
