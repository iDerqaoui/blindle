#!/usr/bin/env node
// Importe les thèmes « anime » depuis Tenrai (https://api.tenrai.org/v1), API publique des données
// MyAnimeList (successeur de Jikan). Un thème par univers : on ne mélange jamais les licences.
//
// Rareté = « populaire OU puissant » :
//   - popularité : nombre de favoris MyAnimeList, échelle log ramenée à 0–100 dans l'univers ;
//   - puissance : rang S/A/B/C/D tiré de content/curation/anime-<univers>.json (commité, à relire) ;
//   - score = max(popularité, puissance) + 0.25 × min(popularité, puissance) ;
//   - les cartes sont triées par score et la rareté suit des quotas fixes (10 % légendaires,
//     20 % épiques, 30 % rares, 40 % communes) : tous les univers ont le même équilibre.
// Seuls les personnages de la liste de curation deviennent des cartes (noms d'affichage compris).
//
// Les images restent chez MyAnimeList : le thème ne contient que leurs URL, et les fichiers générés
// (content/themes/anime-*.json, content/presets/anime-*.json) sont ignorés par git. Profil « private » (§8) :
// usage entre amis, sur invitation, canal web uniquement.
//
// Depuis le 2026-11-27, la curation vit en base (page admin #admin) et le serveur régénère ces
// thèmes à chaque démarrage (module curation). Ce script reste un secours hors base : il ne lit que
// les fichiers content/curation/*.json, qui ne servent plus qu’à initialiser la base.
//
// Usage : node tools/import-anime.mjs [slug…]   (sans argument : tous les univers ci-dessous)

import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const API = 'https://api.tenrai.org/v1';

/** Univers : saisons MyAnimeList fusionnées (les personnages récents n'existent que dans les suites). */
const UNIVERSES = [
  { slug: 'one-piece', name: 'One Piece', mal: [21], color: '#d9412b' },
  { slug: 'naruto', name: 'Naruto', mal: [20, 1735], color: '#f28c28' },
  { slug: 'dragon-ball', name: 'Dragon Ball', mal: [813, 223, 30694], color: '#f5a623' },
  { slug: 'attack-on-titan', name: 'Attack on Titan', mal: [16498, 25777, 35760, 40028], color: '#7a5c3e' },
  { slug: 'demon-slayer', name: 'Demon Slayer', mal: [38000, 47778, 51019, 55701], color: '#2e8b57' },
  { slug: 'jujutsu-kaisen', name: 'Jujutsu Kaisen', mal: [40748, 51009], color: '#4b3fa8' },
  { slug: 'my-hero-academia', name: 'My Hero Academia', mal: [31964, 33486, 36456, 38408, 41587, 49918], color: '#2a9d8f' },
  { slug: 'hunter-x-hunter', name: 'Hunter × Hunter', mal: [11061], color: '#3a7d44' },
];

const CARDS_PER_THEME = 40;
const POWER = { S: 100, A: 85, B: 70, C: 50, D: 25 };
/** Quotas cumulés par rang : 10 % légendaires, 20 % épiques, 30 % rares, le reste communes. */
const QUOTAS = [['legendary', 0.1], ['epic', 0.3], ['rare', 0.6], ['common', 1]];
const PAUSE_MS = 800;

const CONTENT_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'content');
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function get(url) {
  for (let attempt = 1; attempt <= 4; attempt++) {
    const res = await fetch(url, { headers: { Accept: 'application/json' } });
    if (res.status === 429 || res.status >= 500) {
      const wait = Number(res.headers.get('retry-after') ?? 10 * attempt) * 1000;
      console.warn(`  ${res.status}, nouvel essai dans ${wait / 1000} s`);
      await sleep(wait);
      continue;
    }
    if (!res.ok) {
      throw new Error(`${res.status} sur ${url}`);
    }
    await sleep(PAUSE_MS);
    return (await res.json()).data;
  }
  throw new Error('Tenrai indisponible');
}

export function popularityScores(favourites) {
  const max = Math.log(Math.max(...favourites, 2));
  return favourites.map(f => Math.max(1, Math.round((100 * Math.log(Math.max(f, 1))) / max)));
}

export function rarityOf(rank, total) {
  return QUOTAS.find(([, share]) => rank < Math.round(share * total))?.[0] ?? 'common';
}

async function importUniverse(u) {
  const curation = JSON.parse(await readFile(path.join(CONTENT_DIR, 'curation', `anime-${u.slug}.json`), 'utf8'));
  const found = new Map();
  for (const id of u.mal) {
    for (const e of await get(`${API}/anime/${id}/characters`)) {
      const c = e.character;
      if (!found.has(c.mal_id)) {
        found.set(c.mal_id, { favourites: e.favorites ?? 0, role: e.role, image: c.images?.jpg?.image_url });
      } else if (e.role === 'Main') {
        found.get(c.mal_id).role = 'Main';
      }
    }
  }
  const anime = await get(`${API}/anime/${u.mal[0]}`);

  const pool = [];
  for (const [id, name, tier] of curation.characters) {
    const c = found.get(id);
    if (!c || !c.image || c.image.includes('questionmark')) {
      console.warn(`  ${u.slug} : ${name} (${id}) introuvable ou sans image, ignoré`);
      continue;
    }
    pool.push({ id, name, tier, ...c });
  }
  const pops = popularityScores(pool.map(c => c.favourites));
  pool.forEach((c, i) => {
    c.popularity = pops[i];
    c.power = POWER[c.tier];
    c.score = Math.max(c.popularity, c.power) + 0.25 * Math.min(c.popularity, c.power);
  });
  pool.sort((a, b) => b.score - a.score || b.favourites - a.favourites);
  const picked = pool.slice(0, CARDS_PER_THEME);

  const cards = picked.map((c, rank) => ({
    id: `c${c.id}`,
    name: c.name,
    tags: [c.role === 'Main' ? 'principal' : 'secondaire', `tier-${c.tier.toLowerCase()}`],
    rarity: rarityOf(rank, picked.length),
    stats: { popularity: c.popularity, power: c.power },
    image: c.image,
    hints: [
      { level: 1, type: 'text', text: c.role === 'Main' ? 'Main character' : 'Supporting character' },
      { level: 2, type: 'initial', value: c.name.charAt(0).toUpperCase() },
    ],
  }));
  const cover = anime.images?.webp?.large_image_url ?? anime.images?.jpg?.large_image_url ?? null;
  const theme = {
    id: `anime-${u.slug}`,
    name: u.name,
    version: '2.0.0',
    description: `${cards.length} characters from ${anime.title_english ?? anime.title}, ranked by popularity (MyAnimeList) and power.`,
    rights: {
      profile: 'private',
      channels: ['web'],
      visibility: 'private',
      imagePolicy: 'any',
      disclaimer: 'Characters and artwork © their respective owners, via MyAnimeList (Tenrai API). Private play among friends, not affiliated.',
      takedownContact: 'contact@blindle.me',
    },
    art: { cover, banner: null, color: u.color },
    defaultSlots: [1, 2, 3, 4, 5].map(i => ({ id: `slot-${i}`, label: `Carte ${i}` })),
    attributes: ['popularity', 'power'],
    cards,
  };
  await writeFile(path.join(CONTENT_DIR, 'themes', `${theme.id}.json`), JSON.stringify(theme, null, 2) + '\n', 'utf8');
  // Mode principal « Anime Blind Bid » : le piocheur voit la carte, les autres surenchérissent à l'aveugle.
  // Verdict aux points (PointsVerdictMode) ; le piocheur vend la carte (seller-collects) et paie pour la jeter (discard-fee).
  const preset = {
    id: theme.id,
    name: u.name,
    description: 'Anime Blind Bid: the drawer secretly sees the character and sells it; the others outbid blind. Score: rarity points − empty slots (unspent gold is worth nothing).',
    theme: theme.id,
    bidMode: 'drawer',
    verdictMode: 'points',
    settings: { budget: 1000, rounds: cards.length, teamSize: 5, hintLevels: 1, bidTimerSec: 20 },
    rules: [
      { ref: 'budget-cap' },
      { ref: 'anti-snipe', params: { extendSeconds: 5 } },
      { ref: 'seller-collects' },
      { ref: 'discard-fee', params: { amount: 50 } },
    ],
  };
  await writeFile(path.join(CONTENT_DIR, 'presets', `${preset.id}.json`), JSON.stringify(preset, null, 2) + '\n', 'utf8');
  const count = r => cards.filter(c => c.rarity === r).length;
  console.log(`${theme.id} : ${cards.length} cartes (L ${count('legendary')}, E ${count('epic')}, R ${count('rare')}, C ${count('common')})`);
  console.log('  légendaires : ' + cards.filter(c => c.rarity === 'legendary').map(c => c.name).join(', '));
}

const wanted = process.argv.slice(2);
let failed = 0;
for (const u of UNIVERSES.filter(x => wanted.length === 0 || wanted.includes(x.slug))) {
  try {
    await importUniverse(u);
  } catch (e) {
    failed++;
    console.error(`${u.slug} : échec (${e.message})`);
  }
}
process.exitCode = failed ? 1 : 0;
