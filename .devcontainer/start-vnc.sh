#!/bin/bash
# Start VNC server and noVNC for browser-based access

# Kill any existing VNC sessions
vncserver -kill :1 2>/dev/null || true

# Start VNC server
vncserver :1 -geometry ${VNC_RESOLUTION:-1280x800} -depth 24

# Start noVNC (browser access at http://localhost:6080/vnc.html)
websockify --web=/usr/share/novnc/ ${NOVNC_PORT:-6080} localhost:${VNC_PORT:-5901} &

echo ""
echo "=========================================="
echo "  noVNC running at http://localhost:6080"
echo "  VNC password: cpiextract"
echo "=========================================="
echo ""
