"""Packs Natural Earth outlines into the compact files the wind map reads (app/src/main/assets).

Download ne_50m_land.geojson and ne_50m_admin_0_boundary_lines_land.geojson from
https://github.com/nvkelso/natural-earth-vector/tree/master/geojson into the current directory, then
run `python3 pack_map.py` and copy land.bin and borders.bin into app/src/main/assets/.

Format (read by WorldOutline.kt): for each line, a big-endian u16 point count, then that many
(i16 lon, i16 lat) pairs in hundredths of a degree. Points closer than `tol` hundredths of a degree
to the previous one are dropped to keep the files small.
"""
import json, struct, sys
def lines(geo):
    for f in geo["features"]:
        g = f["geometry"]; t = g["type"]; c = g["coordinates"]
        if t == "Polygon": yield from c
        elif t == "MultiPolygon":
            for p in c: yield from p
        elif t == "LineString": yield c
        elif t == "MultiLineString": yield from c
def pack(src, dst, tol):
    out = bytearray(); n = pts = 0
    for line in lines(json.load(open(src))):
        q = []
        for lon, lat in line:
            p = (round(lon * 100), round(lat * 100))
            if q and abs(p[0]-q[-1][0]) < tol and abs(p[1]-q[-1][1]) < tol: continue
            q.append(p)
        if line and q[-1] != (round(line[-1][0]*100), round(line[-1][1]*100)):
            q.append((round(line[-1][0]*100), round(line[-1][1]*100)))
        if len(q) < 2: continue
        for i in range(0, len(q), 65000):
            chunk = q[i:i+65001]
            out += struct.pack(">H", len(chunk))
            for x, y in chunk: out += struct.pack(">hh", x, y)
            n += 1; pts += len(chunk)
    open(dst, "wb").write(out); print(dst, n, "lines", pts, "points", len(out), "bytes")
pack("ne_50m_land.geojson", "land.bin", 6)
pack("ne_50m_admin_0_boundary_lines_land.geojson", "borders.bin", 8)
