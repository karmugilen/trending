#!/usr/bin/env bash
# Script to launch the Trending Web App on your laptop
set -e
cd "$(dirname "$0")/web"
# Bound to localhost (127.0.0.1) to avoid leaking token across local networks
exec zsh -ic "actenv && python -m uvicorn server:app --host 127.0.0.1 --port 8080 --reload"
