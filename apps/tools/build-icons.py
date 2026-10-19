#!/usr/bin/env python3
"""Writes ../icons/<id>.svg: each app's launcher icon, drawn from the app's own icon files.

Android launcher icons are vector drawables (a background layer and a foreground layer). This reads
them from each app's repository (res/mipmap-anydpi-v26/ic_launcher.xml and the drawables it points
to) and converts them to SVG, cropped to the part a phone launcher shows. Re-run it after an app's
icon changes:  python3 tools/build-icons.py
"""
import sys, os, re, json, base64, math, tempfile, urllib.request
import xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'
AAPT='{http://schemas.android.com/aapt}'

class Ctx:
    def __init__(s, d): 
        s.d=d; s.colors={}; s.gid=0; s.defs=[]
        for f in os.listdir(d):
            if f.startswith('values__'):
                for e in ET.parse(f'{d}/{f}').getroot().iter('color'):
                    s.colors[e.get('name')]=e.text.strip()
    def color(s, v):
        if v is None: return None
        if v.startswith('@color/'): v=s.colors[v[7:]]
        if v.startswith('@android:color/'): v={'white':'#FFFFFF','black':'#000000','transparent':'#00000000'}[v.split('/')[1]]
        v=v.lstrip('#')
        if len(v)==3: v=''.join(c*2 for c in v)
        if len(v)==8: return '#'+v[2:], int(v[:2],16)/255
        return '#'+v, 1.0

def fmt(x): 
    return ('%.4f'%x).rstrip('0').rstrip('.')

def gradient(ctx, g, shape_box=None):
    ctx.gid+=1; gid=f'g{ctx.gid}'
    t=g.get(A+'type','linear')
    stops=[]
    items=g.findall('item')
    if items:
        for it in items: stops.append((float(it.get(A+'offset')), it.get(A+'color')))
    else:
        if g.get(A+'startColor'): stops.append((0,g.get(A+'startColor')))
        if g.get(A+'centerColor'): stops.append((0.5,g.get(A+'centerColor')))
        if g.get(A+'endColor'): stops.append((1,g.get(A+'endColor')))
    st=''.join('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>'%(fmt(o),ctx.color(c)[0],fmt(ctx.color(c)[1])) for o,c in stops)
    if g.get(A+'angle') is not None and g.get(A+'startX') is None:
        ang=math.radians(float(g.get(A+'angle'))); vx,vy=math.cos(ang),-math.sin(ang)
        x1,y1,x2,y2=54-vx*54,54-vy*54,54+vx*54,54+vy*54
        ctx.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{fmt(x1)}" y1="{fmt(y1)}" x2="{fmt(x2)}" y2="{fmt(y2)}">{st}</linearGradient>')
    elif t=='radial':
        ctx.defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{g.get(A+"centerX","0")}" cy="{g.get(A+"centerY","0")}" r="{g.get(A+"gradientRadius","0")}">{st}</radialGradient>')
    elif t=='sweep':
        raise SystemExit('sweep gradient unsupported')
    else:
        ctx.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A+"startX","0")}" y1="{g.get(A+"startY","0")}" x2="{g.get(A+"endX","0")}" y2="{g.get(A+"endY","0")}">{st}</linearGradient>')
    return f'url(#{gid})'

def paint(ctx, el, attr):
    v=el.get(A+attr)
    if v: return ctx.color(v)
    for at in el.findall(AAPT+'attr'):
        if at.get('name')=='android:'+attr:
            return gradient(ctx, at.find('gradient')), 1.0
    return None

def node(ctx, el):
    if el.tag=='group':
        tr=[]
        tx,ty=el.get(A+'translateX'),el.get(A+'translateY')
        if tx or ty: tr.append(f'translate({tx or 0} {ty or 0})')
        px,py=el.get(A+'pivotX','0'),el.get(A+'pivotY','0')
        r,sx,sy=el.get(A+'rotation'),el.get(A+'scaleX'),el.get(A+'scaleY')
        if r or sx or sy:
            tr.append(f'translate({px} {py})')
            if r: tr.append(f'rotate({r})')
            if sx or sy: tr.append(f'scale({sx or 1} {sy or 1})')
            tr.append(f'translate(-{px} -{py})')
        inner=''.join(node(ctx,c) for c in el)
        return f'<g transform="{" ".join(tr)}">{inner}</g>' if tr else f'<g>{inner}</g>'
    if el.tag=='path':
        a={'d':el.get(A+'pathData')}
        f=paint(ctx,el,'fillColor')
        if f:
            a['fill']=f[0] if isinstance(f[0],str) and f[0].startswith('url') else f[0]
            op=f[1]*float(el.get(A+'fillAlpha','1'))
            if op!=1: a['fill-opacity']=fmt(op)
        else: a['fill']='none'
        if el.get(A+'fillType')=='evenOdd': a['fill-rule']='evenodd'
        s=paint(ctx,el,'strokeColor')
        if s:
            a['stroke']=s[0]
            op=s[1]*float(el.get(A+'strokeAlpha','1'))
            if op!=1: a['stroke-opacity']=fmt(op)
            a['stroke-width']=el.get(A+'strokeWidth','0')
            if el.get(A+'strokeLineCap'): a['stroke-linecap']=el.get(A+'strokeLineCap')
            if el.get(A+'strokeLineJoin'): a['stroke-linejoin']=el.get(A+'strokeLineJoin')
            if el.get(A+'strokeMiterLimit'): a['stroke-miterlimit']=el.get(A+'strokeMiterLimit')
        return '<path '+' '.join(f'{k}="{v}"' for k,v in a.items())+'/>'
    raise SystemExit('unsupported element '+el.tag)

def layer(ctx, ref):
    ref=ref.split('/')[-1]; d=ctx.d
    if os.path.exists(f'{d}/drawable__{ref}.xml'):
        root=ET.parse(f'{d}/drawable__{ref}.xml').getroot()
        if root.tag=='vector':
            vw,vh=float(root.get(A+'viewportWidth')),float(root.get(A+'viewportHeight'))
            body=''.join(node(ctx,c) for c in root)
            al=root.get(A+'alpha')
            return f'<g transform="scale({fmt(108/vw)} {fmt(108/vh)})"'+(f' opacity="{al}"' if al else '')+f'>{body}</g>'
        if root.tag=='shape':
            g=root.find('gradient'); s=root.find('solid')
            fill=gradient(ctx,g) if g is not None else ctx.color(s.get(A+'color'))[0]
            return f'<rect width="108" height="108" fill="{fill}"/>'
    if os.path.exists(f'{d}/mipmap-xxxhdpi__{ref}.png') and os.path.getsize(f'{d}/mipmap-xxxhdpi__{ref}.png')>0:
        b=base64.b64encode(open(f'{d}/mipmap-xxxhdpi__{ref}.png','rb').read()).decode()
        return f'<image width="108" height="108" href="data:image/png;base64,{b}"/>'
    if ref in ctx.colors: return f'<rect width="108" height="108" fill="{ctx.color(ctx.colors[ref])[0]}"/>'
    raise SystemExit('cannot resolve '+ref+' in '+d)

def convert(d):
    ctx=Ctx(d)
    root=ET.parse(f'{d}/mipmap-anydpi-v26__ic_launcher.xml').getroot()
    bg=root.find('background').get(A+'drawable')
    fg=root.find('foreground').get(A+'drawable')
    b=layer(ctx,bg); f=layer(ctx,fg)
    # visible window of an adaptive icon is the central 72dp of 108dp
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="192" height="192">'
            f'<defs>{"".join(ctx.defs)}</defs>{b}{f}</svg>')


# Where each app's Android project lives when it is not at the root of its repository.
SUBDIR = {'myvideos': 'video-player-android/', 'pakkam': 'pakkam-android/', 'vellum': 'android/'}
FILES = ['mipmap-anydpi-v26/ic_launcher.xml', 'drawable/ic_launcher_background.xml', 'drawable/ic_launcher_foreground.xml',
         'mipmap-xxxhdpi/ic_launcher_foreground.png', 'values/colors.xml', 'values/ic_launcher_background.xml']

def fetch(app, dest):
    os.makedirs(dest, exist_ok=True)
    root = f"https://raw.githubusercontent.com/{OWNER}/{app['repo']}/HEAD/{SUBDIR.get(app.get('id'), '')}app/src/main/res/"
    for f in FILES:
        try:
            data = urllib.request.urlopen(urllib.request.Request(root + f, headers={'User-Agent': 'apps-icons'}), timeout=30).read()
        except Exception:
            continue                                  # not every app has every file
        open(os.path.join(dest, f.replace('/', '__')), 'wb').write(data)

HERE = os.path.dirname(os.path.abspath(__file__))
cfg = json.load(open(os.path.join(HERE, '..', 'apps.json')))
OWNER = cfg['owner']
out = os.path.join(HERE, '..', 'icons')
os.makedirs(out, exist_ok=True)
with tempfile.TemporaryDirectory() as tmp:
    for app in cfg['apps']:
        id = app.get('id') or app['repo']
        d = os.path.join(tmp, id)
        fetch(app, d)
        svg = convert(d)
        open(os.path.join(out, id + '.svg'), 'w').write(svg)
        print(id, len(svg))
