/**
 * TVBox 配置 JSON 拉取与校验（供手机端「线路测试」使用，避免浏览器 CORS）
 */

const FETCH_TIMEOUT_MS = 18000;
const MAX_BODY_BYTES = 8 * 1024 * 1024;

function trimJsonObject(content) {
  if (!content) return '';
  const trimContent = content.trim();
  const start = trimContent.indexOf('{');
  const end = trimContent.lastIndexOf('}');
  if (start >= 0 && end > start) return trimContent.substring(start, end + 1);
  return trimContent;
}

function resolveUrl(base, relative) {
  if (!relative) return '';
  try {
    return new URL(relative, base).href;
  } catch (e) {
    return relative;
  }
}

function isAllowedUrl(url) {
  try {
    const u = new URL(url);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch (e) {
    return false;
  }
}

function analyzeConfig(jsonStr, baseUrl) {
  const trimmed = trimJsonObject(jsonStr);
  if (!trimmed) {
    return { ok: false, error: '内容为空' };
  }

  let info;
  try {
    info = JSON.parse(trimmed);
  } catch (e) {
    return { ok: false, error: 'JSON 解析失败：' + e.message };
  }

  if (!info || typeof info !== 'object') {
    return { ok: false, error: '不是有效的 JSON 对象' };
  }

  const urls = info.urls;
  if (Array.isArray(urls) && !info.sites) {
    const children = [];
    for (const element of urls) {
      let name = '';
      let url = '';
      if (element && typeof element === 'object') {
        name = String(element.name || element.title || '').trim();
        url = String(element.url || element.api || '').trim();
      } else if (typeof element === 'string') {
        url = element.trim();
      }
      if (url) {
        children.push({ name, url: resolveUrl(baseUrl, url) });
      }
    }
    if (children.length === 0) {
      return { ok: false, error: '多仓索引 urls 为空', type: 'index' };
    }
    return {
      ok: true,
      type: 'index',
      title: String(info.name || info.title || '').trim(),
      childCount: children.length,
      children: children.slice(0, 30),
      truncated: children.length > 30
    };
  }

  const sites = Array.isArray(info.sites) ? info.sites.length : 0;
  const lives = Array.isArray(info.lives) ? info.lives.length : 0;
  const parses = Array.isArray(info.parses) ? info.parses.length : 0;

  if (!sites && !lives) {
    return { ok: false, error: '不是有效的 TVBox 配置（缺少 sites / lives）', type: 'unknown' };
  }

  return {
    ok: true,
    type: 'full',
    title: String(info.name || info.title || '').trim(),
    sites,
    lives,
    parses,
    hasSpider: !!info.spider,
    spider: info.spider ? String(info.spider) : ''
  };
}

async function fetchConfigText(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'TVBox-Cloud-ConfigTest/1.0', Accept: 'application/json, text/plain, */*' },
      redirect: 'follow'
    });
    if (!res.ok) {
      return { ok: false, error: 'HTTP ' + res.status };
    }
    const buf = await res.arrayBuffer();
    if (buf.byteLength > MAX_BODY_BYTES) {
      return { ok: false, error: '响应过大（>' + MAX_BODY_BYTES + ' 字节）' };
    }
    const text = new TextDecoder('utf-8', { fatal: false }).decode(buf);
    return { ok: true, text, finalUrl: res.url || url };
  } catch (e) {
    if (e.name === 'AbortError') return { ok: false, error: '请求超时（' + (FETCH_TIMEOUT_MS / 1000) + 's）' };
    return { ok: false, error: e.message || '网络错误' };
  } finally {
    clearTimeout(timer);
  }
}

async function testConfigUrl(url) {
  const trimmed = (url || '').trim();
  if (!trimmed) {
    return { ok: false, error: '缺少 url' };
  }
  if (!isAllowedUrl(trimmed)) {
    return { ok: false, error: '仅支持 http / https 地址' };
  }

  const fetched = await fetchConfigText(trimmed);
  if (!fetched.ok) {
    return { ok: false, url: trimmed, error: fetched.error };
  }

  const analyzed = analyzeConfig(fetched.text, fetched.finalUrl || trimmed);
  return {
    ...analyzed,
    url: trimmed,
    finalUrl: fetched.finalUrl || trimmed,
    bytes: fetched.text.length
  };
}

module.exports = {
  testConfigUrl,
  isAllowedUrl,
  analyzeConfig,
  trimJsonObject
};
