'use strict';
const $ = id => document.getElementById(id);
let state, selected = null, mode = null, recruitKind = null, busy = false, toastTimer;
const glyph = { BASE: '▣', SCOUT: '♟', TANK: '◆' };
const traits = { BASE: '120 health · Reinforcement point', SCOUT: 'Move 3 · Range 1 · Damage 18', TANK: 'Move 2 · Range 3 · Damage 25' };
function notify(message) {
  $('message').textContent = message; $('message').style.display = 'block';
  clearTimeout(toastTimer); toastTimer = setTimeout(() => $('message').style.display = 'none', 4500);
}
async function request(path, body) {
  if (busy) return;
  busy = true; if (state) render();
  try {
    const response = await fetch('/api/game' + path, {
      method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Command failed. Please try again.');
    state = data; mode = null; recruitKind = null;
    if (!state.units.some(u => u.id === selected)) selected = null;
    if (state.status !== 'PLAYING') notify({VICTORY:'Victory! You destroyed the red base.',DEFEAT:'Defeat. Start a new match and try again.',DRAW:'Draw. The round limit was reached.'}[state.status]);
  } catch (error) {
    notify(error.message);
    if (!state) $('phase').textContent = 'Cannot connect. Refresh to retry.';
  } finally { busy = false; if (state) render(); }
}
function activeActions() {
  if (mode === 'RECRUIT') return state.legalActions.filter(a => a.type === mode && a.recruitKind === recruitKind);
  return state.legalActions.filter(a => a.unitId === selected && a.type === mode);
}
function render() {
  const actions = activeActions();
  $('board').replaceChildren();
  for (let row = 0; row < state.size; row++) for (let col = 0; col < state.size; col++) {
    const u = state.units.find(u => u.row === row && u.col === col);
    const wall = state.walls.some(c => c.row === row && c.col === col);
    const relay = state.relays.some(c => c.row === row && c.col === col);
    const action = actions.find(a => a.row === row && a.col === col);
    const cell = document.createElement('button');
    cell.className = 'cell' + (u ? ' ' + u.team.toLowerCase() : '') + (wall ? ' wall' : '') + (relay ? ' relay' : '') + (u?.id === selected ? ' selected' : '') + (u?.acted ? ' acted' : '') + (action ? (mode === 'ATTACK' ? ' target' : ' legal') : '');
    const coord = String.fromCharCode(65 + col) + (row + 1);
    cell.setAttribute('aria-label', `${coord}: ${u ? `${u.team.toLowerCase()} ${u.kind.toLowerCase()}, ${u.health} health${u.acted ? ', already acted' : ''}` : wall ? 'wall' : relay ? 'energy relay' : 'empty'}${action ? ', available ' + mode.toLowerCase() : ''}`);
    cell.disabled = busy || state.status !== 'PLAYING';
    const label = document.createElement('span'); label.className = 'coord'; label.textContent = coord;
    const icon = document.createElement('span'); icon.className = 'symbol'; icon.textContent = u ? glyph[u.kind] : wall ? '▧' : relay ? '◇' : '';
    cell.append(label, icon);
    if (u) {
      const hp = document.createElement('span'); hp.className = 'hp';
      const bar = document.createElement('i'); bar.style.width = (u.health / u.maxHealth * 100) + '%'; hp.append(bar); cell.append(hp);
      if (u.shielded) { const shield = document.createElement('span'); shield.className = 'shield'; shield.textContent = '◉'; cell.append(shield); }
    }
    cell.onclick = () => {
      if (action) { request('/action', action); return; }
      if (u?.team === 'PLAYER') { selected = u.id; mode = null; recruitKind = null; render(); }
      else if (mode) notify('Choose a highlighted tile.');
    };
    $('board').append(cell);
  }
  for (const [team, prefix] of [['PLAYER','blue'],['COMPUTER','red']]) {
    const base = state.units.find(u => u.team === team && u.kind === 'BASE');
    $(prefix+'Health').textContent = `${base?.health || 0} / 120`; $(prefix+'Bar').value = base?.health || 0;
  }
  $('energy').textContent = state.energy; $('energyBar').value = state.energy;
  $('round').textContent = 'ROUND ' + String(state.round).padStart(2,'0');
  $('phase').textContent = busy ? 'Sending orders…' : state.status === 'PLAYING' ? 'Your turn / Blue command' : {VICTORY:'Mission complete / Victory',DEFEAT:'Base lost / Defeat',DRAW:'Round limit / Draw'}[state.status];
  const u = state.units.find(u => u.id === selected);
  $('selectedName').textContent = u ? 'Blue ' + u.kind.toLowerCase() : 'Select a blue unit';
  $('unitInfo').textContent = u ? `${traits[u.kind]} · ${u.health} HP${u.acted ? ' · Already acted' : ''}` : 'Click a unit on the board to see its orders.';
  document.querySelectorAll('[data-mode]').forEach(button => {
    button.disabled = busy || !state.legalActions.some(a => a.unitId === selected && a.type === button.dataset.mode);
    button.classList.toggle('active', mode === button.dataset.mode);
  });
  $('shield').disabled = busy || !state.legalActions.some(a => a.unitId === selected && a.type === 'SHIELD');
  document.querySelectorAll('[data-recruit]').forEach(button => {
    button.disabled = busy || !state.legalActions.some(a => a.type === 'RECRUIT' && a.recruitKind === button.dataset.recruit);
  });
  $('endTurn').disabled = busy || state.status !== 'PLAYING'; $('newGame').disabled = busy;
  $('hint').textContent = mode === 'RECRUIT' ? 'Click a highlighted tile next to your base.' : mode ? `Choose a highlighted ${mode === 'ATTACK' ? 'enemy' : 'tile'}.` : 'Choose your orders, then end the turn.';
  $('log').replaceChildren();
  state.log.forEach(message => { const item = document.createElement('li'); item.textContent = message; $('log').append(item); });
}
document.querySelectorAll('[data-mode]').forEach(button => button.onclick = () => { mode = button.dataset.mode; recruitKind = null; render(); });
document.querySelectorAll('[data-recruit]').forEach(button => button.onclick = () => { mode = 'RECRUIT'; recruitKind = button.dataset.recruit; render(); });
$('shield').onclick = () => { const action = state.legalActions.find(a => a.unitId === selected && a.type === 'SHIELD'); if (action) request('/action', action); };
$('endTurn').onclick = () => request('/action', {type:'END_TURN',unitId:null,row:0,col:0,recruitKind:null});
$('newGame').onclick = () => { if (state && !confirm('Start a new match? Your current battle will be reset.')) return; selected = null; request('/new', {}); };
request('');
