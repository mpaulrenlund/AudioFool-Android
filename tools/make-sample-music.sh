#!/bin/bash
# Makes the emulator's sample library: short tones in every format the tag reader
# must handle, with folder covers, embedded art, a hidden folder and an untagged
# file. Needs ffmpeg and node. Usage: tools/make-sample-music.sh <out-folder>
# Then: MSYS_NO_PATHCONV=1 adb push <out-folder>/Music /sdcard/
set -e
OUT="${1:?out folder}"; M="$OUT/Music"; HERE="$(cd "$(dirname "$0")" && pwd)"
rm -rf "$M"; mkdir -p "$M"; cd "$OUT"
q() { ffmpeg -hide_banner -loglevel error -y "$@"; }
tone() { q -f lavfi -i "sine=frequency=$1:duration=4:sample_rate=$2" -ac 2 "$3"; }
q -f lavfi -i "testsrc=size=600x600:rate=1" -frames:v 1 cover-a.jpg
q -f lavfi -i "mandelbrot=size=500x500:rate=1" -frames:v 1 cover-b.jpg

d="$M/Pink Floyd/The Dark Side of the Moon"; mkdir -p "$d"; cp cover-a.jpg "$d/cover.jpg"
for i in 1 2 3; do tone $((300+i*50)) 44100 t.wav; q -i t.wav -c:a flac -sample_fmt s16 -metadata title="Track $i of Dark Side" -metadata artist="Pink Floyd" -metadata album_artist="Pink Floyd" -metadata album="The Dark Side of the Moon" -metadata date=1973-03-01 -metadata track="$i/3" "$d/0$i.flac"; done
d="$M/Pink Floyd/Wish You Were Here"; mkdir -p "$d"
for i in 1 2; do q -f lavfi -i "sine=frequency=$((500+i*40)):duration=4:sample_rate=96000" -ac 2 t96.wav; q -i t96.wav -i cover-b.jpg -map 0 -map 1 -c:a flac -sample_fmt s32 -bits_per_raw_sample 24 -c:v copy -disposition:v attached_pic -metadata title="Shine $i" -metadata artist="Pink Floyd" -metadata album="Wish You Were Here" -metadata date=1975 -metadata track=$i "$d/0$i.flac"; done
d="$M/The Beatles/Abbey Road"; mkdir -p "$d"
for i in 1 2; do tone $((600+i*30)) 44100 t.wav; q -i t.wav -i cover-a.jpg -map 0 -map 1 -c:a libmp3lame -b:a 320k -c:v copy -id3v2_version 4 -disposition:v attached_pic -metadata title="Abbey $i" -metadata artist="The Beatles" -metadata album="Abbey Road" -metadata date=1969-09-26 -metadata track="$i/2" "$d/0$i.mp3"; done
d="$M/The Beatles/Revolver"; mkdir -p "$d"
tone 700 44100 t.wav; q -i t.wav -c:a libmp3lame -b:a 192k -id3v2_version 3 -metadata title="Taxman" -metadata artist="The Beatles" -metadata album="Revolver" -metadata date=1966 -metadata track=1 "$d/01.mp3"
d="$M/Björk/Homogenic"; mkdir -p "$d"
tone 800 44100 t.wav; q -i t.wav -i cover-b.jpg -map 0 -map 1 -c:a aac -b:a 256k -c:v copy -disposition:v attached_pic -metadata title="Jóga" -metadata artist="Björk" -metadata album="Homogenic" -metadata date=1997-09-22 -metadata track=1 "$d/01.m4a"
d="$M/Björk/Vespertine"; mkdir -p "$d"
q -f lavfi -i "sine=frequency=850:duration=4:sample_rate=48000" -ac 2 t48.wav; q -i t48.wav -c:a alac -sample_fmt s32p -metadata title="Hidden Place" -metadata artist="Björk" -metadata album="Vespertine" -metadata date=2001 -metadata track=1 "$d/01.m4a"
d="$M/Sigur Rós/Ágætis byrjun"; mkdir -p "$d"
tone 900 48000 t.wav; q -i t.wav -c:a libopus -b:a 160k -metadata title="Ný batterí" -metadata artist="Sigur Rós" -metadata album="Ágætis byrjun" -metadata date=1999 -metadata track=1 "$d/01.opus"
d="$M/Air/Moon Safari"; mkdir -p "$d"; cp cover-b.jpg "$d/Folder.JPG"
tone 950 44100 t.wav; q -i t.wav -c:a libvorbis -q:a 5 -metadata title="La femme d'argent" -metadata artist="Air" -metadata album="Moon Safari" -metadata date=1998 -metadata track=1 "$d/01.ogg"
d="$M/Beck/Odelay"; mkdir -p "$d"
tone 1000 44100 t.wav; q -i t.wav -c:a pcm_s16le -metadata title="Devils Haircut" -metadata artist="Beck" -metadata album="Odelay" -metadata date=1996 -metadata track=1 "$d/01.wav"
tone 1050 44100 t.wav; q -i t.wav -c:a pcm_s24be -write_id3v2 1 -metadata title="Where It's At" -metadata artist="Beck" -metadata album="Odelay" -metadata date=1996 -metadata track=2 "$d/02.aiff"
d="$M/Cartoon Theory/Ephemeral Dance"; mkdir -p "$d"
tone 1100 44100 t.wav; q -i t.wav -c:a wavpack -metadata title="Ephemeral" -metadata artist="Cartoon Theory" -metadata album="Ephemeral Dance" -metadata date=2026-10-02 -metadata track=1 "$d/01.wv"
d="$M/Cartoon Theory/Remixes + More"; mkdir -p "$d"
tone 1150 44100 t.wav; q -i t.wav -c:a flac -metadata title="Remix" -metadata artist="Cartoon Theory" -metadata album="Remixes + More" -metadata date=2026 -metadata track=1 "$d/01.flac"
d="$M/DSD"; mkdir -p "$d"
cp "$HERE/../core/src/test/resources/testdata/sample.dsf" "$d/sample.dsf"
q -f lavfi -i "anullsrc=r=44100:cl=stereo" -t 0.1 -c:a libmp3lame -id3v2_version 4 -metadata title="DFF Song" -metadata artist="DSD Artist" -metadata album="DFF Album" -metadata date=2020-05-01 -metadata track=1 id3src.mp3
node "$HERE/make-dff.js" id3src.mp3 "$d/DFF Song.dff"
# Gapless (spec 6.1): one continuous rising sweep cut sample-exactly into three
# songs of a "live album", at counts that fall mid-frame for every decoder.
d="$M/Gapless Live/Live at the Emulator"; mkdir -p "$d"; cp cover-a.jpg "$d/cover.jpg"
q -f lavfi -i "aevalsrc=0.4*sin(2*PI*(200*t+12*t*t)):s=44100:d=36" -ac 2 sweep.wav
cuts=(0 529207 1058431 1587600)
for i in 1 2 3; do q -i sweep.wav -af "atrim=start_sample=${cuts[$((i-1))]}:end_sample=${cuts[$i]}" -c:a flac -sample_fmt s16 -metadata title="Live Part $i" -metadata artist="Gapless Live" -metadata album="Live at the Emulator" -metadata date=2026 -metadata track="$i/3" "$d/0$i.flac"; done
d="$M/Untagged"; mkdir -p "$d"; tone 1200 44100 t.wav; q -i t.wav -c:a libmp3lame -map_metadata -1 -write_id3v2 0 "$d/mystery track.mp3"
mkdir -p "$M/.hidden"; cp "$d/mystery track.mp3" "$M/.hidden/skip.mp3"
rm -f t.wav t48.wav t96.wav sweep.wav id3src.mp3 cover-a.jpg cover-b.jpg
echo "Sample library in $M"
