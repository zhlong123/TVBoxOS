/**
 * 配置地址规范化，用于去重比较（忽略末尾斜杠、首尾空格）
 */
function normalizeConfigUrl(url) {
  const raw = String(url || '').trim();
  if (!raw) return '';
  try {
    const u = new URL(raw);
    let path = u.pathname || '/';
    if (path.length > 1) path = path.replace(/\/+$/, '');
    return u.protocol + '//' + u.host + path + (u.search || '');
  } catch (e) {
    return raw.replace(/\/+$/, '');
  }
}

function dedupeByUrl(items, getUrl) {
  const map = new Map();
  (items || []).forEach((item) => {
    const url = getUrl ? getUrl(item) : item.url;
    const key = normalizeConfigUrl(url);
    if (!key) return;
    const prev = map.get(key);
    if (!prev) {
      map.set(key, item);
      return;
    }
    const pick = pickBetterEntry(prev, item);
    map.set(key, pick);
  });
  return Array.from(map.values());
}

function pickBetterEntry(a, b) {
  const depthA = Number(a.depth) || 99;
  const depthB = Number(b.depth) || 99;
  if (depthA !== depthB) return depthA < depthB ? a : b;
  if (a.availability === 'available' && b.availability === 'unavailable') return a;
  if (b.availability === 'available' && a.availability === 'unavailable') return b;
  if (a.verified === 'OK' && b.verified !== 'OK') return a;
  if (b.verified === 'OK' && a.verified !== 'OK') return b;
  return (a.name || '').length >= (b.name || '').length ? a : b;
}

module.exports = { normalizeConfigUrl, dedupeByUrl, pickBetterEntry };
