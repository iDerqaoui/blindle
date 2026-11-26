# web/ — front Angular de Blindle

Angular 21 (standalone components, signals, sans zone.js) + `@stomp/stompjs`.
**Aucune logique de jeu côté client** : le front affiche les vues envoyées par le serveur
et lui transmet les actions du joueur.

## Lancer en dev

```bash
# terminal 1 — back (port 8080)
mvn -pl app -am spring-boot:run

# terminal 2 — front (port 4200, proxy /api et /ws vers 8080 : proxy.conf.json)
cd web
npm install
npm start
```

Ouvrir http://localhost:4200 dans 3 onglets (une connexion = un joueur ; il faut 3 joueurs).

## Structure

| Fichier | Rôle |
|---|---|
| `src/app/game/game.service.ts` | Client STOMP, signaux `room` / `me` / `error`, horloge corrigée sur `serverTime` |
| `src/app/game/game.types.ts` | Types des vues serveur (`RoomView`, `PlayerView`…) |
| `src/app/app.ts` | Choix de l'écran selon la phase |
| `src/app/screens/` | Accueil, lobby, enchère (+ révélation et placement), verdict, résultats |
| `src/app/ui/` | Indice (rendu selon `hint.type`), timer, carte, tableau des équipes |
| `src/styles.css` | Styles globaux, responsive (une colonne sous 760 px) |

## Build

```bash
npx ng build   # sortie : web/dist/blindle-web
```
