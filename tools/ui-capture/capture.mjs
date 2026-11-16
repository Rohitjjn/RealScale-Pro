#!/usr/bin/env node
/**
 * Captures the RealScale-Pro React prototype as reference artefacts:
 *
 *   docs/ui-reference/screens/<theme>/NN-name.png          screenshot (800x1720, DSF 2)
 *   docs/ui-reference/screens/<theme>/NN-name.layout.json   nested layout spec of the same state
 *
 * The prototype itself is never modified: it is built into `dist/` (git-ignored) and served
 * read-only. Every capture runs with `prefers-reduced-motion: reduce` so animation frames are
 * deterministic; the animation metadata is recorded separately by tools/ui-capture/animations.mjs.
 */
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const dist = path.join(repo, 'docs/ui-reference/prototype/dist');
const outRoot = path.join(repo, 'docs/ui-reference/screens');

const WIDTH = Number(process.env.CAPTURE_W ?? 400);
const HEIGHT = Number(process.env.CAPTURE_H ?? 860);
const DSF = 2;
const ONLY = process.env.CAPTURE_ONLY ?? '';
const THEMES = (process.env.CAPTURE_THEMES ?? 'light,dark').split(',');

if (!fs.existsSync(path.join(dist, 'index.html'))) {
  console.error('dist/index.html missing — run `npm run build` first (see tools/ui-capture/README.md)');
  process.exit(2);
}

/* ---------- static server ---------- */
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.jpg': 'image/jpeg', '.png': 'image/png', '.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  const url = decodeURIComponent((req.url ?? '/').split('?')[0]);
  const file = path.join(dist, url === '/' ? 'index.html' : url);
  if (!file.startsWith(dist) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404).end('not found');
    return;
  }
  res.writeHead(200, { 'content-type': MIME[path.extname(file)] ?? 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});

/* ---------- page helpers ---------- */
const KEY = 'realscale-pro:v1';

const baseState = (over = {}) => ({
  onboarded: true,
  photoAccess: true,
  notifications: true,
  cameraAsked: true,
  mode: 'upscale',
  superIntroSeen: true,
  installed: ['realesrgan-x4plus', 'realcugan-se-2x'],
  results: [],
  settings: {
    theme: 'light',
    dynamicColor: false,
    haptics: true,
    launchMode: 'last',
    compute: 'auto',
    keepAwake: true,
    throttle: true,
    format: 'auto',
    keepMeta: true,
    wifiOnly: true,
    analytics: false,
    crashReports: false,
    ...(over.settings ?? {}),
  },
  prefs: over.prefs,
  ...Object.fromEntries(Object.entries(over).filter(([k]) => k !== 'settings')),
});

const settled = async (page) => {
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
};

async function boot(browser, { theme = 'light', state, url = '/' } = {}) {
  // The prototype stores its own theme setting; drive it so [data-theme="dark"] really applies.
  const seeded = { ...state, settings: { ...(state?.settings ?? {}), theme } };
  state = seeded;
  const page = await browser.newPage();
  page.on('pageerror', (e) => console.log('  ! pageerror:', e.message));
  page.on('console', (m) => m.type() === 'error' && console.log('  ! console:', m.text()));
  await page.emulateMediaFeatures([
    { name: 'prefers-color-scheme', value: theme },
    { name: 'prefers-reduced-motion', value: 'reduce' },
  ]);
  await page.evaluateOnNewDocument(
    (k, v) => {
      try {
        localStorage.setItem(k, v);
      } catch {
        /* ignore */
      }
    },
    KEY,
    JSON.stringify(state),
  );
  await page.goto(`http://127.0.0.1:${server.address().port}${url}`, { waitUntil: 'load' });
  return page;
}

const wait = (page, ms) => new Promise((r) => setTimeout(r, ms));

/** Resolves when `selector` matches, or when `fn()` in the page returns truthy. */
async function waitFor(page, selectorOrFn, timeout = 8000) {
  const started = Date.now();
  while (Date.now() - started < timeout) {
    const ok = await page.evaluate((sel) => {
      if (typeof sel === 'string') return !!document.querySelector(sel);
      return false;
    }, selectorOrFn);
    if (ok) return true;
    await new Promise((r) => setTimeout(r, 60));
  }
  return false;
}

/** Click by visible accessible name (aria-label or inner text, whitespace-insensitive, substring ok). */
async function tap(page, name, { nth = 0, last = false, timeout = 5000 } = {}) {
  const res = await page.evaluate(
    (name, nth, last) => {
      const norm = (v) => (v ?? '').replace(/\s+/g, '').toLowerCase();
      const els = [...document.querySelectorAll('button,[role="button"],[role="radio"],[role="switch"],[role="tab"],a,summary,label,[role="slider"]')];
      const key = norm(name);
      const scored = [];
      els.forEach((el, i) => {
        const aria = norm(el.getAttribute('aria-label'));
        const text = norm(el.innerText);
        if (aria === key || text === key) scored.push({ el, tier: 0, i });
        else if (aria.includes(key) || text.includes(key)) scored.push({ el, tier: 1, i });
      });
      scored.sort((a, b) => a.tier - b.tier || a.i - b.i);
      const pick = last ? scored[scored.length - 1] : scored[nth];
      if (!pick || (!last && nth >= scored.length)) {
        return { ok: false, list: els.map((e) => norm(e.getAttribute('aria-label') || e.innerText)).filter(Boolean).slice(0, 70) };
      }
      pick.el.scrollIntoView({ block: 'center', inline: 'center' });
      pick.el.click();
      return { ok: true, label: norm(pick.el.getAttribute('aria-label') || pick.el.innerText).slice(0, 60), count: scored.length };
    },
    name,
    nth,
    last,
  );
  if (!res.ok) throw new Error(`tap("${name}") not found. controls: ${res.list.join(' | ')}`);
  await wait(page, 260);
}

async function buttonList(page) {
  return page.evaluate(() =>
    [...document.querySelectorAll('button,[role="button"],[role="radio"],[role="switch"],input,summary,a')]
      .map((el) => (el.getAttribute('aria-label') ?? el.innerText ?? el.getAttribute('type') ?? el.tagName).replace(/\s+/g, ' ').trim().slice(0, 40))
      .filter(Boolean)
      .slice(0, 60)
      .join(' | '),
  );
}

async function tapText(page, text) {
  const ok = await page.evaluate((t) => {
    const el = [...document.querySelectorAll('button,[role="button"],div,span,a,li')].find((n) => n.textContent?.trim() === t);
    if (!el) return false;
    (el.closest('button') ?? el).click();
    return true;
  }, text);
  if (!ok) throw new Error(`tapText("${text}") not found; buttons: ${await buttonList(page)}`);
  await wait(page, 260);
}

async function scrollTo(page, selectorOrText, { dy = 0 } = {}) {
  await page.evaluate(
    (sel, dy) => {
      const scroller = [...document.querySelectorAll('div')].find((d) => d.scrollHeight > d.clientHeight + 40 && getComputedStyle(d).overflowY !== 'visible');
      if (!scroller) return;
      const target = document.querySelector(sel);
      if (target) scroller.scrollTop += target.getBoundingClientRect().top - 100 + dy;
      else scroller.scrollTop += dy;
    },
    selectorOrText,
    dy,
  );
  await wait(page, 200);
}

async function scrollMain(page, top) {
  await page.evaluate((top) => {
    const scroller = [...document.querySelectorAll('div')].find((d) => d.scrollHeight > d.clientHeight + 40 && ['auto', 'scroll'].includes(getComputedStyle(d).overflowY));
    if (scroller) scroller.scrollTop = typeof top === 'string' ? (top === 'end' ? scroller.scrollHeight : Number(top)) : top;
  }, top);
  await wait(page, 260);
}

/* ---------- layout spec ---------- */
const LAYOUT_FN = () => {
  const STYLE_KEYS = [
    'fontFamily', 'fontSize', 'fontWeight', 'lineHeight', 'letterSpacing', 'textTransform', 'fontVariantNumeric',
    'color', 'backgroundColor', 'borderTopWidth', 'borderTopColor', 'borderBottomWidth',
    'borderRadius', 'padding', 'margin', 'gap', 'boxShadow', 'opacity', 'transform',
    'animationName', 'animationDuration', 'animationTimingFunction', 'display', 'flexDirection',
    'justifyContent', 'alignItems', 'overflow', 'position', 'textAlign',
  ];
  const round = (n) => Math.round(n * 100) / 100;
  const vw = innerWidth;
  const vh = innerHeight;
  const SKIP_TAGS = new Set(['path', 'circle', 'defs', 'line', 'rect', 'g', 'use', 'lineargradient', 'stop', 'clippath', 'filter', 'mask', 'title', 'desc', 'polygon', 'polyline', 'ellipse']);
  const ownText = (el) => {
    let t = '';
    for (const n of el.childNodes) if (n.nodeType === 3) t += n.textContent;
    return t.replace(/\s+/g, ' ').trim().slice(0, 120);
  };
  const walk = (el, depth) => {
    const tag = el.tagName.toLowerCase();
    if (SKIP_TAGS.has(tag)) return null;
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity) === 0) return null;
    const visible = r.width > 0.5 && r.height > 0.5 && r.bottom > 0 && r.top < vh && r.right > 0 && r.left < vw;
    const text = ownText(el);
    const aria = el.getAttribute('aria-label');
    const role = el.getAttribute('role');
    const bg = cs.backgroundColor;
    const hasBorder = parseFloat(cs.borderTopWidth) > 0 || parseFloat(cs.borderBottomWidth) > 0;
    const art = tag === 'img' || tag === 'svg' || tag === 'input' || tag === 'hr' || tag === 'video';
    const own = text || aria || role || art || (bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') || hasBorder || (cs.boxShadow && cs.boxShadow !== 'none');
    const kids = [];
    if (visible) {
      for (const child of el.children) {
        const n = walk(child, depth + 1);
        if (n) kids.push(n);
      }
    }
    if (!visible) return null;
    if (!own && kids.length === 0) return null;
    if (!own && kids.length === 1 && cs.display !== 'flex' && cs.display !== 'grid') return kids[0];
    const node = {
      t: tag,
      box: [round(r.left), round(r.top), round(r.width), round(r.height)],
    };
    if (role) node.role = role;
    if (aria) node.aria = aria;
    if (text) node.text = text;
    if (tag === 'input') node.value = el.value;
    if (el.getAttribute('aria-checked')) node.checked = el.getAttribute('aria-checked');
    if (el.getAttribute('disabled') !== null) node.disabled = true;
    const style = {};
    for (const k of STYLE_KEYS) {
      const v = cs[k];
      if (v && v !== 'none' && v !== 'normal' && v !== 'auto' && v !== '0px' && v !== 'rgba(0, 0, 0, 0)' && v !== 'visible' && v !== 'static' && v !== 'row' && v !== 'flex-start' && v !== 'stretch' && v !== 'left' && v !== 'start') style[k] = v;
    }
    if (Object.keys(style).length) node.s = style;
    if (kids.length) node.k = kids;
    return node;
  };
  return walk(document.body, 0);
};

async function setRange(page, label, value) {
  await page.evaluate(
    (label, value) => {
      const el = document.querySelector(`input[type="range"][aria-label="${label}"]`);
      if (!el) throw new Error('range not found: ' + label);
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      setter.call(el, String(value));
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    },
    label,
    value,
  );
  await new Promise((r) => setTimeout(r, 200));
}

/* ---------- snapshot ---------- */
async function snap(page, theme, name, scenarioNo = 0, shot = 1) {
  const folder = path.join(outRoot, theme);
  fs.mkdirSync(folder, { recursive: true });
  const nn = String(scenarioNo).padStart(2, '0');
  const base = path.join(folder, `${nn}-${name}${shot > 1 ? `-${shot}` : ''}`);
  await settled(page);
  await page.screenshot({ path: `${base}.png` });
  const layout = await page.evaluate(LAYOUT_FN);
  fs.writeFileSync(`${base}.layout.json`, JSON.stringify({ screen: name, viewport: { w: WIDTH, h: HEIGHT, dsf: DSF }, theme, tree: layout }, null, 0));
  const size = fs.statSync(`${base}.png`).size;
  console.log(`  ✓ ${theme}/${nn}-${name}.png (${(size / 1024).toFixed(0)} kB)`);
  return `${nn}-${name}`;
}

/* ---------- scenarios ---------- */
import { SCENARIOS } from './scenarios.mjs';

async function main() {
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const executablePath = await chromium.executablePath();
  const browser = await puppeteer.launch({
    args: [...chromium.args, '--font-render-hinting=none', '--force-color-profile=srgb', '--hide-scrollbars'],
    executablePath,
    headless: true,
    defaultViewport: { width: WIDTH, height: HEIGHT, deviceScaleFactor: DSF, isMobile: true, hasTouch: true },
  });
  let failures = 0;
  for (const theme of THEMES) {
    let scenarioNo = 0;
    for (const scenario of SCENARIOS) {
      scenarioNo += 1;
      if (ONLY && !scenario.name.includes(ONLY)) continue;
      let shot = 0;
      const ctx = {
        browser, theme, scenarioNo,
        boot: (opts = {}) => boot(browser, { theme, ...opts }),
        snap: (page, name) => snap(page, theme, name, scenarioNo, ++shot),
        tap, tapText, wait, waitFor, scrollMain, scrollTo, buttonList, settled,
        setRange: (page, label, value) => setRange(page, label, value),
        close: (page) => page.close(),
      };
      try {
        await scenario.run(ctx);
        if (scenario.close !== false) {
          for (const p of await browser.pages()) if (p.url().startsWith('http://127.0.0.1')) await p.close();
        }
      } catch (e) {
        failures += 1;
        console.error(`  ✗ ${theme}/${scenario.name}: ${e.message}`);
      }
    }
  }
  await browser.close();
  server.close();
  console.log(failures ? `\n${failures} scenario(s) failed` : '\nall scenarios captured');
  process.exit(failures ? 1 : 0);
}

main().catch(async (e) => {
  console.error(e);
  server.close();
  process.exit(1);
});
