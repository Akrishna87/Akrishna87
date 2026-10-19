#!/bin/bash
# Makes the videos the emulator test plays: calm animated landscapes (a sky, a sun crossing it, rolling
# hills) in place of ffmpeg's colour-bar test pattern, so the library, player and the screenshots on the
# download page look like a phone with real videos on it.
#   make-videos.sh <output dir>
set -euo pipefail
OUTDIR=${1:?usage: make-videos.sh <output dir>}
mkdir -p "$OUTDIR"

# scene W H SECONDS VARIANT OUT
scene() {
W=$1; H=$2; D=$3; V=$4; OUT=$5
case $V in
 1) C0=0x1b2a6b; C1=0xd9534f; C2=0xffc371; SUN=0xfff2c2; H1=0x2c3e50; H2=0x1a252f ;;
 2) C0=0x0f4c5c; C1=0x5fa8a0; C2=0xf6e7b4; SUN=0xffffff; H1=0x24564a; H2=0x153a30 ;;
 3) C0=0x3a1c71; C1=0xd76d77; C2=0xffaf7b; SUN=0xffe8d1; H1=0x4a2c5a; H2=0x2a1636 ;;
esac
ffmpeg -loglevel error -y \
 -f lavfi -i "gradients=size=${W}x${H}:rate=25:duration=$D:speed=0.01:c0=$C0:c1=$C1:c2=$C2:nb_colors=3:type=linear:x0=0:y0=0:x1=0:y1=$H" \
 -f lavfi -i "color=c=black:size=${W}x${H}:rate=25:duration=$D,format=gray,geq=lum='255*lt(hypot(X-(W*(0.15+0.7*T/$D)),Y-(H*0.45-H*0.12*sin(3.14159*T/$D))),H*0.09)'" \
 -f lavfi -i "color=c=$SUN:size=${W}x${H}:rate=25:duration=$D" \
 -f lavfi -i "color=c=$H1:size=${W}x${H}:rate=25:duration=$D,format=yuva420p,geq=lum='lum(X,Y)':cb='cb(X,Y)':cr='cr(X,Y)':a='255*gt(Y,H*0.68+H*0.05*sin(X/(W*0.09)+T*0.4))'" \
 -f lavfi -i "color=c=$H2:size=${W}x${H}:rate=25:duration=$D,format=yuva420p,geq=lum='lum(X,Y)':cb='cb(X,Y)':cr='cr(X,Y)':a='255*gt(Y,H*0.8+H*0.04*sin(X/(W*0.06)+1+T*0.7))'" \
 -f lavfi -i "sine=frequency=$((300+V*110)):duration=$D" \
 -filter_complex "[2:v][1:v]alphamerge[sun];[0:v][sun]overlay[a];[a][3:v]overlay[b];[b][4:v]overlay,format=yuv420p[v]" \
 -map "[v]" -map 5:a -c:v libx264 -preset veryfast -c:a aac -shortest "$OUT"
}

for i in 1 2 3; do scene 640 360 40 "$i" "$OUTDIR/Smoke Video $i.mp4"; done
# A tall phone video, which should play upright
scene 360 640 40 1 "$OUTDIR/Smoke Portrait.mp4"
ls -la "$OUTDIR"
