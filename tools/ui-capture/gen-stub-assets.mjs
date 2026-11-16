#!/usr/bin/env node
/**
 * Regenerates the assets that the prototype zip is missing, WITHOUT touching the pristine
 * prototype source: the generated files are git-ignored (see docs/ui-reference/prototype/.gitignore).
 *
 *   src/assets/logo.jpg          copy of src/assets/logo-raw.jpg (the brand master)
 *   src/assets/photos/*.jpg      the two real raws (anime, vintage) + procedural stand-ins
 *   src/assets/photos/*_lo.jpg   25% downscales, used as the "before" side of the sweep
 *
 * Requires ImageMagick (`magick` or `convert`) on PATH.
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const proto = path.resolve(here, '../../docs/ui-reference/prototype');
const assets = path.join(proto, 'src/assets');
const photos = path.join(assets, 'photos');

const magick = (() => {
  for (const bin of ['magick', 'convert']) {
    try {
      execFileSync(bin, ['-version'], { stdio: 'ignore' });
      return bin;
    } catch {
      /* keep looking */
    }
  }
  throw new Error('ImageMagick not found (need `magick` or `convert` on PATH)');
})();

fs.mkdirSync(photos, { recursive: true });

// 1. Brand mark: the zip only ships logo-raw.jpg.
fs.copyFileSync(path.join(assets, 'logo-raw.jpg'), path.join(assets, 'logo.jpg'));

// 2. The two real sample photos.
for (const real of ['anime', 'vintage']) {
  fs.copyFileSync(path.join(assets, 'raw', `${real}.jpg`), path.join(photos, `${real}.jpg`));
}

// 3. Stand-ins for the photos the zip does not ship. They are derived from the two real sample
//    photos (different crops, slight tone shifts) so the reference screenshots stay photo-like.
//    Placeholders only: never shipped in the app, excluded from every pixel diff.
const stand = [
  ['coffee', 3000, 2000, 'vintage', '60%x40%+120+80', '96,118,100'],
  ['dog', 3024, 2016, 'anime', '70%x46%+40+200', '104,112,100'],
  ['facade', 3000, 2000, 'vintage', '55%x37%+0+400', '92,108,102'],
  ['friends', 3024, 2016, 'anime', '62%x41%+300+0', '102,110,104'],
  ['lake', 4032, 3024, 'vintage', '100%x75%+0+0', '100,116,98'],
  ['market', 3000, 2000, 'anime', '58%x39%+120+520', '98,124,96'],
  ['night', 3024, 2016, 'vintage', '45%x30%+500+300', '78,96,96'],
  ['portrait', 3024, 4032, 'anime', '46%x61%+320+60', '100,106,102'],
];

for (const [name, w, h, from, crop, modulate] of stand) {
  const out = path.join(photos, `${name}.jpg`);
  if (fs.existsSync(out)) continue;
  execFileSync(magick, [
    path.join(assets, 'raw', `${from}.jpg`),
    '-gravity', 'NorthWest',
    '-crop', crop,
    '+repage',
    '-resize', `${w}x${h}^`,
    '-gravity', 'center',
    '-extent', `${w}x${h}`,
    '-modulate', modulate,
    '-quality', '82',
    out,
  ]);
}

// 4. Low-res "before" twins for every photo.
for (const f of fs.readdirSync(photos)) {
  if (f.endsWith('_lo.jpg')) continue;
  const lo = path.join(photos, f.replace(/\.jpg$/, '_lo.jpg'));
  if (!fs.existsSync(lo)) {
    execFileSync(magick, [path.join(photos, f), '-resize', '25%', '-quality', '78', lo]);
  }
}
console.log('stub assets ready in', photos);
