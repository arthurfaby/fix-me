# Fixme

Simulation d'une plateforme de trading électronique. Trois exécutables
indépendants dialoguent en TCP via des messages **FIX simplifiés** :

- **Router** — commutateur central. Attribue un ID à chaque client, tient la
  table de routage et transfère les messages d'après leur destinataire.
  Aucune logique métier.
- **Market** — tient un stock d'instruments, exécute ou rejette les ordres.
- **Broker** — CLI de saisie d'ordres, affiche les rapports d'exécution.

```
  ┌──────────┐    :5000        ┌────────┐        :5001    ┌──────────┐
  │  BROKER  │ ───── Buy ────► │ ROUTER │ ──── forward ─► │  MARKET  │
  │ id 100003│ ◄── Executed ── │        │ ◄── Executed ── │ id 100001│
  └──────────┘                 └────────┘                 └──────────┘
```

## Prérequis

- **JDK 25** (`java -version` → 25.x)
- **Maven 3.9+**, tournant lui-même sur le JDK 25 (`mvn -v | grep "Java version"`)

## Build

À la racine :

```bash
mvn clean package
```

Produit un jar exécutable auto-suffisant (dépendances embarquées via
`maven-shade-plugin`) pour chacun des trois composants :

```
fixme-router/target/fixme-router-1.0.0-SNAPSHOT.jar
fixme-market/target/fixme-market-1.0.0-SNAPSHOT.jar
fixme-broker/target/fixme-broker-1.0.0-SNAPSHOT.jar
```

`fixme-common` est une librairie (protocole FIX, pipeline, réseau NIO) partagée
par les trois ; elle est embarquée dans chaque jar, aucun classpath à fournir.

## Lancement

Un composant par terminal (chacun a besoin de son propre TTY pour sa CLI).

```bash
# Terminal 1 — Router (écoute brokers:5000, markets:5001)
java -jar fixme-router/target/fixme-router-1.0.0-SNAPSHOT.jar

# Terminal 2 — Market
java -jar fixme-market/target/fixme-market-1.0.0-SNAPSHOT.jar \
     --port 5001 --instruments=AAPL:1000,GOOG:500

# Terminal 3 — un second Market (prouve l'unicité des IDs)
java -jar fixme-market/target/fixme-market-1.0.0-SNAPSHOT.jar \
     --port 5001 --instruments=TSLA:200

# Terminal 4 — Broker
java -jar fixme-broker/target/fixme-broker-1.0.0-SNAPSHOT.jar --port 5000
```

Un `demo.sh` (voir plus bas) automate ce lancement dans des panes `tmux`.

### Options

| Composant | Option | Défaut | Rôle |
|---|---|---|---|
| Router | `-b`, `--broker-port` | `5000` | Port d'écoute des brokers |
| Router | `-m`, `--market-port` | `5001` | Port d'écoute des markets |
| Market | `--host` | `localhost` | Hôte du Router |
| Market | `-p`, `--port` | `5000` | Port du Router (à mettre à `5001`) |
| Market | `--instruments` | — | `SYMBOL:QTY,SYMBOL:QTY` (ex. `AAPL:1000,GOOG:500`) |
| Market | `--instruments-file` | — | Fichier `.properties` (`AAPL=1000`) |
| Broker | `--host` | `localhost` | Hôte du Router |
| Broker | `-p`, `--port` | `5000` | Port du Router |

> **macOS** : le port 5000 est souvent occupé par le *Receiver AirPlay*
> (ControlCenter). Désactive-le (Réglages → Général → AirDrop et Handoff), ou
> lance avec d'autres ports : `--broker-port 6000 --market-port 6001` côté
> Router, `--port 6001` côté Market, `--port 6000` côté Broker.

### CLI

**Market** — `book` (affiche le stock), `id`, `help`, `exit`.

**Broker** :

```
buy  <marketId> <instrument> <qty> <price>
sell <marketId> <instrument> <qty> <price>
id | help | exit
```

Exemple de session Broker :

```
> buy 100001 AAPL 100 150.50
[SENT]     #1 Buy 100 AAPL @ 150.50 -> market 100001
[EXECUTED] #1 Buy 100 AAPL @ 150.50
> buy 100001 AAPL 999999 150.50
[SENT]     #2 Buy 999999 AAPL @ 150.50 -> market 100001
[REJECTED] #2 : Not enough quantity
> buy 999999 AAPL 10 150.0
[SENT]     #4 Buy 10 AAPL @ 150.0 -> market 999999
[REJECTED] #4 : Unknown target
```

## Format des messages

Champs séparés par `SOH` (`\x01`, noté `|` ci-dessous). Tout message **commence
par l'émetteur (`49`)** et **finit par le checksum (`10`)**.

| Tag | Nom | Exemple |
|---|---|---|
| `49` | SenderID (toujours en premier) | `49=100003` |
| `56` | TargetID (destinataire) | `56=100001` |
| `35` | MsgType : `D`=Order, `8`=ExecutionReport, `3`=Reject, `A`=Logon | `35=D` |
| `11` | ClOrdID (corrélation ordre ↔ rapport) | `11=1` |
| `55` | Instrument | `55=AAPL` |
| `54` | Side : `1`=Buy, `2`=Sell | `54=1` |
| `38` | Quantity | `38=100` |
| `44` | Price | `44=150.50` |
| `39` | OrdStatus : `2`=Executed, `8`=Rejected | `39=2` |
| `58` | Text (raison du rejet) | `58=Not enough quantity` |
| `10` | Checksum (toujours en dernier) | `10=214` |

Exemples complets :

```
Buy       49=100003|56=100001|35=D|11=1|55=AAPL|54=1|38=100|44=150.50|10=214|
Executed  49=100001|56=100003|35=8|11=1|55=AAPL|38=100|44=150.50|39=2|10=187|
Rejected  49=100001|56=100003|35=8|11=2|55=AAPL|38=999999|39=8|58=Not enough quantity|10=093|
Logon     49=0|56=100003|35=A|10=178|          (Router → client, attribution de l'ID)
```

Le **checksum** est la somme de tous les octets jusqu'au `SOH` précédant `10=`,
modulo 256, sur exactement 3 chiffres zero-paddés. Le champ `10=` lui-même
n'entre jamais dans le calcul. L'ID `0` (`49=0`) est réservé au Router ; les
clients reçoivent des IDs à 6 chiffres à partir de `100000`.

## Architecture

```
fixme-common/   protocol (FIX ↔ byte[], checksum) · pipeline (chain-of-responsibility)
                net (Reactor NIO, framing, SerialExecutor)
fixme-router/   RoutingTable · IdGenerator · Checksum → RoutingResolution → Forwarding
fixme-market/   InstrumentBook · OrderType → InstrumentKnown → QuantityAvailable → Execution
fixme-broker/   PendingOrders · Checksum → IdAssignment → ExecutionReport
```

Points de conception clés :

- **NIO brut du JDK** (`Selector` + `SocketChannel` non bloquants), pas de Netty.
  Un seul thread Selector par composant, propriétaire exclusif des channels.
- **Executor framework** pour le traitement des messages ; un `SerialExecutor`
  par connexion garantit l'ordre *par* connexion tout en gardant le parallélisme
  *entre* connexions.
- **Chain-of-responsibility** : chaque composant monte sa chaîne de maillons ;
  ajouter une règle = ajouter une ligne au montage.
- Le Router ne lit que `49`/`56`/`10` — le même code route ordres et rapports.

## Tests

```bash
mvn test
```

Couvre le protocole (checksum, parsing, round-trip), le framing octet-par-octet,
le `SerialExecutor`, les books concurrents (100 threads), et les pipelines de
chaque composant en bout-en-bout via un vrai `Reactor` sur port éphémère.
