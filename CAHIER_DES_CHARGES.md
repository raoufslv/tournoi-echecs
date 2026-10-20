# Cahier des charges — Tournoi d’échecs en direct

Document de référence du projet perso. Il fixe le périmètre, la stack, l’architecture, les règles métier, les tests, le déploiement et le monitoring.

Statut : cadrage validé, développement pas encore commencé.

---

## 1. Contexte

On joue souvent aux échecs entre amis. L’idée de départ est un petit tournoi entre nous, le même soir, avec un suivi clair pour tout le monde.

Des plateformes existantes couvrent déjà ce besoin de jeu :

- Lichess : tournoi privé gratuit (arène avec mot de passe, ou équipe + Swiss / arène).
- Chess.com : Shareable Tournament, lien privé, Swiss ou arène, en général au moins 4 joueurs pour une arène live.
- Chessly : apprentissage, pas d’organisation de tournois.

Le projet ne cherche pas à remplacer Lichess. Il sert à concevoir soi-même un produit temps réel, et à monter en compétence sur la stack la plus demandée côté junior en France : Java, Spring Boot, PostgreSQL, Docker, tests, et Kafka.

Pourquoi ce sujet plutôt qu’un CRUD :

- un seul coup valide à la fois ;
- plusieurs écrans synchronisés (Blancs, Noirs, spectateur) ;
- un coup illégal ou hors tour doit être refusé par le serveur ;
- la partie se rejoue depuis un journal d’événements, pas depuis un état recopié dans le navigateur.

## 2. Objectifs

### Produit

- Deux amis jouent une partie en direct dans le navigateur.
- Un troisième écran suit la partie sans pouvoir jouer.
- Un coup illégal est refusé.
- La partie se rejoue coup par coup depuis l’historique.
- Plus tard : un mini-tournoi entre amis (invitation, parties, classement).

### Technique (ce que le CV et l’entretien doivent pouvoir défendre)

- API Java 21 / Spring Boot 3, source de vérité côté serveur.
- PostgreSQL, migrations Flyway, contrainte d’unicité sur le numéro de coup.
- Spring Security + JWT, rôles joueur / spectateur.
- Kafka pour publier les faits de jeu, avec outbox dans la même transaction que le coup.
- Second service qui consomme Kafka et pousse le direct.
- Docker Compose, JUnit, Mockito, Testcontainers, GitHub Actions.
- Front Angular + TypeScript qui affiche l’échiquier, sans décider si un coup est légal.

### Hors objectifs

- Moteur d’analyse (Stockfish, évaluation, flèches).
- Classement Elo mondial, anti-triche, matchmaking public.
- Kubernetes, Spring Cloud (Eureka, Config), gRPC, Elasticsearch, Keycloak.
- Application mobile native.
- Remplacer Chess.com ou Lichess.

## 3. Utilisateurs et rôles

| Rôle | Peut |
|---|---|
| Visiteur | Créer un compte, se connecter |
| Joueur Blanc ou Noir | Rejoindre sa partie, jouer uniquement quand c’est son tour |
| Spectateur | Ouvrir une partie avec son code, voir les coups en direct, lancer le rejeu |
| Organisateur (v2 tournoi) | Créer un tournoi, inviter des amis, voir le classement |

Comptes de démo prévus pour l’entretien : deux joueurs + un spectateur, identifiants dans le README.

## 4. Périmètre

### V1 — partie en direct (obligatoire avant le tournoi)

- Inscription et connexion JWT.
- Créer une partie, obtenir un code, le second joueur rejoint.
- Échiquier, coup envoyé au serveur, diffusion aux deux joueurs.
- Refus : coup illégal, mauvais tour, spectateur qui essaie de jouer, double envoi du même numéro de coup.
- Fin de partie : mat, pat, abandon.
- Promotion du pion (le joueur choisit la pièce, le serveur l’applique).
- Spectateur en lecture seule.
- Rejeu ordonné.
- Lancement local en une commande Docker Compose.
- README avec schéma et parcours de démo.

### V2 — tournoi entre amis (seulement quand la V1 est stable)

- Créer un tournoi, inviter par code.
- Rattacher les parties au tournoi.
- Appariement simple (Swiss léger ou arène à 4 joueurs, pas un round robin aller-retour).
- Page de classement : points, parties jouées.

### Explicitement reporté

- Contrôle du temps (blitz, pendule). On peut l’ajouter après la V1 si la démo est déjà solide.
- Chat.
- Plusieurs parties simultanées par utilisateur au-delà du besoin démo.
- Notifications e-mail.

## 5. Parcours de démo (celui de l’entretien)

Trois fenêtres côte à côte.

1. Les Blancs jouent un coup.
2. Les Noirs le voient apparaître.
3. Le spectateur le voit aussi, et ne peut pas jouer.
4. Tentative de coup illégal : l’API refuse, l’échiquier ne change pas.
5. Bouton Rejouer : la partie se reconstruit coup par coup depuis l’historique.

Phrase d’intention, si on demande pourquoi ce projet :

On joue souvent entre amis. Organiser notre tournoi était l’occasion. Lichess et Chess.com savent déjà le faire gratuitement. L’objectif ici est de concevoir le produit : validation côté serveur, événements, persistance, spectateur et rejeu, sur une stack très présente dans les offres Java en France.

## 6. Stack

### Backend

- Java 21
- Spring Boot 3
- Maven
- Spring Web (REST)
- Spring Data JPA
- Spring Security (JWT)
- Validation (Bean Validation)
- PostgreSQL
- Flyway
- Apache Kafka
- chesslib (règles, FEN, coups légaux, mat / pat). Le serveur n’accepte un coup que s’il fait partie des coups légaux renvoyés par la librairie.

Deux services :

- `game-service` : comptes, parties, coups, persistance, publication des événements.
- `live-service` : consommation Kafka, push temps réel, rejeu exposé aux clients.

### Frontend

- Angular
- TypeScript
- Angular CLI
- chessground (échiquier visuel uniquement, enveloppé dans un composant Angular : affichage, drag and drop). Il ne valide pas les règles.
- Composants, services, routeur et HttpClient pour le REST. WebSocket ou SSE pour le direct.

### Outillage

- Docker et Docker Compose
- JUnit 5, Mockito, Testcontainers
- GitHub Actions
- Cursor, avec Extension Pack for Java et Spring Boot Extension Pack

### Plus tard, si utile

- OpenAPI pour documenter l’API
- Redis uniquement si la présence ou les sessions deviennent un vrai problème

## 7. Architecture

```
Navigateur (Angular + chessground)
        |  REST + JWT                |  WebSocket ou SSE
        v                            v
  game-service                  live-service
  (valide le coup,              (consomme les événements,
   écrit en base)                pousse aux écrans)
        |                            ^
        |  même transaction          |
        v                            |
   PostgreSQL                        |
   parties, coups, outbox            |
        |                            |
        +---- publisher outbox ----> Kafka
                                    topic chess.game.events
                                    clé = gameId
```

Règles d’architecture :

- Le navigateur propose un coup. Seul `game-service` décide s’il est légal.
- Le coup, la mise à jour de la partie et la ligne d’outbox sont écrits dans la même transaction PostgreSQL.
- Un publisher envoie ensuite l’outbox vers Kafka. Si Kafka est indisponible, le coup reste en base et sera republié.
- `live-service` ne valide pas les règles. Il diffuse ce que `game-service` a déjà accepté.
- L’ordre par partie est porté par `move_number` en base et par la clé Kafka `gameId`.
- Pas de troisième service tant que ces deux rôles restent clairs.

## 8. Modèle de données (V1)

### users

- id
- email (unique)
- password_hash
- display_name
- created_at

### games

- id
- code (unique, court, partageable)
- white_user_id (nullable jusqu’au join)
- black_user_id (nullable jusqu’au join)
- status : `WAITING`, `IN_PROGRESS`, `FINISHED`
- result : `WHITE_WIN`, `BLACK_WIN`, `DRAW`, null
- end_reason : `CHECKMATE`, `STALEMATE`, `RESIGN`, null
- current_fen
- next_move_number (entier, démarre à 1)
- created_at
- updated_at

### moves

- id
- game_id
- move_number
- player_color : `WHITE` ou `BLACK`
- uci (ex. `e2e4`, `e7e8q` pour une promotion)
- san (ex. `e4`, `e8=Q`) pour l’affichage
- fen_after
- created_at
- contrainte unique `(game_id, move_number)`

### outbox_events

- id
- game_id
- event_type
- payload (JSON)
- move_number (nullable)
- created_at
- published_at (null tant que Kafka n’a pas acquitté)

### V2 uniquement

- tournaments (id, name, code, status, created_by)
- tournament_participants (tournament_id, user_id, score)
- games.tournament_id nullable

Migrations uniquement via Flyway. Pas de `ddl-auto=update` en dehors d’un essai local jetable.

## 9. API (V1)

Authentification : `Authorization: Bearer <jwt>`.

| Méthode | Chemin | Effet |
|---|---|---|
| POST | `/auth/register` | Crée un compte |
| POST | `/auth/login` | Renvoie un JWT |
| POST | `/games` | Crée une partie, renvoie le code. Le créateur choisit sa couleur ou prend les Blancs par défaut |
| POST | `/games/{code}/join` | Le second joueur prend la couleur libre |
| GET | `/games/{code}` | État courant : FEN, statut, joueurs, dernier coup |
| POST | `/games/{code}/moves` | Joue un coup. Corps : `uci` |
| POST | `/games/{code}/resign` | Abandon du joueur authentifié |
| GET | `/games/{code}/moves` | Historique ordonné, pour le rejeu |
| GET | `/games/{code}/watch` | Réservé au flux : le spectateur s’abonne sans être assis à l’échiquier |

Erreurs métier, en JSON, avec un code stable :

- `NOT_YOUR_TURN`
- `ILLEGAL_MOVE`
- `SPECTATOR_CANNOT_MOVE`
- `GAME_NOT_STARTED`
- `GAME_ALREADY_FINISHED`
- `GAME_FULL`

Le live :

- WebSocket (ou SSE) sur `live-service`, canal par `gameId` ou par `code`.
- Message poussé après `move.played` et `game.ended` : FEN, coup, numéro, statut.

CORS : origines du front local, puis l’URL de prod, rien d’autre.

## 10. Événements Kafka

Topic : `chess.game.events`  
Clé : `gameId`  
Un type par message.

### move.played

- gameId
- moveNumber
- uci
- san
- fenAfter
- playedBy : white ou black

### game.ended

- gameId
- result
- endReason
- finalFen

### player.joined

- gameId
- color
- userId

Le consumer doit tolérer un message déjà vu (rejeu de l’offset, republication outbox) : appliquer un coup déjà connu ne doit pas dupliquer l’état.

## 11. Règles de jeu côté serveur

À chaque `POST /moves` :

1. Charger la partie et la verrouiller le temps de la transaction.
2. Vérifier le statut `IN_PROGRESS`, l’identité du joueur, la couleur attendue d’après `next_move_number`.
3. Charger le FEN dans chesslib.
4. N’accepter le coup que s’il est légal. Ne pas se fier à `isMoveLegal` seul si la version de chesslib a des faux positifs : la liste `legalMoves()` fait foi. Cas de test obligatoire : pion qui prend droit devant une pièce adverse, coup refusé.
5. Appliquer le coup, calculer le FEN suivant, détecter mat et pat.
6. Insérer `moves` avec le `move_number` courant. La contrainte unique protège deux requêtes simultanées.
7. Mettre à jour `games` et insérer `outbox_events`.
8. Commit. La publication Kafka se fait après, par le publisher.

Promotion : l’UCI contient la pièce (`e7e8q`). Si le pion arrive sur la dernière rangée sans pièce de promotion, répondre `ILLEGAL_MOVE` et laisser le front redemander le choix.

## 12. Frontend

Écrans V1 :

- Connexion / inscription
- Accueil : créer une partie, rejoindre avec un code, suivre avec un code
- Partie : composant chessground, liste des coups, statut (à vous / à l’adversaire / terminée)
- Spectateur : même échiquier, aucun envoi de coup, badge « spectateur »
- Rejeu : curseur ou boutons précédent / suivant, à partir de `GET /moves`

Le front applique le FEN renvoyé par le serveur. Il peut prévisualiser les cases d’arrivée pour le confort, mais un refus serveur annule le coup visuel.

## 13. Qualité et tests

Minimum pour considérer la V1 présentable :

- Unitaire : coup illégal, mauvais tour, spectateur, partie déjà finie, promotion.
- Concurrence : deux `POST /moves` en parallèle, un seul gagne, l’autre reçoit une erreur, un seul `move_number`.
- Intégration Testcontainers (PostgreSQL + Kafka) : trois coups acceptés produisent trois événements dans l’ordre, le consumer reconstruit le même FEN que la base.
- Le README décrit comment lancer ces tests.

Pas de couverture à 100 %. Les parcours ci-dessus sont le contrat.

## 14. Dépôt et structure

```
tournoi-echecs/
  CAHIER_DES_CHARGES.md
  README.md                  (ajouté au démarrage du code)
  docker-compose.yml
  game-service/
  live-service/
  frontend/
  .github/workflows/ci.yml
```

L'infrastructure locale est en place : PostgreSQL, Kafka et le topic `chess.game.events`. Avancement : `game-service` (comptes, JWT, parties, coups, outbox et publisher Kafka), `live-service` (consumer Kafka et flux SSE) et le front Angular (jeu en direct, historique SAN, rejeu pas à pas, abandon) existent, et la CI GitHub Actions teste les trois modules. Le rejeu s'appuie sur `GET /games/{code}/moves`, pas sur le flux. Restent le tournoi V2, la mise en ligne et le monitoring.

Ordre de construction, à ne pas inverser :

1. Infrastructure : Docker Compose, PostgreSQL, Kafka, topic.
2. Coup légal en REST + PostgreSQL.
3. Front Angular qui joue.
4. Outbox branché sur le Kafka déjà lancé.
5. live-service, spectateur, rejeu.
6. Tests d’intégration et CI.
7. Tournoi V2.
8. Mise en ligne.
9. Monitoring.

Durée indicative : environ 3 à 4 semaines à mi-temps, ou 2 semaines en continu, une fois Java et Spring en main.

## 15. Environnement local

Prérequis machine :

- JDK 21
- Maven
- Node.js (LTS)
- Docker Desktop
- Git

`docker compose up` lance aujourd'hui PostgreSQL et Kafka. `game-service` et `live-service` se lancent à part avec `./mvnw spring-boot:run` et rejoindront le même Compose au moment du déploiement.  
Le front tourne avec `ng serve`, proxy vers les API locales.

Comptes et codes de partie de démo documentés dans le README, jamais de secret réel dans Git.

## 16. CI

GitHub Actions, à chaque push sur la branche de travail :

- `mvn test` pour `game-service` et `live-service` (Testcontainers a besoin de Docker sur le runner)
- `npm ci` puis `ng build` du front Angular
- build des images Docker, sans les publier tant que le déploiement n’existe pas

## 17. Déploiement

Cible retenue pour une démo : un VPS (Hetzner, Scaleway ou OVH, environ 5 à 15 € par mois) + Docker Compose, et le front statique sur Vercel ou Cloudflare Pages.

Sur le VPS :

- Docker Compose de prod : PostgreSQL (volume), Kafka, `game-service`, `live-service`
- Reverse proxy (Caddy ou Nginx) avec HTTPS Let’s Encrypt
- PostgreSQL et Kafka non exposés sur Internet
- JWT secret et mots de passe uniquement en variables d’environnement
- CORS limité à l’URL du front
- WebSocket / SSE autorisés par le proxy

Pipeline souhaité une fois la V1 stable : GitHub Actions build les images, puis déploiement par SSH sur le VPS. Pas de Kubernetes.

Nom de domaine : optionnel. Une IP ou un sous-domaine du VPS suffit pour l’entretien, un domaine est plus propre pour le CV.

## 18. Monitoring et exploitation

Niveau adapté à une démo, pas à un SI bancaire.

Santé :

- Spring Actuator sur les deux services : `/actuator/health` (liveness) et health PostgreSQL / Kafka
- Le reverse proxy ne publie pas les endpoints sensibles (env, heapdump)

Journaux :

- Logs structurés (JSON si simple à activer, sinon texte avec `gameId` et `moveNumber`)
- Niveau info sur coup accepté, coup refusé, fin de partie, échec de publication outbox
- Pas de mot de passe, pas de JWT complet dans les logs

Métriques minimum :

- coups acceptés / refusés
- lag du consumer Kafka (le direct ne doit pas prendre plusieurs secondes)
- taille de l’outbox non publiée (alerte manuelle : si elle grossit, Kafka ou le publisher est en panne)

En pratique sur le VPS : `docker compose logs`, healthchecks Compose, et une vérification manuelle avant une démo (créer une partie, jouer un coup, voir le spectateur bouger).

Sauvegarde : dump PostgreSQL quotidien tant que des parties de démo comptent. Kafka n’est pas la source de vérité : la base et l’outbox le sont. En cas de perte du topic, on republie l’outbox.

Incident type à savoir expliquer : Kafka tombe après le commit. Le coup est en base, `published_at` reste null, le publisher reprend, le spectateur rattrape. Le joueur, lui, a déjà la réponse HTTP.

## 19. Sécurité (niveau projet perso)

- Mots de passe hashés (BCrypt)
- JWT signé, expiration courte, secret hors Git
- Un joueur ne joue que sa couleur
- Le spectateur n’a pas de route de coup
- Validation des entrées (UCI, longueur du code partie)
- Dépendances : pas de secret dans le repo, `.env` ignoré

Hors sujet V1 : OAuth, 2FA, chiffrement du trafic interne entre conteneurs au-delà du réseau Docker.

## 20. Critères de fin

La V1 est terminée quand :

- la démo des trois écrans marche sur une machine propre avec le README ;
- le coup illégal et le double envoi sont couverts par des tests ;
- `docker compose up` suffit pour l’API, la base et Kafka ;
- le README explique l’architecture en un schéma ;
- une URL publique, même sans domaine custom, permet de refaire la démo sans lancer le PC du développeur.

La V2 est terminée quand quatre comptes jouent un mini-tournoi et qu’un classement s’affiche.

## 21. Ce qu’on dira sur le CV (quand c’est vrai)

- Partie d’échecs en direct : API Java 21 / Spring Boot, validation des coups côté serveur, front Angular.
- Journal des coups publié sur Kafka via un outbox, ordre garanti par un numéro de coup.
- Spectateur et rejeu reconstruits depuis les événements, tests Testcontainers sur les coups illégaux.

Kafka, Spring et Java n’apparaissent dans les compétences « maîtrisées » qu’une fois le parcours de démo réellement livré. Le projet reste dans une section Projets, pas dans les expériences professionnelles.
