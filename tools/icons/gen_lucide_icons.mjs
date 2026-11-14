#!/usr/bin/env node
/**
 * Generates Compose ImageVectors for the Lucide glyphs the prototype uses.
 *
 * Source: `lucide-static` (npm, ISC licence — the same glyphs `lucide-react` renders), so the
 * Compose icons are pixel-identical to the prototype's rather than look-alikes.
 * Geometry is emitted as SVG path data and parsed at class-load time with `addPathNodes`.
 *
 *   node tools/icons/gen_lucide_icons.mjs            # needs `npm i lucide-static` in tools/icons
 *
 * Output: RealSR-NCNN-Android-GUI/app/src/main/java/com/tumuyan/ncnn/realsr/design/icons/LucideIcons.kt
 */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const require = createRequire(import.meta.url);
const iconsDir = path.dirname(require.resolve('lucide-static/package.json')) + '/icons';
const outKt = path.join(repo, 'RealSR-NCNN-Android-GUI/app/src/main/java/com/tumuyan/ncnn/realsr/design/icons/LucideIcons.kt');

// Every glyph imported by docs/ui-reference/prototype/src/**/*.tsx (grep 'lucide-react').
const NAMES = [
  'ArrowLeftRight', 'Bell', 'BookOpen', 'Camera', 'Check', 'ChevronDown', 'ChevronLeft', 'ChevronRight',
  'ChevronsLeftRight', 'CircleAlert', 'CircleHelp', 'Clipboard', 'Cloud', 'Cpu', 'Crop', 'Download',
  'Ellipsis', 'Expand', 'Eye', 'FileText', 'FolderOpen', 'Gauge', 'HardDrive', 'ImagePlus', 'ImageUpscale',
  'Images', 'Info', 'Laptop', 'Layers', 'Lock', 'MemoryStick', 'MessageSquare', 'Minus', 'Pause', 'Play',
  'Plus', 'RefreshCw', 'Replace', 'RotateCcw', 'Settings', 'Share2', 'ShieldCheck', 'Smartphone', 'Tablet',
  'Thermometer', 'Trash2', 'TriangleAlert', 'X', 'Zap',
];

const kebab = (name) =>
  name
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
    .replace(/([a-z])(\d)$/g, '$1-$2')
    .toLowerCase();

const num = (v) => Number(v);

/** Converts every SVG primitive lucide uses into one SVG path string. */
function toPathData(svg) {
  const parts = [];
  const elements = svg.match(/<(path|circle|rect|line|polyline|polygon)\b[^>]*\/?>/g) ?? [];
  for (const el of elements) {
    const attr = (n) => {
      const m = el.match(new RegExp(`${n}="([^"]*)"`));
      return m ? m[1] : null;
    };
    const tag = el.match(/<([a-z]+)/)[1];
    const fill = attr('fill');
    let d = '';
    if (tag === 'path') {
      d = attr('d') ?? '';
    } else if (tag === 'circle') {
      const cx = num(attr('cx') ?? 0);
      const cy = num(attr('cy') ?? 0);
      const r = num(attr('r') ?? 0);
      d = `M ${cx - r} ${cy} a ${r} ${r} 0 1 0 ${2 * r} 0 a ${r} ${r} 0 1 0 ${-2 * r} 0`;
    } else if (tag === 'rect') {
      const x = num(attr('x') ?? 0);
      const y = num(attr('y') ?? 0);
      const w = num(attr('width') ?? 0);
      const h = num(attr('height') ?? 0);
      const rx = num(attr('rx') ?? 0);
      d = rx > 0
        ? `M ${x + rx} ${y} h ${w - 2 * rx} a ${rx} ${rx} 0 0 1 ${rx} ${rx} v ${h - 2 * rx} a ${rx} ${rx} 0 0 1 ${-rx} ${rx} h ${-(w - 2 * rx)} a ${rx} ${rx} 0 0 1 ${-rx} ${-rx} v ${-(h - 2 * rx)} a ${rx} ${rx} 0 0 1 ${rx} ${-rx} z`
        : `M ${x} ${y} h ${w} v ${h} h ${-w} z`;
    } else if (tag === 'line') {
      d = `M ${attr('x1')} ${attr('y1')} L ${attr('x2')} ${attr('y2')}`;
    } else if (tag === 'polyline' || tag === 'polygon') {
      const pts = (attr('points') ?? '').trim().split(/[\s,]+/).map(Number);
      const pairs = [];
      for (let i = 0; i < pts.length - 1; i += 2) pairs.push(`${pts[i]} ${pts[i + 1]}`);
      d = `M ${pairs.join(' L ')}` + (tag === 'polygon' ? ' Z' : '');
    }
    parts.push({ d, fill, strokeWidth: attr('stroke-width'), stroke: attr('stroke') });
  }
  return parts;
}

const entries = NAMES.map((name) => {
  const file = path.join(iconsDir, `${kebab(name)}.svg`);
  if (!fs.existsSync(file)) throw new Error(`missing lucide icon: ${kebab(name)} (${name})`);
  const svg = fs.readFileSync(file, 'utf8');
  const parts = toPathData(svg);
  const body = parts
    .map(({ d, fill, strokeWidth, stroke }) => {
      const filled = fill && fill !== 'none';
      const stroked = !stroke || stroke !== 'none';
      const args = [
        filled ? 'fill = SolidColor(Color.Black)' : null,
        stroked ? 'stroke = SolidColor(Color.Black)' : null,
        stroked ? `strokeLineWidth = ${Number(strokeWidth ?? 2)}f` : null,
        stroked ? 'strokeLineCap = StrokeCap.Round' : null,
        stroked ? 'strokeLineJoin = StrokeJoin.Round' : null,
      ].filter(Boolean);
      return `            path(${args.join(', ')}) { addPathNodes(${JSON.stringify(d)}) }`;
    })
    .join('\n');
  return { name, body };
});

const kt = `package com.tumuyan.ncnn.realsr.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The prototype's Lucide glyphs, converted 1:1 from \`lucide-static\` (ISC licence, v1.72.0) by
 * \`tools/icons/gen_lucide_icons.mjs\`. Geometry is the original SVG path data; stroke width 2 on a
 * 24 x 24 viewBox, round caps and joins, exactly like \`lucide-react\`.
 *
 * Generated file — do not edit by hand.
 */
object Lucide {
${entries.map(({ name }) => `    val ${name}: ImageVector by lazy { build("${name}", "${kebab(name)}") }`).join('\n')}

    private fun build(name: String, id: String): ImageVector {
        val builder = ImageVector.Builder(
            name = id,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        return when (name) {
${entries.map(({ name, body }) => `            "${name}" -> builder.apply {\n${body}\n            }`).join('\n')}
            else -> builder
        }.build()
    }
}
`;

fs.mkdirSync(path.dirname(outKt), { recursive: true });
fs.writeFileSync(outKt, kt);
console.log(`LucideIcons.kt: ${entries.length} icons → ${path.relative(repo, outKt)}`);
