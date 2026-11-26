#!/usr/bin/env node
// Importe des thèmes « anime » depuis l'API publique AniList (https://graphql.anilist.co).
// Un thème par univers : on ne mélange jamais les licences dans une partie.
//
// Les images restent chez AniList : le thème ne contient que leurs URL, et les fichiers générés
// (content/themes/anime-*.json, content/presets/anime-*.json) sont ignorés par git. Profil « private » (§8) : usage entre amis,
// sur invitation, canal web uniquement.
//
// Usage : node tools/import-anilist.mjs [slug…]   (sans argument : tous les univers ci-dessous)

import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const UNIVERSES = [
  { slug: 'one-piece', search: 'One Piece', name: 'One Piece' },
  { slug: 'naruto', search: 'Naruto Shippuden', name: 'Naruto' },
  { slug: 'dragon-ball', search: 'Dragon Ball Z', name: 'Dragon Ball' },
  { slug: 'attack-on-titan', search: 'Shingeki no Kyojin', name: 'L’Attaque des Titans' },
  { slug: 'demon-slayer', search: 'Kimetsu no Yaiba', name: 'Demon Slayer' },
  { slug: 'jujutsu-kaisen', search: 'Jujutsu Kaisen', name: 'Jujutsu Kaisen' },
  { slug: 'my-hero-academia', search: 'Boku no Hero Academia', name: 'My Hero Academia' },
  { slug: 'hunter-x-hunter', search: 'Hunter x Hunter (2011)', name: 'Hunter × Hunter' },
];

const CARDS_PER_THEME = 40;
const PAGE_SIZE = 25;
const PAUSE_MS = 2500; // AniList limite le nombre de requêtes par minute
const CONTENT_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'content');
const OUT_DIR = path.join(CONTENT_DIR, 'themes');
const PRESET_DIR = path.join(CONTENT_DIR, 'presets');

const QUERY = `
query ($search: String, $page: Int, $perPage: Int) {
  Media(search: $search, type: ANIME, sort: POPULARITY_DESC) {
    id
    title { romaji english }
    coverImage { extraLarge color }
    bannerImage
    characters(sort: FAVOURITES_DESC, page: $page, perPage: $perPage) {
      edges {
        role
        node { id name { full } image { large } favourites gender }
      }
    }
  }
}`;

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function query(variables) {
  for (let attempt = 1; attempt <= 4; attempt++) {
    const res = await fetch('https://graphql.anilist.co', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ query: QUERY, variables }),
    });
    if (res.status === 429) {
      const wait = Number(res.headers.get('retry-after') ?? 60) * 1000;
      console.warn(`  limite atteinte, pause ${wait / 1000} s`);
      await sleep(wait);
      continue;
    }
    const json = await res.json();
    if (json.errors) {
      throw new Error(json.errors.map(e => e.message).join('; '));
    }
    return json.data.Media;
  }
  throw new Error('AniList indisponible');
}

function rarity(rank) {
  if (rank < 4) return 'legendary';
  if (rank < 12) return 'epic';
  if (rank < 24) return 'rare';
  return 'common';
}

async function importUniverse(u) {
  const edges = [];
  let media;
  for (let page = 1; edges.length < CARDS_PER_THEME; page++) {
    media = await query({ search: u.search, page, perPage: PAGE_SIZE });
    const got = media.characters.edges.filter(e => e.node.image?.large && !e.node.image.large.includes('default'));
    edges.push(...got);
    await sleep(PAUSE_MS);
    if (media.characters.edges.length < PAGE_SIZE) break;
  }
  const picked = edges.slice(0, CARDS_PER_THEME);
  const maxFav = Math.max(...picked.map(e => e.node.favourites), 2);
  const cards = picked.map((e, rank) => {
    const c = e.node;
    const fav = Math.max(c.favourites, 1);
    return {
      id: `c${c.id}`,
      name: c.name.full,
      tags: [e.role === 'MAIN' ? 'principal' : 'secondaire'],
      rarity: rarity(rank),
      stats: { popularity: Math.max(1, Math.round((100 * Math.log(fav)) / Math.log(maxFav))) },
      image: c.image.large,
      hints: [
        { level: 1, type: 'text', text: e.role === 'MAIN' ? 'Personnage principal' : 'Personnage secondaire' },
        { level: 2, type: 'initial', value: c.name.full.charAt(0).toUpperCase() },
      ],
    };
  });
  const theme = {
    id: `anime-${u.slug}`,
    name: u.name,
    version: '1.0.0',
    description: `${cards.length} personnages de ${media.title.english ?? media.title.romaji}, classés par popularité (AniList).`,
    rights: {
      profile: 'private',
      channels: ['web'],
      visibility: 'private',
      imagePolicy: 'any',
      disclaimer: 'Images et personnages © leurs ayants droit, via AniList. Usage privé entre amis, non affilié.',
      takedownContact: 'contact@blindle.me',
    },
    art: { cover: media.coverImage.extraLarge, banner: media.bannerImage ?? null, color: media.coverImage.color ?? null },
    defaultSlots: [1, 2, 3, 4, 5].map(i => ({ id: `slot-${i}`, label: `Carte ${i}` })),
    attributes: ['popularity'],
    cards,
  };
  const file = path.join(OUT_DIR, `${theme.id}.json`);
  await writeFile(file, JSON.stringify(theme, null, 2) + '\n', 'utf8');
  // Mode principal « Anime Blind Bid » : le piocheur voit la carte, les autres surenchérissent à l'aveugle.
  const preset = {
    id: theme.id,
    name: u.name,
    description: 'Anime Blind Bid : le piocheur seul voit le personnage et ouvre l’enchère, les autres surenchérissent ou laissent.',
    theme: theme.id,
    bidMode: 'drawer',
    verdictMode: 'vote',
    settings: { budget: 1000, rounds: cards.length, teamSize: 5, hintLevels: 1, bidTimerSec: 20 },
    rules: [{ ref: 'budget-cap' }, { ref: 'anti-snipe', params: { extendSeconds: 5 } }],
  };
  await writeFile(path.join(PRESET_DIR, `${preset.id}.json`), JSON.stringify(preset, null, 2) + '\n', 'utf8');
  console.log(`${theme.id} : ${cards.length} cartes (${media.title.romaji}) → ${path.relative(process.cwd(), file)} + preset`);
}

const wanted = process.argv.slice(2);
for (const u of UNIVERSES.filter(x => wanted.length === 0 || wanted.includes(x.slug))) {
  try {
    await importUniverse(u);
  } catch (e) {
    console.error(`${u.slug} : échec (${e.message})`);
  }
}
