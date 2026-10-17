"""Packs Natural Earth data into the compact files the wind map reads (app/src/main/assets).

Download these from https://github.com/nvkelso/natural-earth-vector/tree/master/geojson into one
directory, then run `python3 pack_map.py <that directory> <output directory>`:

    ne_50m_land.geojson                      coastlines and the land fill
    ne_10m_admin_0_countries_ind.geojson     country borders and names, India's official view
    ne_10m_admin_1_states_provinces.geojson  India's states and union territories
    ne_10m_populated_places_simple.geojson   cities

Outputs land.bin, borders.bin, states.bin and labels.tsv.

The .bin files (read by WorldOutline.kt) hold lines: for each, a big-endian u16 point count, then
that many (i16 lon, i16 lat) pairs in hundredths of a degree, simplified with Douglas-Peucker.

labels.tsv has one place per line: kind, the zoom it first shows at, lat, lon, name. Kind is C
(country), S (state or union territory), K (capital city) or P (other city). The zoom is the map
scale in dp per degree of longitude; a phone showing the whole world is about 1, India about 12.
"""
import json
import os
import struct
import sys


def rdp(points, eps):
    """Douglas-Peucker simplification (iterative, so long coastlines don't hit the recursion limit)."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = points[a]
        bx, by = points[b]
        dx, dy = bx - ax, by - ay
        norm = (dx * dx + dy * dy) ** 0.5
        best, idx = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm if norm else ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            if d > best:
                best, idx = d, i
        if best > eps and idx > 0:
            keep[idx] = True
            stack.append((a, idx))
            stack.append((idx, b))
    return [p for p, k in zip(points, keep) if k]


def rings(geometry):
    t, c = geometry["type"], geometry["coordinates"]
    if t == "Polygon":
        yield from c
    elif t == "MultiPolygon":
        for poly in c:
            yield from poly
    elif t == "LineString":
        yield c
    elif t == "MultiLineString":
        yield from c


def pack(lines, path, eps):
    out = bytearray()
    n = pts = 0
    for line in lines:
        q = rdp([(lon, lat) for lon, lat, *_ in line], eps)
        q = [(round(lon * 100), round(lat * 100)) for lon, lat in q]
        q = [p for i, p in enumerate(q) if i == 0 or p != q[i - 1]]
        if len(q) < 2:
            continue
        for start in range(0, len(q), 65000):
            chunk = q[start:start + 65001]
            out += struct.pack(">H", len(chunk))
            for x, y in chunk:
                out += struct.pack(">hh", x, y)
            n += 1
            pts += len(chunk)
    with open(path, "wb") as f:
        f.write(out)
    print(f"{os.path.basename(path)}: {n} lines, {pts} points, {len(out)} bytes")


def zoom_for(web_zoom, factor=1.0):
    """Natural Earth gives the web-map zoom where a label belongs; turn it into dp per degree."""
    return round(0.36 * 2 ** web_zoom * factor, 2)


def main(src, dst):
    load = lambda name: json.load(open(os.path.join(src, name), encoding="utf-8"))["features"]

    land = load("ne_50m_land.geojson")
    pack((r for f in land for r in rings(f["geometry"])), os.path.join(dst, "land.bin"), 0.03)

    countries = load("ne_10m_admin_0_countries_ind.geojson")
    pack((r for f in countries for r in rings(f["geometry"])), os.path.join(dst, "borders.bin"), 0.04)

    states = [f for f in load("ne_10m_admin_1_states_provinces.geojson") if f["properties"]["adm0_a3"] == "IND"]
    pack((r for f in states for r in rings(f["geometry"])), os.path.join(dst, "states.bin"), 0.015)

    labels = []
    for f in countries:
        p = f["properties"]
        name = p.get("NAME_EN") or p["NAME"]
        if p.get("LABEL_X") is None:
            continue
        labels.append(("C", zoom_for(p.get("MIN_LABEL") or 5), p["LABEL_Y"], p["LABEL_X"], name))
    for f in states:
        p = f["properties"]
        # The small union territories (Delhi, Chandigarh, ...) only once zoomed in further.
        labels.append(("S", zoom_for(p["min_label"], 3 if p["labelrank"] >= 9 else 1), p["latitude"], p["longitude"], p["name"]))
    for f in load("ne_10m_populated_places_simple.geojson"):
        p = f["properties"]
        india = p["adm0_a3"] == "IND"
        if not india and p["min_zoom"] > 5:
            continue  # elsewhere, only the bigger cities
        kind = "K" if p["adm0cap"] else "P"
        # India's cities show up a zoom level earlier than elsewhere.
        labels.append((kind, zoom_for(p["min_zoom"], 0.5 if india else 1), p["latitude"], p["longitude"], p["name"]))

    labels.sort(key=lambda l: (l[1], "CKSP".index(l[0])))
    with open(os.path.join(dst, "labels.tsv"), "w", encoding="utf-8") as f:
        for kind, zoom, lat, lon, name in labels:
            f.write(f"{kind}\t{zoom}\t{lat:.3f}\t{lon:.3f}\t{name.strip()}\n")
    print(f"labels.tsv: {len(labels)} places ({sum(l[0] == 'C' for l in labels)} countries, "
          f"{sum(l[0] == 'S' for l in labels)} Indian states, {sum(l[0] in 'KP' for l in labels)} cities)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
