(() => {
  'use strict';

  const API = 'https://api.github.com';
  const FRESH_MS = 10 * 60 * 1000;       // reuse a stored answer this long before asking GitHub again
  const NEW_MS = 3 * 24 * 3600 * 1000;   // a build this young is shown as "fresh"
  const MAX_SHOTS = 12;
  const MAX_OLDER = 6;

  // ---------- small helpers ----------

  const store = {
    get(key) { try { return JSON.parse(localStorage.getItem(key)); } catch { return null; } },
    set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage may be blocked */ } },
  };

  // Build DOM without innerHTML, so nothing from GitHub is ever parsed as markup.
  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === false || v == null) continue;
      if (k === 'class') el.className = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
  }

  // Only ever link to GitHub itself.
  function safeUrl(u) {
    try {
      const url = new URL(u);
      const ok = url.protocol === 'https:' &&
        (url.hostname === 'github.com' || url.hostname.endsWith('.githubusercontent.com'));
      return ok ? url.href : '#';
    } catch { return '#'; }
  }

  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  function ago(iso) {
    const secs = (new Date(iso).getTime() - Date.now()) / 1000;
    const steps = [['year', 31536000], ['month', 2592000], ['day', 86400], ['hour', 3600], ['minute', 60]];
    for (const [unit, size] of steps) {
      if (Math.abs(secs) >= size) return rtf.format(Math.round(secs / size), unit);
    }
    return 'just now';
  }
  const fullDate = iso => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
  const fmtSize = bytes => (bytes / 1048576).toFixed(1) + ' MB';
  const versionNum = name => parseInt((name.match(/v(\d+)/i) || [])[1], 10) || 0;
  const natural = (a, b) => a.localeCompare(b, undefined, { numeric: true });

  let toastTimer;
  function toast(msg) {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 4200);
  }

  // ---------- reading releases from GitHub ----------

  class RateLimited extends Error {}

  async function ghJson(path) {
    let res;
    try { res = await fetch(API + path, { headers: { Accept: 'application/vnd.github+json' } }); }
    catch { throw new Error('offline'); }
    if (res.status === 403 || res.status === 429) throw new RateLimited('GitHub is limiting requests right now');
    if (res.status === 404) throw new Error('No build has been published yet');
    if (!res.ok) throw new Error('GitHub answered ' + res.status);
    return res.json();
  }

  function pickApk(assets, preferred) {
    const apks = assets.filter(a => /\.apk$/i.test(a.name));
    if (!apks.length) return null;
    const exact = preferred && apks.find(a => a.name === preferred);
    if (exact) return exact;
    return [...apks].sort((a, b) => versionNum(b.name) - versionNum(a.name) || b.updated_at.localeCompare(a.updated_at))[0];
  }

  // Reduce a release to the few fields the page needs; this is also what gets stored.
  function summarise(app, release) {
    const assets = release.assets || [];
    const apk = pickApk(assets, app.apk);
    if (!apk) throw new Error('This release has no APK file yet');

    const buildFromName = (release.name || '').match(/build\s+(\d+)/i);
    const build = buildFromName ? +buildFromName[1] : (versionNum(apk.name) || null);

    const sha = /^[0-9a-f]{40}$/.test(release.target_commitish || '')
      ? release.target_commitish
      : ((release.body || '').match(/Commit ([0-9a-f]{40})/) || [])[1] || null;

    const seen = new Set([apk.digest || apk.name]);
    const older = assets
      .filter(a => /\.apk$/i.test(a.name) && a.id !== apk.id)
      .filter(a => { const key = a.digest || a.name; if (seen.has(key)) return false; seen.add(key); return true; })
      .sort((a, b) => versionNum(b.name) - versionNum(a.name) || b.updated_at.localeCompare(a.updated_at))
      .slice(0, MAX_OLDER)
      .map(a => ({ name: a.name, url: a.browser_download_url, size: a.size, updated: a.updated_at, build: versionNum(a.name) || null }));

    const shots = assets
      .filter(a => /^image\/(png|jpe?g)$/.test(a.content_type))
      .map(a => a.name).sort(natural).slice(0, MAX_SHOTS)
      .map(n => assets.find(a => a.name === n).browser_download_url);

    return {
      tag: release.tag_name,
      title: release.name,
      page: release.html_url,
      build, sha, older, shots,
      apk: {
        name: apk.name,
        url: apk.browser_download_url,
        size: apk.size,
        digest: (apk.digest || '').replace(/^sha256:/, '') || null,
        updated: apk.updated_at,      // moves with every new build, even when the release itself is reused
        downloads: apk.download_count,
      },
    };
  }

  async function loadApp(app, owner) {
    const key = 'apphub:r1:' + app.repo;
    const cached = store.get(key);
    if (cached && Date.now() - cached.t < FRESH_MS) return { info: cached.info, stale: false };
    try {
      const release = app.tag
        ? await ghJson(`/repos/${owner}/${app.repo}/releases/tags/${encodeURIComponent(app.tag)}`)
        : (await ghJson(`/repos/${owner}/${app.repo}/releases?per_page=5`)).find(r => !r.draft && (r.assets || []).some(a => /\.apk$/i.test(a.name)));
      if (!release) throw new Error('No build has been published yet');
      const info = summarise(app, release);
      store.set(key, { t: Date.now(), info });
      return { info, stale: false };
    } catch (err) {
      if (cached) return { info: cached.info, stale: true };   // better an older answer than none
      throw err;
    }
  }

  async function loadCommit(owner, app, sha) {
    const key = 'apphub:c1:' + sha;
    const cached = store.get(key);
    if (cached) return cached;
    const d = await ghJson(`/repos/${owner}/${app.repo}/commits/${sha}`);
    const msg = d.commit.message.trim();
    const [title, ...rest] = msg.split('\n');
    const out = { title, body: rest.join('\n').trim(), date: d.commit.author && d.commit.author.date, url: d.html_url };
    store.set(key, out);
    return out;
  }

  // ---------- what this browser has already downloaded ----------

  const installed = store.get('apphub:installed1') || {};
  const state = { owner: 'Akrishna87', apps: [], data: new Map() };

  function standing(app) {
    const entry = state.data.get(app.repo);
    if (!entry || !entry.info) return 'none';
    const mine = installed[app.repo];
    if (!mine) return 'new';
    return entry.info.apk.updated > mine ? 'update' : 'current';
  }

  const installLabel = { none: 'Install', new: 'Install', update: 'Update', current: 'Download again' };

  function installButton(app, info, extra = '') {
    const s = standing(app);
    return h('a', {
      class: 'btn primary ' + extra,
      href: safeUrl(info.apk.url),
      download: info.apk.name,
      rel: 'noopener',
      'aria-label': `${installLabel[s]} ${app.name}, ${fmtSize(info.apk.size)}`,
      onclick: () => {
        installed[app.repo] = info.apk.updated;
        store.set('apphub:installed1', installed);
        toast('Downloading ' + info.apk.name + '. When it finishes, open it to install.');
        setTimeout(() => { renderCard(app); updateStatus(); }, 400);
      },
    }, '⬇ ', installLabel[s]);
  }

  // ---------- the grid ----------

  const grid = document.getElementById('apps');
  const tile = (app, large) => h('div', { class: 'tile' + (large ? ' lg' : ''), style: `--h:${app.hue ?? 150}`, 'aria-hidden': 'true' }, app.emoji || '📱');
  const nameEl = app => [app.name, app.nameLocal ? h('span', { class: 'local', lang: 'ta' }, app.nameLocal) : null];

  function chipsFor(app, info, stale) {
    const s = standing(app);
    const young = Date.now() - new Date(info.apk.updated).getTime() < NEW_MS;
    return h('ul', { class: 'chips' },
      s === 'update' ? h('li', { class: 'chip update' }, 'Update available') : null,
      s === 'current' ? h('li', { class: 'chip fresh' }, '✓ You have the latest') : null,
      app.channel === 'debug' ? h('li', { class: 'chip debug' }, 'Test build') : null,
      info.build ? h('li', { class: 'chip' }, 'Build ' + info.build) : null,
      h('li', { class: 'chip' + (young && s !== 'update' ? ' fresh' : '') }, 'Updated ' + ago(info.apk.updated)),
      h('li', { class: 'chip' }, fmtSize(info.apk.size)),
      stale ? h('li', { class: 'chip debug', title: 'GitHub could not be reached, so this is the last answer saved on this device' }, 'Saved copy') : null,
    );
  }

  function skeleton() {
    return h('article', { class: 'card skeleton', 'aria-hidden': 'true' },
      h('div', { class: 'card-top' }, h('div', { class: 'tile' }), h('div', { style: 'flex:1;display:grid;gap:8px' }, h('div', { class: 'line w60' }), h('div', { class: 'line w90' }))),
      h('div', { class: 'line' }), h('div', { class: 'block' }));
  }

  function buildCard(app) {
    const entry = state.data.get(app.repo);
    const releasesUrl = `https://github.com/${state.owner}/${app.repo}/releases`;
    const head = h('div', { class: 'card-top' }, tile(app), h('div', {}, h('h2', {}, nameEl(app))));

    if (!entry) return h('article', { class: 'card', id: 'card-' + app.repo }, head, h('p', { class: 'tagline' }, app.tagline), skeleton().lastChild);
    if (entry.error) {
      return h('article', { class: 'card', id: 'card-' + app.repo }, head,
        h('p', { class: 'tagline' }, app.tagline),
        h('p', { class: 'problem' }, entry.error),
        h('div', { class: 'actions' }, h('a', { class: 'btn block', href: releasesUrl, rel: 'noopener' }, 'Open on GitHub')));
    }
    const { info, stale } = entry;
    return h('article', { class: 'card', id: 'card-' + app.repo }, head,
      h('p', { class: 'tagline' }, app.tagline),
      chipsFor(app, info, stale),
      h('div', { class: 'actions' },
        installButton(app, info),
        h('button', { class: 'btn', type: 'button', onclick: () => openSheet(app) }, 'Details')));
  }

  function renderCard(app) {
    const old = document.getElementById('card-' + app.repo);
    const fresh = buildCard(app);
    if (old) old.replaceWith(fresh); else grid.append(fresh);
    if (document.getElementById('sheet').open && currentApp === app) renderSheet(app);
  }

  function updateStatus() {
    const el = document.getElementById('status');
    const ready = state.apps.filter(a => state.data.get(a.repo)?.info);
    if (!ready.length) return;
    const updates = ready.filter(a => standing(a) === 'update');
    const newest = [...ready].sort((a, b) => state.data.get(b.repo).info.apk.updated.localeCompare(state.data.get(a.repo).info.apk.updated))[0];
    el.replaceChildren(
      `${ready.length} app${ready.length === 1 ? '' : 's'} · `,
      updates.length
        ? h('b', {}, `${updates.length} update${updates.length === 1 ? '' : 's'} waiting for you`)
        : `latest build: ${newest.name}, ${ago(state.data.get(newest.repo).info.apk.updated)}`);
  }

  // ---------- details sheet ----------

  const sheet = document.getElementById('sheet');
  const sheetBody = document.getElementById('sheet-body');
  let currentApp = null;
  sheetBody.tabIndex = -1;
  sheetBody.setAttribute('autofocus', '');

  function openSheet(app) {
    currentApp = app;
    renderSheet(app);
    if (!sheet.open) sheet.showModal();
    const url = new URL(location.href);
    url.searchParams.set('app', app.repo);
    history.replaceState(null, '', url);
  }

  sheet.addEventListener('close', () => {
    currentApp = null;
    const url = new URL(location.href);
    url.searchParams.delete('app');
    history.replaceState(null, '', url);
  });
  document.getElementById('sheet-close').addEventListener('click', () => sheet.close());
  sheet.addEventListener('click', e => { if (e.target === sheet) sheet.close(); });   // click on the backdrop

  function section(title, ...kids) { return h('section', { class: 'sheet-section' }, h('h3', {}, title), ...kids); }

  function renderSheet(app) {
    const entry = state.data.get(app.repo);
    if (!entry || !entry.info) return;
    const { info } = entry;
    const repoUrl = `https://github.com/${state.owner}/${app.repo}`;
    const deepLink = new URL(location.href);
    deepLink.search = '?app=' + encodeURIComponent(app.repo);
    deepLink.hash = '';

    const scroll = sheetBody.scrollTop;
    const commitBox = h('p', { class: 'change' }, 'Loading…');

    const qrBox = h('div', { class: 'qr' });
    try {
      const qr = qrcode(0, 'M');
      qr.addData(deepLink.href);
      qr.make();
      const holder = h('div', { 'aria-hidden': 'false' });
      holder.innerHTML = qr.createSvgTag({ cellSize: 4, scalable: true, title: 'QR code for the ' + app.name + ' page' });  // markup made by the QR library from a URL we built
      qrBox.append(holder.firstChild, h('p', {}, 'Scan this with your phone camera to open ', h('strong', {}, app.name), ' here, then tap Install.'));
    } catch { qrBox.append(h('p', {}, 'Link: ' + deepLink.href)); }

    const hash = info.apk.digest && h('dd', {}, h('div', { class: 'hash' },
      h('code', {}, info.apk.digest),
      h('button', { class: 'copy', type: 'button', onclick: async () => {
        try { await navigator.clipboard.writeText(info.apk.digest); toast('Fingerprint copied'); } catch { toast('Select the text to copy it'); }
      } }, 'Copy')));

    const parts = [
      h('div', { class: 'sheet-head' }, tile(app, true), h('div', {},
        h('h2', { id: 'sheet-title' }, nameEl(app)),
        h('p', { class: 'tagline' }, app.tagline))),
      chipsFor(app, info, entry.stale),
      app.note ? h('p', { class: 'note' }, app.note) : null,
      h('div', { class: 'actions', style: 'margin-top:16px' }, installButton(app, info, 'block')),

      section('This build',
        h('dl', { class: 'facts' },
          info.build ? [h('dt', {}, 'Build'), h('dd', {}, '#' + info.build)] : null,
          h('dt', {}, 'Updated'), h('dd', {}, fullDate(info.apk.updated) + ' (' + ago(info.apk.updated) + ')'),
          h('dt', {}, 'Size'), h('dd', {}, fmtSize(info.apk.size)),
          h('dt', {}, 'File'), h('dd', {}, info.apk.name),
          hash ? [h('dt', {}, 'SHA-256'), hash] : null,
          h('dt', {}, 'Downloads'), h('dd', {}, String(info.apk.downloads)))),

      info.sha ? section('Latest change', commitBox) : null,
      section('Install on another phone', qrBox),

      info.shots.length ? section('Screenshots',
        h('div', { class: 'shots', tabindex: '0', role: 'group', 'aria-label': 'Screenshots of ' + app.name },
          info.shots.map(src => h('img', { src: safeUrl(src), alt: '', loading: 'lazy', decoding: 'async' }))),
        h('p', { class: 'shots-note' }, 'Taken by the automatic test that runs on every build.')) : null,

      info.older.length ? section('Other downloads',
        h('ul', { class: 'older' }, info.older.map(o =>
          h('li', {}, h('a', { href: safeUrl(o.url), rel: 'noopener' },
            h('span', {}, o.build ? 'Build ' + o.build : o.name), h('small', {}, ago(o.updated) + ' · ' + fmtSize(o.size))))))) : null,

      section('Links', h('div', { class: 'links' },
        h('a', { href: repoUrl, rel: 'noopener' }, 'Source code'),
        h('a', { href: safeUrl(info.page), rel: 'noopener' }, 'Release page'),
        h('a', { href: repoUrl + '/issues', rel: 'noopener' }, 'Report a problem'))),
    ];
    sheetBody.replaceChildren(...parts.filter(Boolean));
    sheetBody.scrollTop = scroll;

    if (info.sha) {
      loadCommit(state.owner, app, info.sha).then(c => {
        commitBox.replaceChildren(
          h('b', {}, c.title),
          c.body ? h('span', {}, c.body.length > 600 ? c.body.slice(0, 600) + '…' : c.body) : null);
      }).catch(() => { commitBox.replaceChildren(h('a', { href: `${repoUrl}/commit/${info.sha}`, rel: 'noopener' }, 'See what changed on GitHub')); });
    }
  }

  // ---------- start ----------

  async function main() {
    const isAndroid = /Android/i.test(navigator.userAgent);
    document.getElementById('desktop-notice').hidden = isAndroid;

    let config;
    try { config = await (await fetch('apps.json', { cache: 'no-cache' })).json(); }
    catch {
      grid.setAttribute('aria-busy', 'false');
      document.getElementById('status').textContent = 'Could not load the app list. Please refresh.';
      return;
    }
    state.owner = config.owner;
    state.apps = config.apps;
    const cards = [];
    let lastGroup = null;
    for (const app of state.apps) {
      if (app.group && app.group !== lastGroup) cards.push(h('h2', { class: 'group' }, app.group));
      lastGroup = app.group;
      cards.push(buildCard(app));
    }
    grid.replaceChildren(...cards);

    await Promise.all(state.apps.map(async app => {
      try { state.data.set(app.repo, await loadApp(app, state.owner)); }
      catch (err) { state.data.set(app.repo, { error: err instanceof RateLimited ? 'GitHub is limiting requests right now. Try again in a few minutes.' : err.message === 'offline' ? 'No connection to GitHub right now.' : err.message }); }
      renderCard(app);
      updateStatus();
    }));
    grid.setAttribute('aria-busy', 'false');

    const failed = state.apps.filter(a => state.data.get(a.repo)?.error);
    const notice = document.getElementById('error-notice');
    if (failed.length) {
      notice.hidden = false;
      notice.replaceChildren(failed.length === state.apps.length ? 'Could not load any builds. ' : 'Some builds could not be loaded. ', h('a', { href: '', onclick: e => { e.preventDefault(); location.reload(); } }, 'Try again'), ' in a minute.');
    }
    if (!state.apps.some(a => state.data.get(a.repo)?.info)) document.getElementById('status').textContent = 'Could not reach GitHub.';

    const wanted = new URLSearchParams(location.search).get('app');
    const target = wanted && state.apps.find(a => a.repo === wanted);
    if (target && state.data.get(target.repo)?.info) openSheet(target);
  }

  main();
})();
