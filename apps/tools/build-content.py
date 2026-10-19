#!/usr/bin/env python3
"""Writes ../content.json: for each app, the "About this app" text and the "What's new" list.

The page reads this file first, so the description and changes appear straight away and still
show when GitHub's rate limit stops the live lookup. The page asks GitHub again and replaces them
whenever the live answer is newer. Run:  python3 tools/build-content.py   (GITHUB_TOKEN is optional)
The parsing rules mirror parseReadme() and loadChanges() in app.js.
"""
import json, os, re, sys, urllib.request, urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
cfg = json.load(open(os.path.join(HERE, '..', 'apps.json')))
OWNER = cfg['owner']


def get(url, accept=None):
    h = {'User-Agent': 'apps-content'}
    if os.environ.get('GITHUB_TOKEN') and 'api.github.com' in url:
        h['Authorization'] = 'Bearer ' + os.environ['GITHUB_TOKEN']
    with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=30) as r:
        return r.read().decode()


def api(path):
    return json.loads(get('https://api.github.com' + path))


def clip(t, n):
    return t if len(t) <= n else re.sub(r'\s+\S*$', '', t[:n]) + '…'


def sentence(t, n):
    m = re.match(r'^(.{25,}?[.!?])(\s|$)', t[:n])
    return m.group(1) if m else clip(t, n)


def plain(t):
    t = re.sub(r'!\[[^\]]*\]\([^)]*\)', '', t)
    t = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', t)
    t = re.sub(r'<[^>]+>', '', t)
    t = re.sub(r'[*_`]+', '', t)
    t = re.sub(r'^\[[ xX]\]\s*', '', t)
    return re.sub(r'\s+', ' ', t).strip()


def parse_readme(md):
    intro, features = [], []
    section, para, bullet = 'intro', [], None

    def flush():
        nonlocal para
        if not para:
            return
        t = plain(' '.join(para))
        para = []
        if t and not re.match(r'^(download|try it|live)\b', t, re.I) and not re.search(r'\.apk\b', t, re.I) and len(intro) < 2:
            intro.append(clip(t, 460))

    for raw in md.replace('\r', '').split('\n'):
        line = raw.strip()
        if re.match(r'^#\s', line):
            continue
        if re.match(r'^#{2,}\s', line):
            if section == 'intro':
                flush()
            head = re.sub(r'^#+\s*', '', line).lower()
            section = 'features' if not features and re.search(r'what it does|features|highlights|what.*(has|does|can)|status', head) else 'other'
            continue
        if section == 'intro':
            if not line:
                flush()
            elif not re.match(r'^([>|]|[-*]\s|```)', line):
                para.append(line)
        elif section == 'features':
            if re.match(r'^[-*]\s+\S', raw) and len(features) < 10:
                bullet = [re.sub(r'^[-*]\s+', '', raw)]
                features.append('')
            elif bullet is not None and line and re.match(r'^\s+\S', raw) and not re.match(r'^\s+[-*]\s', raw):
                bullet.append(line)
            elif not line:
                bullet = None
            if bullet is not None:
                features[-1] = sentence(plain(' '.join(bullet)), 150)
    flush()
    return intro, [f for f in features if len(f) > 3]


def release_for(app):
    has_apk = lambda r: not r.get('draft') and any(a['name'].lower().endswith('.apk') for a in r.get('assets', []))
    base = f"/repos/{OWNER}/{app['repo']}"
    if app.get('tagPrefix'):
        return next((r for r in api(base + '/releases?per_page=30') if r['tag_name'].startswith(app['tagPrefix']) and has_apk(r)), None)
    if app.get('tag'):
        return api(base + '/releases/tags/' + urllib.parse.quote(app['tag']))
    return next((r for r in api(base + '/releases?per_page=5') if has_apk(r)), None)


def commit_sha(release):
    if re.fullmatch(r'[0-9a-f]{40}', release.get('target_commitish') or ''):
        return release['target_commitish']
    m = re.search(r'Commit ([0-9a-f]{40})', release.get('body') or '')
    return m.group(1) if m else None


def changes(app, sha):
    q = 'per_page=40' + ('&sha=' + sha if sha else '') + ('&path=' + urllib.parse.quote(app['path']) if app.get('path') else '')
    items = []
    for c in api(f"/repos/{OWNER}/{app['repo']}/commits?{q}"):
        lines = c['commit']['message'].strip().split('\n')
        title = lines[0]
        if re.match(r'^Merge pull request', title, re.I):
            title = next((l.strip() for l in lines[1:] if l.strip()), '')
        elif re.match(r'^Merge\b', title, re.I):
            title = ''
        title = re.sub(r'\s*\(#\d+\)\s*$', '', title).strip()
        if not title or re.search(r'^(ci|tests?)\b|smoke test|emulator test|\bci:', title, re.I) or re.match(r'^move into its own repository', title, re.I):
            continue
        if any(i['title'] == title for i in items):
            continue
        items.append({'title': clip(title, 140), 'date': c['commit']['committer']['date']})
        if len(items) == 6:
            break
    return items


out = {}
for app in cfg['apps']:
    key = app.get('id') or app['repo']
    entry = {}
    try:
        text = get(f"https://raw.githubusercontent.com/{OWNER}/{app['repo']}/HEAD/{app.get('readme', 'README.md')}")[:14000]
        entry['about'], entry['features'] = parse_readme(text)
    except Exception as e:
        print(key, 'README:', e, file=sys.stderr)
    try:
        rel = release_for(app)
        sha = commit_sha(rel) if rel else None
        entry['sha'] = sha
        entry['changes'] = changes(app, sha)
    except Exception as e:
        print(key, 'changes:', e, file=sys.stderr)
    out[key] = entry
    print(key, len(entry.get('about', [])), len(entry.get('features', [])), len(entry.get('changes', [])))

with open(os.path.join(HERE, '..', 'content.json'), 'w') as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
    f.write('\n')
