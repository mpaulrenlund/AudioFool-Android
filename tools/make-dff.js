// Writes a minimal DSDIFF (DFF) file: DSD64 stereo, one second, with an ID3v2 tag chunk.
const fs = require('fs');
const [src, out] = process.argv.slice(2);
const mp3 = fs.readFileSync(src);
const id3len = 10 + ((mp3[6] & 0x7f) << 21 | (mp3[7] & 0x7f) << 14 | (mp3[8] & 0x7f) << 7 | (mp3[9] & 0x7f));
const id3 = mp3.subarray(0, id3len);
const be64 = n => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(n)); return b; };
const chunk = (id, body) => { const pad = body.length % 2 ? Buffer.alloc(1) : Buffer.alloc(0); return Buffer.concat([Buffer.from(id, 'ascii'), be64(body.length), body, pad]); };
const u32 = n => { const b = Buffer.alloc(4); b.writeUInt32BE(n); return b; };
const u16 = n => { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; };
const fver = chunk('FVER', u32(0x01050000));
const fs_ = chunk('FS  ', u32(2822400));
const chnl = chunk('CHNL', Buffer.concat([u16(2), Buffer.from('SLFTSRGT', 'ascii')]));
const cmpr = chunk('CMPR', Buffer.concat([Buffer.from('DSD ', 'ascii'), Buffer.from([14]), Buffer.from('not compressed'), Buffer.alloc(1)]));
const prop = chunk('PROP', Buffer.concat([Buffer.from('SND ', 'ascii'), fs_, chnl, cmpr]));
const dsd = chunk('DSD ', Buffer.alloc(2822400 / 8 * 2, 0x69));
const tag = chunk('ID3 ', id3);
const body = Buffer.concat([Buffer.from('DSD ', 'ascii'), fver, prop, dsd, tag]);
fs.writeFileSync(out, Buffer.concat([Buffer.from('FRM8', 'ascii'), be64(body.length), body]));
