"""Seeds two small test torrents for the smoke test, so it doesn't depend on the internet.

Usage: seed.py <work dir> <port> <peer host>

Makes the test downloads in <work dir>/content (a folder with three files and a single file),
a .torrent file for each, and a magnet link for each in <work dir>/<name>.magnet. The magnet
links carry no trackers; instead they name this seeder as a peer (x.pe=<peer host>:<port>),
which is how the app finds it. MD5 sums of the files go in <work dir>/md5sums.txt.
Then it seeds until it's killed.
"""
import hashlib
import os
import random
import sys
import time
import warnings

warnings.filterwarnings("ignore", category=DeprecationWarning)

import libtorrent as lt

work, port, peer_host = sys.argv[1], int(sys.argv[2]), sys.argv[3]
content = os.path.join(work, "content")
os.makedirs(os.path.join(content, "Smoke Album"), exist_ok=True)

rng = random.Random(42)  # same bytes every run


def make(path, size):
    with open(path, "wb") as f:
        f.write(rng.randbytes(size))


make(os.path.join(content, "Smoke Album", "Track 1.bin"), 4 * 1024 * 1024)
make(os.path.join(content, "Smoke Album", "Track 2.bin"), 16 * 1024 * 1024)
with open(os.path.join(content, "Smoke Album", "Notes.txt"), "w") as f:
    f.write("Seeded by the My Torrents smoke test.\n")
make(os.path.join(content, "Smoke Single.bin"), 24 * 1024 * 1024)

with open(os.path.join(work, "md5sums.txt"), "w") as out:
    for root, _, files in os.walk(content):
        for name in sorted(files):
            path = os.path.join(root, name)
            with open(path, "rb") as f:
                out.write(f"{hashlib.md5(f.read()).hexdigest()}  {os.path.relpath(path, content)}\n")

session = lt.session({
    "listen_interfaces": f"0.0.0.0:{port}",
    "enable_dht": False,
    "enable_lsd": False,
    "enable_upnp": False,
    "enable_natpmp": False,
    "allow_multiple_connections_per_ip": True,
    # TCP only: uTP (BitTorrent over UDP) through the emulator's network address translation
    # stalls now and then, which would make the test flaky without saying anything about the app.
    "enable_incoming_utp": False,
    "enable_outgoing_utp": False,
    "alert_mask": lt.alert_category.error | lt.alert_category.status | lt.alert_category.connect,
})

# Wait until it's listening, and use the port it actually got.
for _ in range(50):
    if session.is_listening():
        break
    time.sleep(0.1)
port = session.listen_port()

# Sent slowly on purpose, so the test can watch them download, pause one and skip a file.
# (The phone/emulator is a "local" peer, which libtorrent doesn't otherwise limit.)
local = session.get_peer_class(lt.session.local_peer_class_id)
local["upload_limit"] = 768 * 1024
session.set_peer_class(lt.session.local_peer_class_id, local)

for name in ["Smoke Album", "Smoke Single.bin"]:
    fs = lt.file_storage()
    lt.add_files(fs, os.path.join(content, name))
    ct = lt.create_torrent(fs, 256 * 1024)
    lt.set_piece_hashes(ct, content)
    data = lt.bencode(ct.generate())
    stem = name.replace(" ", "-").replace(".bin", "")
    with open(os.path.join(work, f"{stem}.torrent"), "wb") as f:
        f.write(data)
    info = lt.torrent_info(data)
    params = lt.add_torrent_params()
    params.ti = info
    params.save_path = content
    params.flags = lt.torrent_flags.seed_mode
    session.add_torrent(params)
    magnet = f"magnet:?xt=urn:btih:{info.info_hashes().v1}&dn={name.replace(' ', '+')}&x.pe={peer_host}:{port}"
    with open(os.path.join(work, f"{stem}.magnet"), "w") as f:
        f.write(magnet)
    print(magnet, flush=True)

print(f"Seeding on port {port}", flush=True)
ticks = 0
while True:
    for a in session.pop_alerts():
        print("seeder:", a.message(), flush=True)
    ticks += 1
    if ticks % 5 == 0:
        for h in session.get_torrents():
            st = h.status()
            if st.num_peers or st.upload_rate:
                print(f"seeder status: {st.name}: {st.num_peers} peers, up {st.upload_rate // 1024} KB/s, "
                      f"sent {st.total_payload_upload // 1024} KB", flush=True)
    time.sleep(1)
