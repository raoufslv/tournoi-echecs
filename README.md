# Tournoi d'échecs en direct

[![CI](https://github.com/raoufslv/tournoi-echecs/actions/workflows/ci.yml/badge.svg)](https://github.com/raoufslv/tournoi-echecs/actions/workflows/ci.yml)

Infrastructure (PostgreSQL, Kafka), `game-service` (Spring Boot), `live-service` (Spring Boot) et `frontend` (Angular).

## Architecture

```text
Angular --REST + JWT--> game-service :8080 --> PostgreSQL (coups + outbox)
                              |
                              +--publisher outbox--> Kafka (chess.game.events)
                                                        |
Angular <-----SSE------ live-service :8081 <-------------+
```

- `game-service` valide les coups (chesslib) et écrit le coup et son événement dans la même transaction. Un publisher envoie ensuite l'outbox vers Kafka : si Kafka est arrêté, le coup reste accepté et l'événement est republié au retour du broker.
- `live-service` consomme le topic et pousse chaque événement en SSE aux spectateurs de la partie (`GET /games/{code}/watch`, lecture seule, sans JWT : le code de partie sert de secret de partage).
- Chaque coup est enregistré avec sa notation SAN (`Nf3`, `Qh4#`). Une partie se termine par mat, pat (résultat enregistré automatiquement) ou abandon (`POST /games/{code}/resign`). Le résultat (`1-0`, `0-1`, `1/2-1/2`) et la raison sont portés par la partie et par l'événement `game.ended`.
- L'historique est servi par `GET /games/{code}/moves`. Le front l'affiche en liste de coups et permet de rejouer la partie pas à pas (boutons, flèches du clavier, clic sur un coup) ; le bandeau « Revenir à la partie en cours » ramène au direct, et un coup reçu pendant la consultation ne déplace pas la vue.
- Le front charge l'état par REST, puis suit le direct en SSE. À chaque reconnexion du flux, il recharge l'état pour rattraper ce qu'il a manqué.

## Ce qui tourne

| Service | Rôle | Accès depuis la machine |
|---|---|---|
| PostgreSQL 16 | Base `chess` | `localhost:5432` |
| Kafka 3.9 | Broker KRaft, un nœud | `localhost:9092` |
| Topic `chess.game.events` | Faits de jeu, une partition | créé au démarrage |
| game-service | API REST, règles, outbox | `localhost:8080` |
| live-service | Flux SSE | `localhost:8081` |
| frontend | Application Angular | `localhost:4200` |

Le réseau Docker s'appelle `chess`. Kafka y est joignable en `kafka:9094`. Depuis la machine, c'est `localhost:9092`.

## Démarrer

```bash
docker compose up -d
docker compose ps
```

PostgreSQL : utilisateur `chess`, mot de passe `chess`, base `chess`. Copier `.env.example` vers `.env` pour changer ces valeurs.

## Lancer l'application

Dans quatre terminaux, dans cet ordre :

```bash
# 1. Infrastructure (à la racine du projet)
docker compose up -d

# 2. Back : http://localhost:8080
cd game-service
./mvnw spring-boot:run        # Windows : .\mvnw.cmd spring-boot:run

# 3. Direct : http://localhost:8081
cd live-service
./mvnw spring-boot:run

# 4. Front : http://localhost:4200
cd frontend
npm install                   # la première fois seulement
npm start
```

Ouvrir http://localhost:4200, créer un compte, créer une partie puis donner le code à un second joueur. Pour jouer à deux sur la même machine, utiliser un second navigateur ou une fenêtre de navigation privée : la session est stockée dans le `localStorage`. Les coups apparaissent chez tous les clients sans rechargement.

Sans `live-service`, le jeu fonctionne mais l'écran n'est plus mis à jour automatiquement : un message « Direct interrompu » s'affiche et le front se reconnecte tout seul.

## Tests

```bash
# Back et live (Docker doit tourner : Testcontainers lance PostgreSQL et Kafka)
cd game-service && ./mvnw test
cd live-service && ./mvnw test

# Front
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless
```

Si Chrome n'est pas installé, pointer `CHROME_BIN` vers un navigateur Chromium, par exemple Edge : `$env:CHROME_BIN = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"` sous PowerShell.

## Configuration

| Variable | Service | Défaut |
|---|---|---|
| `CORS_ALLOWED_ORIGIN` | game-service, live-service | `http://localhost:4200` |
| `KAFKA_BOOTSTRAP_SERVERS` | game-service, live-service | `localhost:9092` |
| `JWT_SECRET` | game-service | valeur de dev (32 caractères minimum en production) |

## Arrêter

```bash
docker compose down
```

`docker compose down -v` efface aussi les données.
