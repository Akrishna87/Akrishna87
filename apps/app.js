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
  function compactAgo(iso) {
    const m = (Date.now() - new Date(iso).getTime()) / 60000;
    if (m < 60) return Math.max(1, Math.round(m)) + 'm';
    const hrs = m / 60;
    if (hrs < 48) return Math.round(hrs) + 'h';
    const d = hrs / 24;
    return d < 60 ? Math.round(d) + 'd' : Math.round(d / 30) + 'mo';
  }
  const shortDate = iso => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
  const fullDate = iso => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
  const fmtSize = bytes => (bytes / 1048576).toFixed(1) + ' MB';
  const versionNum = name => parseInt((name.match(/v(\d+)/i) || [])[1], 10) || 0;
  const appId = app => app.id || app.repo;
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

    const ver = ((release.name || '') + ' ' + (release.tag_name || '')).match(/\d+(?:\.\d+){1,3}/);
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
      .filter(a => !(app.hideShots || []).some(w => a.name.toLowerCase().includes(w)))     // test screens that don't show the app itself
      .map(a => a.name).sort(natural).slice(0, MAX_SHOTS)
      .map(n => assets.find(a => a.name === n).browser_download_url);

    return {
      tag: release.tag_name,
      title: release.name,
      page: release.html_url,
      build, version: !build && ver ? ver[0] : null, sha, older, shots,
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
    const key = 'apphub:r3:' + appId(app);
    const cached = store.get(key);
    if (cached && Date.now() - cached.t < FRESH_MS) return { info: cached.info, stale: false };
    try {
      const hasApk = r => !r.draft && (r.assets || []).some(a => /\.apk$/i.test(a.name));
      const release = app.tagPrefix
        ? (await ghJson(`/repos/${owner}/${app.repo}/releases?per_page=30`)).find(r => (r.tag_name || '').startsWith(app.tagPrefix) && hasApk(r))
        : app.tag
          ? await ghJson(`/repos/${owner}/${app.repo}/releases/tags/${encodeURIComponent(app.tag)}`)
          : (await ghJson(`/repos/${owner}/${app.repo}/releases?per_page=5`)).find(hasApk);
      if (!release) throw new Error('No build has been published yet');
      const info = summarise(app, release);
      store.set(key, { t: Date.now(), info });
      return { info, stale: false };
    } catch (err) {
      if (cached) return { info: cached.info, stale: true };   // better an older answer than none
      throw err;
    }
  }

  // Turn the start of a README into plain text for "About this app": the opening paragraphs, and the
  // bullet list under a heading like "What it does" or "Features".
  const plain = t => t
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '').replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, '').replace(/[*_`]+/g, '').replace(/^\[[ xX]\]\s*/, '').replace(/\s+/g, ' ').trim();
  // Up to the end of the first sentence if that fits, otherwise cut at a word.
  const sentence = (t, n) => { const m = t.slice(0, n).match(/^(.{25,}?[.!?])(\s|$)/); return m ? m[1] : clip(t, n); };
  const clip = (t, n) => t.length <= n ? t : t.slice(0, n).replace(/\s+\S*$/, '') + '…';

  function parseReadme(md) {
    const intro = [], features = [];
    let section = 'intro', para = [], bullet = null;
    const flush = () => {
      if (!para.length) return;
      const t = plain(para.join(' '));
      para = [];
      // Skip the install instructions that sit at the top of some READMEs.
      if (t && !/^(download|try it|live)\b/i.test(t) && !/\.apk\b/i.test(t) && intro.length < 2) intro.push(clip(t, 460));
    };
    for (const raw of md.replace(/\r/g, '').split('\n')) {
      const line = raw.trim();
      if (/^#\s/.test(line)) continue;                                    // the title
      if (/^#{2,}\s/.test(line)) {
        if (section === 'intro') flush();
        const head = line.replace(/^#+\s*/, '').toLowerCase();
        section = !features.length && /what it does|features|highlights|what.*(has|does|can)|status/.test(head) ? 'features' : 'other';
        continue;
      }
      if (section === 'intro') {
        if (!line) flush();
        else if (!/^([>|]|[-*]\s|```)/.test(line)) para.push(line);
      } else if (section === 'features') {
        if (/^[-*]\s+\S/.test(raw) && features.length < 10) { bullet = [raw.replace(/^[-*]\s+/, '')]; features.push(''); }
        else if (bullet && line && /^\s+\S/.test(raw) && !/^\s+[-*]\s/.test(raw)) bullet.push(line);          // a wrapped line of the same bullet
        else if (!line) bullet = null;
        if (bullet) features[features.length - 1] = sentence(plain(bullet.join(' ')), 150);
      }
    }
    flush();
    return { intro, features: features.filter(f => f.length > 3) };
  }

  async function loadReadme(app) {
    const key = 'apphub:m1:' + appId(app);
    const cached = store.get(key);
    if (cached && Date.now() - cached.t < 6 * 3600 * 1000) return parseReadme(cached.text);
    const path = app.readme || 'README.md';
    try {
      const res = await fetch(`https://raw.githubusercontent.com/${state.owner}/${app.repo}/HEAD/${path}`);
      if (!res.ok) throw new Error('no README');
      const text = (await res.text()).slice(0, 14000);
      store.set(key, { t: Date.now(), text });
      return parseReadme(text);
    } catch (err) {
      const snap = state.content[appId(app)];     // the copy saved in content.json
      if (snap && snap.about && snap.about.length) return { intro: snap.about, features: snap.features || [] };
      throw err;
    }
  }

  // "What's new": the plain-English titles of the latest changes in the code this build was made from.
  // Merge commits use the pull request's title; test and CI housekeeping is left out.
  async function loadChanges(app, info) {
    const key = 'apphub:w2:' + appId(app) + ':' + (info.sha || 'head');
    const cached = store.get(key);
    if (cached) return cached;
    const snap = state.content[appId(app)];
    if (snap && snap.changes && info.sha && snap.sha === info.sha) return snap.changes;    // same build: no need to ask GitHub
    try {
      return await liveChanges(app, info, key);
    } catch (err) {
      if (snap && snap.changes) return snap.changes;     // GitHub said no: show the saved list
      throw err;
    }
  }

  async function liveChanges(app, info, key) {
    const q = 'per_page=40' + (info.sha ? '&sha=' + info.sha : '') + (app.path ? '&path=' + encodeURIComponent(app.path) : '');
    const list = await ghJson(`/repos/${state.owner}/${app.repo}/commits?${q}`);
    const items = [];
    for (const c of list) {
      const lines = c.commit.message.trim().split('\n');
      let title = lines[0];
      if (/^Merge pull request/i.test(title)) title = (lines.slice(1).find(l => l.trim()) || '').trim();
      else if (/^Merge\b/i.test(title)) title = '';
      title = title.replace(/\s*\(#\d+\)\s*$/, '').trim();
      if (!title || /^(ci|tests?)\b|smoke test|emulator test|\bci:/i.test(title) || /^move into its own repository/i.test(title)) continue;
      if (items.some(i => i.title === title)) continue;
      items.push({ title: clip(title, 140), date: c.commit.committer.date, url: c.html_url });
      if (items.length === 6) break;
    }
    store.set(key, items);
    return items;
  }

  // ---------- what this browser has already downloaded ----------

  const installed = store.get('apphub:installed2') || {};
  const state = { owner: 'Akrishna87', apps: [], data: new Map(), content: {} };

  function standing(app) {
    const entry = state.data.get(appId(app));
    if (!entry || !entry.info) return 'none';
    const mine = installed[appId(app)];
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
        installed[appId(app)] = info.apk.updated;
        store.set('apphub:installed2', installed);
        toast('Downloading ' + info.apk.name + '. When it finishes, open it to install.');
        setTimeout(() => { renderCard(app); updateStatus(); }, 400);
      },
    }, '⬇ ', installLabel[s]);
  }

  // ---------- the grid ----------

  const grid = document.getElementById('apps');
  // The icon is the app's own launcher icon (icons/<id>.svg, made by tools/build-icons.py); the emoji shows only if it can't load.
  const tile = (app, large) => h('div', { class: 'tile' + (large ? ' lg' : ''), style: `--h:${app.hue ?? 150}`, 'aria-hidden': 'true' },
    app.emoji || '📱',
    h('img', { src: `icons/${encodeURIComponent(appId(app))}.svg`, alt: '', draggable: 'false',
      onload: e => e.currentTarget.parentNode.classList.add('has-icon'), onerror: e => e.currentTarget.remove() }));
  const nameEl = app => [app.name, app.nameLocal ? h('span', { class: 'local', lang: 'ta' }, app.nameLocal) : null];

  function chipsFor(app, info, stale) {
    const s = standing(app);
    const young = Date.now() - new Date(info.apk.updated).getTime() < NEW_MS;
    return h('ul', { class: 'chips' },
      s === 'update' ? h('li', { class: 'chip update' }, 'Update available') : null,
      s === 'current' ? h('li', { class: 'chip fresh' }, '✓ You have the latest') : null,
      app.channel === 'debug' ? h('li', { class: 'chip debug' }, 'Test build') : null,
      info.build ? h('li', { class: 'chip' }, 'Build ' + info.build) : (info.version ? h('li', { class: 'chip' }, 'v' + info.version) : null),
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
    const entry = state.data.get(appId(app));
    const releasesUrl = `https://github.com/${state.owner}/${app.repo}/releases`;
    const head = h('div', { class: 'card-top' }, tile(app), h('div', {}, h('h2', {}, nameEl(app))));

    if (!entry) return h('article', { class: 'card', id: 'card-' + appId(app) }, head, h('p', { class: 'tagline' }, app.tagline), skeleton().lastChild);
    if (entry.error) {
      return h('article', { class: 'card', id: 'card-' + appId(app) }, head,
        h('p', { class: 'tagline' }, app.tagline),
        h('p', { class: 'problem' }, entry.error),
        h('div', { class: 'actions' }, h('a', { class: 'btn block', href: releasesUrl, rel: 'noopener' }, 'Open on GitHub')));
    }
    const { info, stale } = entry;
    return h('article', { class: 'card', id: 'card-' + appId(app) }, head,
      h('p', { class: 'tagline' }, app.tagline),
      chipsFor(app, info, stale),
      h('div', { class: 'actions' },
        installButton(app, info),
        h('button', { class: 'btn', type: 'button', onclick: () => openSheet(app) }, 'Details')));
  }

  function renderCard(app) {
    const old = document.getElementById('card-' + appId(app));
    const fresh = buildCard(app);
    if (old) old.replaceWith(fresh); else grid.append(fresh);
    if (document.getElementById('sheet').open && currentApp === app) renderSheet(app);
  }

  function updateStatus() {
    const el = document.getElementById('status');
    const ready = state.apps.filter(a => state.data.get(appId(a))?.info);
    if (!ready.length) return;
    const updates = ready.filter(a => standing(a) === 'update');
    const newest = [...ready].sort((a, b) => state.data.get(appId(b)).info.apk.updated.localeCompare(state.data.get(appId(a)).info.apk.updated))[0];
    el.replaceChildren(
      `${ready.length} app${ready.length === 1 ? '' : 's'} · `,
      updates.length
        ? h('b', {}, `${updates.length} update${updates.length === 1 ? '' : 's'} waiting for you`)
        : `latest build: ${newest.name}, ${ago(state.data.get(appId(newest)).info.apk.updated)}`);
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
    url.searchParams.set('app', appId(app));
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
  const stat = (value, label) => h('div', { class: 'stat', role: 'listitem' }, h('b', {}, value), h('span', {}, label));
  const skeletonLines = n => h('div', { class: 'skel-lines', 'aria-hidden': 'true' }, Array.from({ length: n }, () => h('i')));

  function fillAbout(box, app, readme) {
    const intro = app.about ? [app.about] : (readme && readme.intro.length ? readme.intro : [app.tagline]);
    const feats = !app.about && readme ? readme.features : [];
    const body = h('div', { class: 'about-body' },
      intro.map(t => h('p', {}, t)),
      feats.length ? [h('p', { class: 'feat-title' }, 'Highlights'), h('ul', { class: 'feats' }, feats.map(f => h('li', {}, f)))] : null);
    const long = feats.length > 0 || intro.join(' ').length > 230;
    if (long) body.classList.add('clamped');
    box.replaceChildren(body, long ? h('button', {
      class: 'more', type: 'button', 'aria-expanded': 'false',
      onclick: e => {
        const open = body.classList.toggle('clamped') === false;
        e.currentTarget.textContent = open ? 'Show less' : 'Read more';
        e.currentTarget.setAttribute('aria-expanded', String(open));
      },
    }, 'Read more') : null);
  }

  function fillChanges(box, items, repoUrl) {
    if (items === null) {
      box.replaceChildren(h('a', { href: repoUrl + '/commits', rel: 'noopener' }, 'See the latest changes on GitHub'));
    } else if (!items.length) {
      box.replaceChildren(h('p', { class: 'plain' }, 'Bug fixes and improvements.'));
    } else {
      box.replaceChildren(h('ul', { class: 'changes' }, items.map(i =>
        h('li', {}, h('span', {}, i.title), h('small', {}, ago(i.date))))));
    }
  }

  function renderSheet(app) {
    const entry = state.data.get(appId(app));
    if (!entry || !entry.info) return;
    const { info } = entry;
    const repoUrl = `https://github.com/${state.owner}/${app.repo}`;
    const deepLink = new URL(location.href);
    deepLink.search = '?app=' + encodeURIComponent(appId(app));
    deepLink.hash = '';
    const scroll = sheetBody.scrollTop;
    const downloads = info.apk.downloads || 0;
    const s = standing(app);

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

    const aboutBox = h('div', { class: 'about' }, skeletonLines(3));
    const newBox = h('div', { class: 'whatsnew' }, skeletonLines(3));

    const parts = [
      h('div', { class: 'ps-head' }, tile(app, true), h('div', { class: 'ps-id' },
        h('h2', { id: 'sheet-title' }, nameEl(app)),
        h('p', { class: 'dev' }, 'Aravind'),
        h('p', { class: 'free' }, (app.channel === 'debug' ? 'Test build · ' : '') + 'Free · No ads'))),

      h('div', { class: 'stats', role: 'list', 'aria-label': 'About this build' },
        stat(fmtSize(info.apk.size), 'Size'),
        info.build ? stat('#' + info.build, 'Build') : stat(info.version || 'Latest', info.version ? 'Version' : 'Build'),
        stat(compactAgo(info.apk.updated), 'Updated'),
        stat(downloads > 0 ? downloads.toLocaleString() : 'New', downloads > 0 ? 'Downloads' : 'This build')),

      s === 'update' ? h('p', { class: 'update-note' }, '● A newer build than the one you downloaded is ready') : null,
      s === 'current' ? h('p', { class: 'update-note ok' }, '✓ You have the latest build') : null,
      app.note ? h('p', { class: 'note' }, app.note) : null,

      h('div', { class: 'actions ps-actions' },
        installButton(app, info, ''),
        h('button', { class: 'btn', type: 'button', onclick: async () => {
          const data = { title: app.name, text: app.tagline, url: deepLink.href };
          try {
            if (navigator.share) await navigator.share(data);
            else { await navigator.clipboard.writeText(deepLink.href); toast('Link copied'); }
          } catch (err) { if (!err || err.name !== 'AbortError') toast(deepLink.href); }
        } }, 'Share')),

      info.shots.length ? h('div', { class: 'shots', tabindex: '0', role: 'group', 'aria-label': 'Screenshots of ' + app.name },
        info.shots.map(src => h('img', { src: safeUrl(src), alt: '', loading: 'lazy', decoding: 'async' }))) : null,
      info.shots.length ? h('p', { class: 'shots-note' }, 'Screenshots come from the automatic test that runs on every build.') : null,

      section('About this app', aboutBox),

      section("What's new",
        h('p', { class: 'sub' }, (info.build ? 'Build ' + info.build + ' · ' : info.version ? 'Version ' + info.version + ' · ' : '') + 'Updated ' + shortDate(info.apk.updated)),
        newBox),

      section('App info', h('dl', { class: 'facts' },
        h('dt', {}, 'Version'), h('dd', {}, info.build ? 'Build ' + info.build : (info.version || info.title || info.tag)),
        h('dt', {}, 'Updated'), h('dd', {}, fullDate(info.apk.updated)),
        h('dt', {}, 'Size'), h('dd', {}, fmtSize(info.apk.size)),
        h('dt', {}, 'File'), h('dd', {}, info.apk.name),
        hash ? [h('dt', {}, 'SHA-256'), hash] : null,
        h('dt', {}, 'Offered by'), h('dd', {}, 'Aravind'))),

      section('Install on another phone', qrBox),

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

    loadReadme(app).catch(() => null).then(readme => fillAbout(aboutBox, app, readme));
    loadChanges(app, info).then(items => fillChanges(newBox, items, repoUrl)).catch(() => fillChanges(newBox, null, repoUrl));
  }

  // ---------- start ----------

  async function main() {
    const isAndroid = /Android/i.test(navigator.userAgent);
    document.getElementById('desktop-notice').hidden = isAndroid;

    fetch('content.json', { cache: 'no-cache' }).then(r => r.json()).then(c => { state.content = c || {}; }).catch(() => {});   // saved descriptions and changes
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
      try { state.data.set(appId(app), await loadApp(app, state.owner)); }
      catch (err) { state.data.set(appId(app), { error: err instanceof RateLimited ? 'GitHub is limiting requests right now. Try again in a few minutes.' : err.message === 'offline' ? 'No connection to GitHub right now.' : err.message }); }
      renderCard(app);
      updateStatus();
    }));
    grid.setAttribute('aria-busy', 'false');

    const failed = state.apps.filter(a => state.data.get(appId(a))?.error);
    const notice = document.getElementById('error-notice');
    if (failed.length) {
      notice.hidden = false;
      notice.replaceChildren(failed.length === state.apps.length ? 'Could not load any builds. ' : 'Some builds could not be loaded. ', h('a', { href: '', onclick: e => { e.preventDefault(); location.reload(); } }, 'Try again'), ' in a minute.');
    }
    if (!state.apps.some(a => state.data.get(appId(a))?.info)) document.getElementById('status').textContent = 'Could not reach GitHub.';

    const wanted = new URLSearchParams(location.search).get('app');
    const target = wanted && state.apps.find(a => appId(a) === wanted);
    if (target && state.data.get(appId(target))?.info) openSheet(target);
  }

  main();
})();
