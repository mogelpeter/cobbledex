'use strict';

let state = { players: [], allSpecies: [], speciesByDex: new Map(), dexBySpecies: new Map() };
let activeUuid  = null;
let activeView  = 'dex';
let sortBy      = 'dex';
let searchQuery = '';
let searchTimer = null;

// ─── Type colours ─────────────────────────────────────────────────────────────

const TYPE_COLORS = {
  normal:'#9b9b9b', fire:'#f05030', water:'#6890f0', grass:'#78c850',
  electric:'#f8d030', ice:'#98d8d8', fighting:'#c03028', poison:'#a040a0',
  ground:'#e0c068', flying:'#a890f0', psychic:'#f85888', bug:'#a8b820',
  rock:'#b8a038', ghost:'#705898', dragon:'#7038f8', dark:'#705848',
  steel:'#b8b8d0', fairy:'#ee99ac',
};
const TYPE_TEXT = {
  normal:'#000', grass:'#000', electric:'#000', ice:'#000',
  ground:'#000', bug:'#000', steel:'#000', fairy:'#000',
};
function typeColor(type) { return TYPE_COLORS[type?.toLowerCase()] || '#484f58'; }
function typeText(type)  { return TYPE_TEXT[type?.toLowerCase()] || '#fff'; }

const STAT_COLORS = { hp:'#f87171', atk:'#fb923c', def:'#fbbf24', spa:'#60a5fa', spd:'#34d399', spe:'#a78bfa' };
const STAT_LABELS = { hp:'HP', atk:'Atk', def:'Def', spa:'SpA', spd:'SpD', spe:'Spe' };

// ─── Type chart (Gen 9 defensive) ────────────────────────────────────────────
// DEFEND_CHART[defendingType][attackingType] = damage multiplier
const DEFEND_CHART = {
  normal:   { ghost: 0, fighting: 2 },
  fire:     { water: 2, rock: 2, ground: 2, fire: 0.5, grass: 0.5, ice: 0.5, bug: 0.5, steel: 0.5, fairy: 0.5 },
  water:    { electric: 2, grass: 2, fire: 0.5, water: 0.5, ice: 0.5, steel: 0.5 },
  electric: { ground: 2, electric: 0.5, flying: 0.5, steel: 0.5 },
  grass:    { fire: 2, ice: 2, poison: 2, flying: 2, bug: 2, water: 0.5, electric: 0.5, grass: 0.5, ground: 0.5 },
  ice:      { fire: 2, fighting: 2, rock: 2, steel: 2, ice: 0.5 },
  fighting: { flying: 2, psychic: 2, fairy: 2, bug: 0.5, rock: 0.5, dark: 0.5 },
  poison:   { ground: 2, psychic: 2, grass: 0.5, fighting: 0.5, poison: 0.5, bug: 0.5, fairy: 0.5 },
  ground:   { water: 2, grass: 2, ice: 2, electric: 0, poison: 0.5, rock: 0.5 },
  flying:   { electric: 2, ice: 2, rock: 2, ground: 0, grass: 0.5, fighting: 0.5, bug: 0.5 },
  psychic:  { bug: 2, ghost: 2, dark: 2, fighting: 0.5, psychic: 0.5 },
  bug:      { fire: 2, flying: 2, rock: 2, grass: 0.5, fighting: 0.5, ground: 0.5 },
  rock:     { water: 2, grass: 2, fighting: 2, ground: 2, steel: 2, normal: 0.5, fire: 0.5, poison: 0.5, flying: 0.5 },
  ghost:    { ghost: 2, dark: 2, normal: 0, fighting: 0, poison: 0.5, bug: 0.5 },
  dragon:   { ice: 2, dragon: 2, fairy: 2, fire: 0.5, water: 0.5, electric: 0.5, grass: 0.5 },
  dark:     { fighting: 2, bug: 2, fairy: 2, psychic: 0, ghost: 0.5, dark: 0.5 },
  steel:    { fire: 2, fighting: 2, ground: 2, poison: 0,
              normal: 0.5, grass: 0.5, ice: 0.5, flying: 0.5, psychic: 0.5,
              bug: 0.5, rock: 0.5, dragon: 0.5, steel: 0.5, fairy: 0.5,
              water: 0.5, electric: 0.5 },
  fairy:    { poison: 2, steel: 2, dragon: 0, fighting: 0.5, bug: 0.5, dark: 0.5 },
};

const ALL_ATTACKING_TYPES = [
  'normal','fire','water','electric','grass','ice','fighting','poison',
  'ground','flying','psychic','bug','rock','ghost','dragon','dark','steel','fairy'
];

function calcDefensiveScore(types) {
  let weak = 0, resist = 0, immune = 0;
  for (const atk of ALL_ATTACKING_TYPES) {
    let mult = 1;
    for (const def of types) {
      const row = DEFEND_CHART[def.toLowerCase()] ?? {};
      mult *= (row[atk] ?? 1);
    }
    if (mult === 0) immune++;
    else if (mult < 1) resist++;
    else if (mult > 1) weak++;
  }
  // raw range roughly -8 to +14 for dual types; map to 0-100
  const raw = resist + immune * 2 - weak;
  return Math.max(0, Math.min(100, (raw + 8) / 22 * 100));
}

function calcBestPokemon(allPokemon) {
  if (!allPokemon?.length) return null;
  let best = null, bestScore = -Infinity;
  for (const p of allPokemon) {
    const statSum = p.stats ? Object.values(p.stats).reduce((a, b) => a + b, 0) : 0;
    const statScore = Math.min(100, statSum / 720 * 100);

    const moveTypes = new Set(
      (p.moves ?? []).map(m => typeof m === 'string' ? null : m?.type?.toLowerCase()).filter(Boolean)
    );
    const coverageScore = Math.min(100, moveTypes.size / 4 * 100);

    const defScore = calcDefensiveScore(p.types ?? []);

    const score = statScore * 0.5 + coverageScore * 0.25 + defScore * 0.25;
    // tie-break: higher dex number wins (stable)
    if (score > bestScore || (score === bestScore && p.dexNumber > (best?.dexNumber ?? 0))) {
      bestScore = score;
      best = p;
    }
  }
  return best;
}

// ─── Bootstrap ────────────────────────────────────────────────────────────────

async function init() {
  try {
    const [dataRes, speciesRes] = await Promise.all([fetch('/api/data'), fetch('/api/species')]);
    const data = await dataRes.json();
    const speciesList = await speciesRes.json();
    state.players = data.players;
    state.allSpecies = speciesList;
    state.speciesByDex = new Map();
    state.dexBySpecies = new Map();
    speciesList.forEach(s => {
      state.speciesByDex.set(s.dexNumber, s);
      state.dexBySpecies.set(s.species, s.dexNumber);
    });
    if (state.players.length > 0) activeUuid = state.players[0].uuid;
    renderTabs();
    setView('dex');
  } catch (e) {
    const g = document.getElementById('dex-grid');
    if (g) g.innerHTML = '<p class="empty-msg">Could not connect to CobbleDex server.</p>';
  }
  connectSSE();
}

// ─── SSE ──────────────────────────────────────────────────────────────────────

let _sseSource  = null;
let _sseRetries = 0;
let _sseTimer   = null;
const SSE_MAX_RETRIES = 8;
const SSE_BASE_MS     = 1000;

function connectSSE() {
  clearTimeout(_sseTimer);
  if (_sseSource) { _sseSource.close(); _sseSource = null; }
  _sseSource = new EventSource('/events');

  _sseSource.onmessage = (e) => {
    _sseRetries = 0;
    const msg = JSON.parse(e.data);
    if (msg.type === 'playerUpdate') {
      const idx = state.players.findIndex(p => p.uuid === msg.player.uuid);
      if (idx >= 0) state.players[idx] = msg.player;
      else state.players.push(msg.player);
      renderTabs();
      if (activeUuid === null) activeUuid = msg.player.uuid;
      if (activeUuid === msg.player.uuid) renderCurrent();
    } else if (msg.type === 'playerLeft') {
      state.players = state.players.filter(p => p.uuid !== msg.uuid);
      if (activeUuid === msg.uuid)
        activeUuid = state.players.length > 0 ? state.players[0].uuid : null;
      renderTabs();
      renderCurrent();
    }
  };

  const badge = document.getElementById('live-badge');

  _sseSource.onopen = () => {
    _sseRetries = 0;
    if (badge) { badge.textContent = '● LIVE'; badge.className = 'live-badge'; }
  };

  _sseSource.onerror = () => {
    _sseSource.close();
    _sseSource = null;
    _sseRetries++;
    if (_sseRetries >= SSE_MAX_RETRIES) {
      if (badge) { badge.textContent = '● OFFLINE'; badge.className = 'live-badge offline'; }
      return;
    }
    if (badge) { badge.textContent = '● RECONNECTING…'; badge.className = 'live-badge offline'; }
    // _sseRetries is already 1 on the first failure; -1 keeps the first delay at SSE_BASE_MS
    const delayMs = Math.min(SSE_BASE_MS * Math.pow(2, _sseRetries - 1), 30_000);
    _sseTimer = setTimeout(connectSSE, delayMs);
  };
}

// Reconnect immediately when the tab regains visibility after being backgrounded.
document.addEventListener('visibilitychange', () => {
  if (!document.hidden &&
      (_sseSource === null || _sseSource.readyState === EventSource.CLOSED)) {
    _sseRetries = 0;
    connectSSE();
  }
});

// ─── Helpers ──────────────────────────────────────────────────────────────────

function toDisplayName(id) {
  return id.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function genderHtml(gender) {
  if (gender === 'MALE')   return '<span class="gender-m">♂</span>';
  if (gender === 'FEMALE') return '<span class="gender-f">♀</span>';
  return '<span class="gender-n">◯</span>';
}

function locationHtml(loc) {
  if (!loc) return '';
  if (loc === 'party') return '<span class="location-badge party-badge">◈ Party</span>';
  const boxName = esc(loc.replace(/^pc:/, ''));
  return `<span class="location-badge pc-badge">◫ ${boxName}</span>`;
}

function activePlayer() {
  return state.players.find(p => p.uuid === activeUuid) ?? null;
}
function renderCurrent() {
  if (activeView === 'dex') renderGrid(); else renderTeam();
}
function el(id) { return document.getElementById(id); }

// ─── View / sort / search controls ───────────────────────────────────────────

function setView(view) {
  activeView = view;
  const tabDex  = el('tab-dex');
  const tabTeam = el('tab-team');
  if (tabDex)  tabDex.className  = 'view-tab' + (view === 'dex'  ? ' active' : '');
  if (tabTeam) tabTeam.className = 'view-tab' + (view === 'team' ? ' active' : '');
  const isDex = view === 'dex';
  const grid     = el('dex-grid');
  const stats    = el('dex-stats');
  const sortCtrl = el('sort-controls');
  const search   = el('search-input');
  const teamView = el('team-view');
  if (grid)     grid.style.display     = isDex ? 'grid'  : 'none';
  if (stats)    stats.style.display    = isDex ? 'block' : 'none';
  if (sortCtrl) sortCtrl.style.display = isDex ? 'flex'  : 'none';
  if (search)   search.style.display   = isDex ? ''      : 'none';
  if (teamView) teamView.style.display = isDex ? 'none'  : 'block';
  renderCurrent();
}

// ─── Tabs ─────────────────────────────────────────────────────────────────────

function renderTabs() {
  const nav = el('player-tabs');
  if (!nav) return;
  nav.innerHTML = '';
  state.players.forEach(player => {
    const btn = document.createElement('button');
    btn.className = 'tab' + (player.uuid === activeUuid ? ' active' : '');
    btn.textContent = `${player.playerName} (${player.caughtSpecies.length})`;
    btn.addEventListener('click', () => { activeUuid = player.uuid; renderTabs(); renderCurrent(); });
    nav.appendChild(btn);
  });
}

// ─── Pokédex grid ─────────────────────────────────────────────────────────────

function renderGrid() {
  const player = activePlayer();
  const grid   = el('dex-grid');
  const stats  = el('dex-stats');
  if (!grid) return;

  if (!player) {
    grid.innerHTML = '<p class="empty-msg">No players online.</p>';
    if (stats) stats.textContent = '';
    return;
  }

  const caught = new Set(player.caughtSpecies);
  const seen   = new Set(player.seenSpecies);

  const shinySpecies = new Set();
  player.pokemon.forEach(p => { if (p.shiny) shinySpecies.add(p.species); });

  const total = state.allSpecies.length || 1025;
  const pct = ((caught.size / total) * 100).toFixed(1);
  if (stats) stats.textContent = `${caught.size} caught · ${seen.size} seen · ${pct}% complete`;

  let speciesList = state.allSpecies.length > 0
    ? state.allSpecies
    : buildFallbackList(caught, seen, player);

  // Search filter
  const q = searchQuery.trim().toLowerCase();
  if (q) {
    speciesList = speciesList.filter(s =>
      (s.displayName || s.species).toLowerCase().includes(q) ||
      String(s.dexNumber).startsWith(q)
    );
  }

  // Sort
  speciesList = [...speciesList].sort((a, b) => {
    if (sortBy === 'name') {
      return (a.displayName || a.species).localeCompare(b.displayName || b.species);
    }
    if (sortBy === 'status') {
      const rank = s => caught.has(s.species) ? 0 : seen.has(s.species) ? 1 : 2;
      return rank(a) - rank(b) || a.dexNumber - b.dexNumber;
    }
    return a.dexNumber - b.dexNumber;
  });

  grid.innerHTML = '';
  speciesList.forEach(({ dexNumber, species, displayName }) => {
    const isCaught = caught.has(species);
    const isSeen   = !isCaught && seen.has(species);
    const isShiny  = isCaught && shinySpecies.has(species);
    const status   = isCaught ? 'caught' : isSeen ? 'seen' : 'unseen';

    const spriteUrl = `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${isShiny ? 'shiny/' : ''}${dexNumber}.png`;

    const cell = document.createElement('div');
    cell.className = `dex-cell ${status}${isShiny ? ' shiny-cell' : ''}`;
    cell.innerHTML = `
      <img class="dex-sprite dex-sprite-${status}"
           src="${spriteUrl}" alt="${esc(displayName)}"
           onerror="this.style.visibility='hidden'">
      <span class="dex-num">#${dexNumber}${isShiny ? ' ✨' : ''}</span>
      <span class="dex-name">${esc(displayName)}</span>
    `;
    if (isCaught) cell.addEventListener('click', () => openModal(player, species));
    grid.appendChild(cell);
  });
}

function buildFallbackList(caught, seen, player) {
  const speciesMeta = new Map();
  player.pokemon.forEach(p => {
    if (!speciesMeta.has(p.species))
      speciesMeta.set(p.species, { dexNumber: p.dexNumber, displayName: p.displayName });
  });
  const list = [];
  caught.forEach(species => {
    const m = speciesMeta.get(species);
    if (m) list.push({ dexNumber: m.dexNumber, species, displayName: m.displayName });
  });
  seen.forEach(species => {
    if (!caught.has(species)) {
      const dex = state.dexBySpecies.get(species) ?? 9999;
      list.push({ dexNumber: dex, species, displayName: toDisplayName(species) });
    }
  });
  list.sort((a, b) => a.dexNumber - b.dexNumber);
  return list;
}

// ─── Card helpers ─────────────────────────────────────────────────────────────

function buildMovesHtml(moves) {
  if (!moves?.length) return '';
  return moves.map(m => {
    // m may be a string (old format) or {name, type} object
    const name = (typeof m === 'string') ? m : (m?.name ?? String(m));
    const type = (typeof m === 'string') ? null : (m?.type ?? null);
    const bg = typeColor(type);
    const fg = typeText(type);
    return `<span class="move" style="background:${bg};color:${fg}">${esc(name)}</span>`;
  }).join('');
}

function buildStatsHtml(stats, ivs) {
  if (!stats) return '';
  return `<div class="stat-bars">${
    Object.entries(stats).map(([key, val]) => {
      const pct  = Math.min(100, Math.round((val / 400) * 100));
      const col  = STAT_COLORS[key] || '#888';
      const iv   = ivs?.[key] ?? 0;
      const ivcl = iv >= 30 ? 'hi' : iv <= 5 ? 'lo' : '';
      return `
        <div class="stat-row">
          <span class="stat-lbl">${STAT_LABELS[key]}</span>
          <div class="stat-track"><div class="stat-fill" style="width:${pct}%;background:${col}"></div></div>
          <span class="stat-num">${val}</span>
          <span class="iv-tag ${ivcl}">${iv}</span>
        </div>`;
    }).join('')
  }</div>`;
}

function buildBallHtml(ball) {
  if (!ball) return '';
  const ballId = esc(ball.toLowerCase().replace(/ /g, '-'));
  return `<img class="ball-icon" src="https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/${ballId}.png"
               alt="${esc(ball)}" title="${esc(ball)}" onerror="this.style.display='none'">`;
}

function buildCardHtml(p, isBest = false) {
  const name = p.nickname
    ? `${esc(p.nickname)}${p.shiny ? ' ✨' : ''}`
    : `${p.shiny ? '✨ ' : ''}${esc(p.displayName)}`;
  const movesHtml  = buildMovesHtml(p.moves);
  const statsHtml  = buildStatsHtml(p.stats, p.ivs);
  const ballHtml   = buildBallHtml(p.ball);
  const shinyClass = p.shiny ? ' shiny-card' : '';
  const bestClass  = isBest ? ' best-pokemon' : '';
  const bestBadge  = isBest ? '<span class="best-badge">★ BEST</span>' : '';
  const ballLabel  = p.ball ? ` ${esc(p.ball)}` : '';
  return `
    <div class="pokemon-card${shinyClass}${bestClass}">
      ${bestBadge}
      <div class="card-head">
        <span class="poke-name">${name}</span>
        <span class="poke-level">Lv.${p.level}</span>
      </div>
      <div class="card-meta">${esc(p.nature)} · ${genderHtml(p.gender)}${ballHtml ? ` · ${ballHtml}${ballLabel}` : ''} · ${locationHtml(p.location)}</div>
      <div class="moves">${movesHtml}</div>
      ${statsHtml}
    </div>`;
}

function buildTeamCardHtml(p, isBest) {
  const name = p.nickname
    ? `${esc(p.nickname)}${p.shiny ? ' ✨' : ''}`
    : `${p.shiny ? '✨ ' : ''}${esc(p.displayName)}`;
  const typeBadges = (p.types ?? []).map(t => {
    const cls = TYPE_COLORS[t.toLowerCase()] ? t.toLowerCase() : 'normal';
    return `<span class="type-badge type-${cls}">${esc(t)}</span>`;
  }).join('');
  const movesHtml   = buildMovesHtml(p.moves);
  const statsHtml   = buildStatsHtml(p.stats, p.ivs);
  const ballHtml    = buildBallHtml(p.ball);
  const artUrl      = `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/${p.shiny ? 'shiny/' : ''}${p.dexNumber}.png`;
  const artFallback = `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${p.shiny ? 'shiny/' : ''}${p.dexNumber}.png`;
  const shinyClass  = p.shiny ? ' shiny-shimmer' : '';
  const bestClass   = isBest ? ' best-pokemon' : '';
  const bestBadge   = isBest ? '<span class="best-badge">★ BEST</span>' : '';
  const ballLabel   = p.ball ? ` ${esc(p.ball)}` : '';
  return `
    <div class="team-card${shinyClass}${bestClass}">
      ${bestBadge}
      <img class="team-sprite" src="${artUrl}" alt="${esc(p.displayName)}"
           onerror="this.src='${artFallback}'">
      <div class="team-card-info">
        <div class="card-head">
          <span class="poke-name">${name}</span>
          <span class="poke-level">Lv.${p.level}</span>
        </div>
        <div class="team-types">${typeBadges}</div>
        <div class="card-meta">${esc(p.nature)} · ${genderHtml(p.gender)}${ballHtml ? ` · ${ballHtml}${ballLabel}` : ''} · ${locationHtml(p.location)}</div>
        <div class="moves">${movesHtml}</div>
        ${statsHtml}
      </div>
    </div>`;
}

// ─── Team view ────────────────────────────────────────────────────────────────

function renderTeam() {
  const player  = activePlayer();
  const teamView = el('team-view');
  if (!teamView) return;
  if (!player) { teamView.innerHTML = '<p class="empty-msg">No players online.</p>'; return; }

  const caught = new Set(player.caughtSpecies);
  const seen   = new Set(player.seenSpecies);
  const total  = state.allSpecies.length || 1025;
  const pct    = ((caught.size / total) * 100).toFixed(1);

  const party = player.pokemon.filter(p => p.location === 'party');
  const avgLevel = party.length > 0
    ? Math.round(party.reduce((s, p) => s + p.level, 0) / party.length) : 0;

  const typeCounts = {};
  party.forEach(p => p.types.forEach(t => { typeCounts[t] = (typeCounts[t] || 0) + 1; }));
  const typeHtml = Object.entries(typeCounts)
    .sort((a, b) => b[1] - a[1])
    .map(([t]) => {
      const cls = TYPE_COLORS[t.toLowerCase()] ? t.toLowerCase() : 'normal';
      return `<span class="type-badge type-${cls}">${esc(t)}</span>`;
    })
    .join('');

  const best = calcBestPokemon(player.pokemon);
  const partyHtml = party.map(p => buildTeamCardHtml(p, p === best))
    .join('') || '<p class="empty-msg">No Pokémon in party.</p>';

  teamView.innerHTML = `
    <div class="player-stats">
      <div class="stat-item"><span class="stat-val caught-val">${caught.size}</span><span class="stat-label">Caught</span></div>
      <div class="stat-item"><span class="stat-val seen-val">${seen.size}</span><span class="stat-label">Seen</span></div>
      <div class="stat-item"><span class="stat-val">${caught.size + seen.size}</span><span class="stat-label">Encountered</span></div>
      <div class="stat-item"><span class="stat-val accent-val">${pct}%</span><span class="stat-label">Complete</span></div>
      ${party.length > 0 ? `<div class="stat-item"><span class="stat-val">${avgLevel}</span><span class="stat-label">Avg Level</span></div>` : ''}
    </div>
    ${typeHtml ? `<div class="party-types">${typeHtml}</div>` : ''}
    <div class="team-grid">${partyHtml}</div>`;
}

// ─── Modal ────────────────────────────────────────────────────────────────────

function openModal(player, species) {
  const entries = player.pokemon.filter(p => p.species === species);
  if (entries.length === 0) return;

  const first    = entries[0];
  const hasShiny = entries.some(p => p.shiny);
  const typeBadges = first.types
    .map(t => {
      const cls = TYPE_COLORS[t.toLowerCase()] ? t.toLowerCase() : 'normal';
      return `<span class="type-badge type-${cls}">${esc(t)}</span>`;
    })
    .join('');

  const artUrl      = `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/${hasShiny ? 'shiny/' : ''}${first.dexNumber}.png`;
  const artFallback = `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${hasShiny ? 'shiny/' : ''}${first.dexNumber}.png`;

  el('modal-title').innerHTML = `
    <img class="modal-artwork" src="${artUrl}" alt="${esc(first.displayName)}"
         onerror="this.src='${artFallback}'">
    <div class="modal-title-info">
      <div class="modal-title-name">
        <span>#${String(first.dexNumber).padStart(3,'0')} ${esc(first.displayName)}</span>
        ${typeBadges}
      </div>
      <span class="modal-owner">${esc(player.playerName)} has ${entries.length}</span>
    </div>`;

  const best = calcBestPokemon(player.pokemon);
  el('modal-cards').innerHTML = entries.map(p => buildCardHtml(p, p === best)).join('');
  el('modal').classList.remove('hidden');
}

function closeModal() {
  const m = el('modal');
  if (m) m.classList.add('hidden');
}

// ─── Event wiring ─────────────────────────────────────────────────────────────

el('modal-backdrop').addEventListener('click', closeModal);
el('modal-close').addEventListener('click', closeModal);
el('tab-dex').addEventListener('click',  () => setView('dex'));
el('tab-team').addEventListener('click', () => setView('team'));
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

el('sort-select').addEventListener('change', e => {
  sortBy = e.target.value;
  renderGrid();
});

el('search-input').addEventListener('input', e => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    searchQuery = e.target.value;
    renderGrid();
  }, 220);
});

// ─── Start ────────────────────────────────────────────────────────────────────
init();
