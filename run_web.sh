#!/usr/bin/env bash
# Launch the Trending web viewer on this computer.
# Uses ./.venv (created on first run) so it works without any personal shell aliases.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
VENV="${TRENDING_VENV:-$ROOT/.venv}"
if [ ! -x "$VENV/bin/python" ]; then
    echo "Creating virtualenv in $VENV ..."
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install -q -r "$ROOT/web/requirements.txt"
fi
cd "$ROOT/web"
# Bound to localhost (127.0.0.1) so the server is not reachable from the network.
exec "$VENV/bin/python" -m uvicorn server:app --host 127.0.0.1 --port 8080 --reload
