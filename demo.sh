#!/usr/bin/env bash
#
# Runs Router + Market + Broker in three tmux panes, each with its own TTY
# (required: the Market/Broker CLIs read stdin interactively).
#
# Usage:   ./demo.sh
# Ports:   overridable via environment variables, e.g.
#          BROKER_PORT=6000 MARKET_PORT=6001 ./demo.sh     (handy on macOS,
#          where port 5000 is often taken by the AirPlay Receiver)
#
set -euo pipefail

BROKER_PORT="${BROKER_PORT:-5000}"
MARKET_PORT="${MARKET_PORT:-5001}"
INSTRUMENTS="${INSTRUMENTS:-AAPL:1000,GOOG:500}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="1.0.0-SNAPSHOT"
ROUTER_JAR="$ROOT/fixme-router/target/fixme-router-$VERSION.jar"
MARKET_JAR="$ROOT/fixme-market/target/fixme-market-$VERSION.jar"
BROKER_JAR="$ROOT/fixme-broker/target/fixme-broker-$VERSION.jar"

if ! command -v tmux >/dev/null 2>&1; then
    cat >&2 <<EOF
tmux not found. Install it (brew install tmux / apt install tmux), or run the
three components by hand in three terminals:

  java -jar $ROUTER_JAR --broker-port $BROKER_PORT --market-port $MARKET_PORT
  java -jar $MARKET_JAR --port $MARKET_PORT --instruments=$INSTRUMENTS
  java -jar $BROKER_JAR --port $BROKER_PORT
EOF
    exit 1
fi

if [[ ! -f "$ROUTER_JAR" || ! -f "$MARKET_JAR" || ! -f "$BROKER_JAR" ]]; then
    echo "Jars missing, building (mvn clean package)..."
    (cd "$ROOT" && mvn -q clean package)
fi

SESSION="fixme"
tmux kill-session -t "$SESSION" 2>/dev/null || true

# Pane 0: Router
tmux new-session -d -s "$SESSION" -n demo \
    "java -jar '$ROUTER_JAR' --broker-port $BROKER_PORT --market-port $MARKET_PORT; read -n1 -r -p 'Router stopped. Press a key to close.'"

# Pane 1: Market (let the Router bind first)
tmux split-window -h -t "$SESSION:demo" \
    "sleep 1; java -jar '$MARKET_JAR' --port $MARKET_PORT --instruments=$INSTRUMENTS; read -n1 -r -p 'Market stopped. Press a key to close.'"

# Pane 2: Broker (let Router + Market come up)
tmux split-window -v -t "$SESSION:demo" \
    "sleep 2; java -jar '$BROKER_JAR' --port $BROKER_PORT; read -n1 -r -p 'Broker stopped. Press a key to close.'"

tmux select-layout -t "$SESSION:demo" tiled
tmux select-pane -t "$SESSION:demo.2"   # focus the Broker to type orders
tmux attach -t "$SESSION"
