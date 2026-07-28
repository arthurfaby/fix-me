#!/usr/bin/env bash
#
# Lance Router + Market + Broker dans trois panes tmux, chacun avec son propre
# TTY (indispensable : les CLI Market/Broker lisent stdin en interactif).
#
# Usage :   ./demo.sh
# Ports :   surchargeables par variables d'environnement, ex.
#           BROKER_PORT=6000 MARKET_PORT=6001 ./demo.sh     (utile sur macOS,
#           ou le port 5000 est souvent pris par le Receiver AirPlay)
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
tmux introuvable. Installe-le (brew install tmux / apt install tmux), ou lance
les trois composants a la main dans trois terminaux :

  java -jar $ROUTER_JAR --broker-port $BROKER_PORT --market-port $MARKET_PORT
  java -jar $MARKET_JAR --port $MARKET_PORT --instruments=$INSTRUMENTS
  java -jar $BROKER_JAR --port $BROKER_PORT
EOF
    exit 1
fi

if [[ ! -f "$ROUTER_JAR" || ! -f "$MARKET_JAR" || ! -f "$BROKER_JAR" ]]; then
    echo "Jars absents, build en cours (mvn clean package)..."
    (cd "$ROOT" && mvn -q clean package)
fi

SESSION="fixme"
tmux kill-session -t "$SESSION" 2>/dev/null || true

# Pane 0 : Router
tmux new-session -d -s "$SESSION" -n demo \
    "java -jar '$ROUTER_JAR' --broker-port $BROKER_PORT --market-port $MARKET_PORT; read -n1 -r -p 'Router arrete. Touche pour fermer.'"

# Pane 1 : Market (laisse le Router se lier d'abord)
tmux split-window -h -t "$SESSION:demo" \
    "sleep 1; java -jar '$MARKET_JAR' --port $MARKET_PORT --instruments=$INSTRUMENTS; read -n1 -r -p 'Market arrete. Touche pour fermer.'"

# Pane 2 : Broker (laisse Router + Market s'etablir)
tmux split-window -v -t "$SESSION:demo" \
    "sleep 2; java -jar '$BROKER_JAR' --port $BROKER_PORT; read -n1 -r -p 'Broker arrete. Touche pour fermer.'"

tmux select-layout -t "$SESSION:demo" tiled
tmux select-pane -t "$SESSION:demo.2"   # focus sur le Broker pour taper les ordres
tmux attach -t "$SESSION"
