// Generates placeholder JCI branding raster assets (logo + PWA icons) as
// real, valid PNG files using only Node's built-in zlib - no image/canvas
// dependency. These are deliberately simple monogram placeholders, NOT the
// real JCI emblem (no reliably fetchable, rights-cleared source for that was
// available in this environment) - swap the files this script writes with
// the official artwork before shipping.
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const FOREST_GREEN = [11, 77, 44, 255];
const GOLDEN_JUTE = [212, 163, 115, 255];
const WHITE = [255, 255, 255, 255];

// 5x7 bitmap font, just the glyphs this script needs.
const FONT = {
  J: ['00111', '00010', '00010', '00010', '00010', '10010', '01100'],
  C: ['01111', '10000', '10000', '10000', '10000', '10000', '01111'],
  I: ['11111', '00100', '00100', '00100', '00100', '00100', '11111'],
};

function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function encodePng(width, height, pixels) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdrData = Buffer.alloc(13);
  ihdrData.writeUInt32BE(width, 0);
  ihdrData.writeUInt32BE(height, 4);
  ihdrData[8] = 8; // bit depth
  ihdrData[9] = 6; // color type RGBA
  ihdrData[10] = 0;
  ihdrData[11] = 0;
  ihdrData[12] = 0;

  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    const rowStart = y * (width * 4 + 1);
    raw[rowStart] = 0; // no filter
    for (let x = 0; x < width; x++) {
      const [r, g, b, a] = pixels(x, y);
      const o = rowStart + 1 + x * 4;
      raw[o] = r; raw[o + 1] = g; raw[o + 2] = b; raw[o + 3] = a;
    }
  }
  const idatData = deflateSync(raw);

  return Buffer.concat([
    signature,
    chunk('IHDR', ihdrData),
    chunk('IDAT', idatData),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function drawGlyph(px, py, glyph, scale, color, set) {
  const rows = FONT[glyph];
  if (!rows) return;
  for (let ry = 0; ry < rows.length; ry++) {
    for (let rx = 0; rx < rows[ry].length; rx++) {
      if (rows[ry][rx] === '1') {
        for (let sy = 0; sy < scale; sy++) {
          for (let sx = 0; sx < scale; sx++) {
            set(px + rx * scale + sx, py + ry * scale + sy, color);
          }
        }
      }
    }
  }
}

function makeMonogram(size) {
  const bitmap = new Map();
  const set = (x, y, color) => bitmap.set(`${x},${y}`, color);

  const cx = size / 2;
  const cy = size / 2;
  const radius = size * 0.48;

  const glyphs = ['J', 'C', 'I'];
  const scale = Math.max(1, Math.floor(size / 26));
  const glyphWidth = 5 * scale;
  const gap = scale * 2;
  const totalWidth = glyphs.length * glyphWidth + (glyphs.length - 1) * gap;
  let startX = Math.floor(cx - totalWidth / 2);
  const startY = Math.floor(cy - (7 * scale) / 2);

  for (const glyph of glyphs) {
    drawGlyph(startX, startY, glyph, scale, WHITE, set);
    startX += glyphWidth + gap;
  }

  const ringWidth = Math.max(2, Math.floor(size * 0.035));

  return function pixels(x, y) {
    const dx = x - cx;
    const dy = y - cy;
    const dist = Math.sqrt(dx * dx + dy * dy);
    if (dist > radius) return [0, 0, 0, 0];
    if (dist > radius - ringWidth) return GOLDEN_JUTE;
    const glyphColor = bitmap.get(`${x},${y}`);
    if (glyphColor) return glyphColor;
    return FOREST_GREEN;
  };
}

function writePng(path, size) {
  mkdirSync(dirname(path), { recursive: true });
  const png = encodePng(size, size, makeMonogram(size));
  writeFileSync(path, png);
  console.log(`wrote ${path} (${size}x${size}, ${png.length} bytes)`);
}

writePng('public/assets/jci-logo.png', 512);
writePng('public/icons/icon-192.png', 192);
writePng('public/icons/icon-512.png', 512);
writePng('public/icons/apple-touch-icon.png', 180);
writePng('public/favicon.png', 64);
