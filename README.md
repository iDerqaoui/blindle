# Blindle (blindle.me)

Jeu d'enchères à l'aveugle multijoueur en ligne (3 à 8 joueurs, ou solo contre des bots).
Mode principal « Anime Blind Bid » : le piocheur voit la carte et la vend, les autres
surenchérissent à l'aveugle ; à la fin, l'équipe la plus rare gagne.

- Spécification et feuille de route : `docs/PROJECT.md`
- Architecture et protocole : `docs/ARCHITECTURE.md`
- Mise en ligne, connexion Google / Discord : `docs/DEPLOY.md`
- Consignes de travail (commandes, conventions, étape en cours) : `CLAUDE.md`

## Lancer en local

    docker compose up -d db                     # PostgreSQL (Docker Desktop lancé)
    mvn clean install -DskipTests
    cd web && npm install && npx ng build && cd ..
    set BLINDLE_ADMIN_PASSWORD=un-mot-de-passe  # accès à la page admin
    java -jar app/target/app-0.1.0-SNAPSHOT.jar --blindle.content.dir=content ^
         --spring.web.resources.static-locations=file:web/dist/blindle-web/browser/

Puis http://localhost:8080 ; page admin : bouton « Connexion » → mot de passe admin → `#admin`.
Partage entre amis : `cloudflared tunnel --url http://localhost:8080`.

## À faire (demandes du 2026-11-27, à reprendre le 2026-11-28)

Aujourd'hui la page admin (`#admin`) ne permet que : changer le rang de puissance S–D et le nom
d'un personnage anime, en ajouter un depuis MyAnimeList, en retirer un, voir la rareté prévue.

1. **Modifier les valeurs des personnages depuis la page admin** : au-delà du rang et du nom,
   pouvoir forcer une rareté, corriger la popularité, changer l'image, les indices et les tags.
   À trancher : une valeur forcée remplace-t-elle le calcul (rareté imposée hors quotas) ou
   l'ajuste-t-elle seulement ?
2. **Personnaliser les cartes** de tous les thèmes (pas seulement anime : manga, pays…) depuis
   l'admin, stockées en base plutôt que dans les fichiers JSON de `content/`. Toujours valider par
   le JSON Schema existant et garder le champ `rights`.
3. **Personnaliser les modes de jeu (presets)** depuis l'admin : budget, nombre de manches,
   taille d'équipe, minuteurs, règles actives et leurs paramètres (ex. `discard-fee`, `anti-snipe`),
   mode d'enchère et de verdict. À trancher : application à chaud (nouvelles rooms) ou au redémarrage
   comme la curation.
4. **Statistiques des joueurs** : parties jouées, victoires, scores, cartes préférées, par compte ;
   vue admin (liste des comptes, activité) et peut-être un profil joueur. Prérequis : lier le compte
   connecté au joueur en partie (aujourd'hui le compte ne fait que pré-remplir le pseudo) et
   enregistrer les résultats de partie en base.

Questions à poser avant de coder (et signaler toute nouvelle dépendance) : ordre de priorité,
qui voit les stats (admin seul ou chaque joueur), stats des parties sans compte.
