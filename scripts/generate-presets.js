/**
 * 从 config-test-all.csv 生成 remote-control/backend/presets.json
 */
const fs = require('fs');
const path = require('path');
const { normalizeConfigUrl, dedupeByUrl, pickBetterEntry } = require('../remote-control/backend/urlNormalize');

const csvPath = path.join(__dirname, 'config-test-all.csv');
const outPath = path.join(__dirname, '..', 'remote-control', 'backend', 'presets.json');
const base = 'https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/';

const FRIENDLY_NAMES = {
  'jsm.json': 'jsm 家庭电视（推荐）',
  '0707.json': '0707 多仓索引',
  '0821.json': '0821 大而全',
  '0825.json': '0825 小而精',
  '0826.json': '0826 FTY',
  '0827.json': '0827 FM',
  'js.json': 'js 聚合',
  'XYQ.json': 'XYQ',
  'fty.json': 'fty',
  '367.json': '367',
  '9918.json': '9918',
  '99188.json': '99188',
  'dianshi.json': 'dianshi'
};

const CHILD_NAMES = {
  '0821.json': '高天流云 No.1',
  '0825.json': '高天流云 PG',
  '0826.json': '高天流云 FTY',
  '0827.json': '高天流云 FM',
  'js.json': '高天流云 JS',
  'XYQ.json': '高天流云 XYQ'
};

function friendlyName(url, rawName, depth) {
  const file = url.split('/').pop();
  if (depth === 1 && CHILD_NAMES[file]) return CHILD_NAMES[file];
  if (FRIENDLY_NAMES[file]) return FRIENDLY_NAMES[file];
  const rel = url.replace(base, '');
  if (rel && rel !== file) return rel;
  const clean = rawName.replace(/^[\s└├│─?]+/, '').trim();
  if (clean && !/[\uFFFD]/.test(clean) && clean.length > 1) return clean;
  return file || clean || url;
}

function groupFrom(url, name, depth, type) {
  if (url.includes('127.0.0.1')) return '局域网';
  if (type === 'index' || url.endsWith('/0707.json')) return '多仓索引';
  if (name.includes('jsm') && depth === 0 && url.includes('jsm.json')) return '推荐';
  if (depth === 1 && type === 'full') return '0707 子线路';
  if (url.includes('/FTY/')) return 'FTY 单站';
  if (url.includes('/XBPQ/')) return 'XBPQ 单站';
  if (url.includes('/tools/')) return 'tools';
  if (depth === 0 && type === 'full') return '主入口';
  return '其他';
}

function upsertMap(map, entry) {
  const key = normalizeConfigUrl(entry.url);
  if (!key) return;
  const item = Object.assign({}, entry, { url: key });
  const prev = map.get(key);
  map.set(key, prev ? pickBetterEntry(prev, item) : item);
}

const csv = fs.readFileSync(csvPath, 'utf8');
const lines = csv.split(/\r?\n/).slice(1).filter(Boolean);
const presetMap = new Map();
const excludedMap = new Map();

for (const line of lines) {
  const parts = [];
  let cur = '';
  let inQ = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') { inQ = !inQ; continue; }
    if (c === ',' && !inQ) { parts.push(cur); cur = ''; continue; }
    cur += c;
  }
  parts.push(cur);
  if (parts.length < 12) continue;

  const name = parts[0].trim();
  const url = parts[1].trim();
  const depth = Number(parts[2]) || 0;
  const type = parts[5].trim();
  const sites = parts[6].trim();
  const lives = parts[7].trim();
  const parses = parts[8].trim();
  const status = parts[11].trim();

  if (status === 'FAIL') {
    upsertMap(excludedMap, {
      name: friendlyName(url, name.replace(/^[\s└├│─]+/, ''), depth),
      url,
      reason: '检测失败或 JSON 无效',
      availability: 'unavailable',
      warehouse: type === 'index' ? 'multi' : 'single',
      depth
    });
    continue;
  }
  if (status !== 'OK' && status !== 'WARN') continue;

  const cleanName = friendlyName(url, name, depth);
  upsertMap(presetMap, {
    name: cleanName,
    url,
    group: groupFrom(url, cleanName, depth, type),
    type,
    availability: 'available',
    warehouse: type === 'index' ? 'multi' : 'single',
    sites,
    lives,
    parses,
    depth,
    verified: status
  });
}

[
  { name: 'jsm（局域网）', url: 'http://127.0.0.1:9978/tvboxConfig/jsm.json', group: '局域网', type: 'full', sites: '-', lives: '-', parses: '-', depth: 0, verified: 'local' },
  { name: '自定义 CMS', url: 'http://127.0.0.1:9978/config/main.json', group: '局域网', type: 'full', sites: '-', lives: '-', parses: '-', depth: 0, verified: 'local' },
  { name: '仅直播模板', url: 'http://127.0.0.1:9978/config/live-only.json', group: '局域网', type: 'full', sites: '-', lives: '-', parses: '-', depth: 0, verified: 'local' }
].forEach((p) => {
  upsertMap(presetMap, Object.assign({ availability: 'available', warehouse: 'single' }, p));
});

let presets = Array.from(presetMap.values());
const presetKeys = new Set(presets.map((p) => normalizeConfigUrl(p.url)));
let excluded = Array.from(excludedMap.values()).filter((e) => !presetKeys.has(normalizeConfigUrl(e.url)));

presets = dedupeByUrl(presets);
excluded = dedupeByUrl(excluded);

const order = ['推荐', '多仓索引', '0707 子线路', '主入口', 'tools', 'FTY 单站', 'XBPQ 单站', '局域网', '其他'];
presets.sort((a, b) => {
  const ga = order.indexOf(a.group);
  const gb = order.indexOf(b.group);
  return (ga < 0 ? 99 : ga) - (gb < 0 ? 99 : gb) || a.name.localeCompare(b.name, 'zh');
});
excluded.sort((a, b) => a.name.localeCompare(b.name, 'zh'));

const multiCount = presets.filter((p) => p.warehouse === 'multi').length;
const singleCount = presets.filter((p) => p.warehouse === 'single').length;

const out = {
  repoBase: base,
  presetCount: presets.length,
  excludedCount: excluded.length,
  availableCount: presets.length,
  unavailableCount: excluded.length,
  multiCount,
  singleCount,
  groupOrder: order,
  classify: {
    availability: ['available', 'unavailable'],
    warehouse: ['multi', 'single']
  },
  presets,
  excluded,
  note: '由 scripts/config-test-all.csv 生成；地址已规范化并按 URL 去重（保留 depth 更小者优先）。'
};

fs.writeFileSync(outPath, JSON.stringify(out, null, 2), 'utf8');
console.log('Wrote', presets.length, 'presets,', excluded.length, 'excluded (deduped) ->', outPath);
