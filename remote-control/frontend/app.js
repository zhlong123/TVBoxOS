(function () {
  'use strict';

  const TOKEN_KEY = 'tvbox_cloud_token';
  const SERVER_KEY = 'tvbox_cloud_server';
  const USER_KEY = 'tvbox_cloud_user';
  const CUSTOM_LINES_KEY = 'tvbox_custom_config_lines';

  const FALLBACK_PRESETS = [
    { name: 'jsm 家庭电视（推荐）', url: 'https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/jsm.json', group: '推荐' },
    { name: '0707 多仓索引', url: 'https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/0707.json', group: '多仓' }
  ];

  let presetLines = FALLBACK_PRESETS.slice();
  let excludedLines = [];
  let configAvailFilter = 'available';
  let configWarehouseFilter = 'all';
  let configSearchQuery = '';
  let presetCounts = { available: 0, unavailable: 0, multi: 0, single: 0 };
  let configsTabLoaded = { _ready: false, _apiLines: [] };

  let apiBase = '';
  let authToken = '';
  let pollTimer = null;
  let isRegister = false;
  let currentApiUrl = '';
  let pendingSwitchUrl = '';
  let pendingSwitchAt = 0;
  let configTestCache = {};

  const $ = (sel) => document.querySelector(sel);
  const authPanel = $('#authPanel');
  const remotePanel = $('#remotePanel');
  const bottomNav = $('#bottomNav');
  const liveDot = $('#liveDot');
  const headerTitle = $('#headerTitle');
  const headerSub = $('#headerSub');
  const headerUsername = $('#headerUsername');
  const userMenu = $('#userMenu');
  const userMenuName = $('#userMenuName');
  const offlineBanner = $('#offlineBanner');
  const toastEl = $('#toast');
  const serverAdvanced = $('#serverAdvanced');

  function normalizeServer(input) {
    let url = (input || '').trim();
    if (!url) return '';
    if (!/^https?:\/\//i.test(url)) url = 'http://' + url;
    return url.replace(/\/+$/, '');
  }

  function getDefaultServer() {
    if (location.port === '3080' || location.pathname.indexOf('/remote') < 0) {
      return location.origin;
    }
    return 'http://localhost:3080';
  }

  function showToast(msg, ms) {
    toastEl.textContent = msg;
    toastEl.hidden = false;
    toastEl.classList.add('show');
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => {
      toastEl.classList.remove('show');
      setTimeout(() => { toastEl.hidden = true; }, 200);
    }, ms || 2200);
  }

  async function apiFetch(path, options) {
    const opts = options || {};
    opts.headers = opts.headers || {};
    if (authToken) opts.headers.Authorization = 'Bearer ' + authToken;
    if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof URLSearchParams)) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(opts.body);
    }
    const res = await fetch(apiBase + path, opts);
    const text = await res.text();
    let data;
    try { data = JSON.parse(text); } catch (e) { data = { ok: false, message: text }; }
    if (!res.ok && data.message) throw new Error(data.message);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    return data;
  }

  function closeUserMenu() {
    if (!userMenu) return;
    userMenu.hidden = true;
    const btn = $('#btnReconnect');
    if (btn) btn.setAttribute('aria-expanded', 'false');
  }

  function openUserMenu(name) {
    if (!userMenu) return;
    if (userMenuName) userMenuName.textContent = name || '';
    userMenu.hidden = false;
    const btn = $('#btnReconnect');
    if (btn) btn.setAttribute('aria-expanded', 'true');
  }

  function toggleUserMenu(name) {
    if (userMenu && !userMenu.hidden) closeUserMenu();
    else openUserMenu(name);
  }

  function setLoggedIn(loggedIn, username, online, activity) {
    authPanel.hidden = loggedIn;
    remotePanel.hidden = !loggedIn;
    bottomNav.hidden = !loggedIn;
    document.body.classList.toggle('is-remote', loggedIn);
    if (!loggedIn) closeUserMenu();

    liveDot.className = 'status-pill ' + (online ? 'on' : 'off');
    if (offlineBanner) offlineBanner.hidden = !loggedIn || online;

    const btnReconnect = $('#btnReconnect');
    if (loggedIn) {
      const name = username || localStorage.getItem(USER_KEY) || '';
      headerTitle.textContent = 'TVBox';
      const status = online ? (activity || '电视在线') : '电视离线';
      headerSub.textContent = status;
      if (headerUsername) {
        headerUsername.textContent = name;
        headerUsername.hidden = !name;
      }
      if (btnReconnect) {
        btnReconnect.classList.remove('is-guest');
        btnReconnect.setAttribute('aria-label', name ? '账号 · ' + name : '账号');
      }
    } else {
      headerTitle.textContent = 'TVBox';
      headerSub.textContent = '云遥控';
      if (headerUsername) {
        headerUsername.textContent = '';
        headerUsername.hidden = true;
      }
      if (btnReconnect) {
        btnReconnect.classList.add('is-guest');
        btnReconnect.setAttribute('aria-label', '账号');
      }
    }
  }

  async function refreshStatus() {
    if (!authToken) return;
    try {
      const info = await apiFetch('/api/device/status');
      setLoggedIn(true, localStorage.getItem(USER_KEY), info.online, info.activity);
      return info;
    } catch (e) {
      setLoggedIn(true, localStorage.getItem(USER_KEY), false, '');
      return null;
    }
  }

  function startPoll() {
    stopPoll();
    pollTimer = setInterval(refreshStatus, 4000);
  }

  function stopPoll() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  }

  async function doAuth() {
    const server = normalizeServer($('#serverInput').value);
    const username = $('#usernameInput').value.trim();
    const password = $('#passwordInput').value;
    if (!server) { showToast('请输入后端地址'); return; }
    if (username.length < 3) { showToast('用户名至少 3 个字符'); return; }
    if (password.length < 6) { showToast('密码至少 6 位'); return; }

    apiBase = server;
    $('#authStatus').textContent = isRegister ? '注册中…' : '登录中…';
    try {
      const path = isRegister ? '/api/auth/register' : '/api/auth/login';
      const data = await apiFetch(path, { method: 'POST', body: { username, password } });
      authToken = data.token;
      localStorage.setItem(TOKEN_KEY, authToken);
      localStorage.setItem(SERVER_KEY, apiBase);
      localStorage.setItem(USER_KEY, data.user.username);
      $('#authStatus').textContent = '';
      showToast(isRegister ? '注册成功' : '登录成功');
      await refreshStatus();
      startPoll();
    } catch (e) {
      $('#authStatus').textContent = e.message || '失败';
      showToast(e.message || '认证失败');
    }
  }

  function logout() {
    stopPoll();
    authToken = '';
    localStorage.removeItem(TOKEN_KEY);
    closeUserMenu();
    setLoggedIn(false);
    authPanel.hidden = false;
    showToast('已退出登录');
  }

  async function sendCommand(body) {
    if (!authToken) { showToast('请先登录'); return null; }
    try {
      return await apiFetch('/api/command', { method: 'POST', body: body });
    } catch (e) {
      showToast(e.message || '指令失败');
      return null;
    }
  }

  async function sendKey(key) {
    const r = await sendCommand({ type: 'key', key: key });
    if (r && r.ok) flash(document.querySelector('[data-key="' + key + '"]'));
  }

  function flash(el) {
    if (!el) return;
    el.classList.add('pressed');
    clearTimeout(el._flashT);
    el._flashT = setTimeout(() => el.classList.remove('pressed'), 120);
  }

  function bindPressable(el, onPress) {
    let repeatTimer = null;
    let holdTimer = null;
    const clear = () => {
      clearInterval(repeatTimer); repeatTimer = null;
      clearTimeout(holdTimer); holdTimer = null;
    };
    const pressOnce = () => { onPress(); flash(el); };
    el.addEventListener('pointerdown', (e) => {
      if (e.button !== undefined && e.button !== 0) return;
      el.setPointerCapture(e.pointerId);
      pressOnce();
      holdTimer = setTimeout(() => { repeatTimer = setInterval(pressOnce, 110); }, 380);
    });
    el.addEventListener('pointerup', (e) => {
      clear();
      try { el.releasePointerCapture(e.pointerId); } catch (err) { /* ignore */ }
    });
    el.addEventListener('pointercancel', clear);
  }

  function bindKeyButtons() {
    document.querySelectorAll('[data-key]').forEach((el) => {
      bindPressable(el, () => sendKey(el.getAttribute('data-key')));
    });
  }

  function bindNav() {
    document.querySelectorAll('.dock-item').forEach((item) => {
      item.addEventListener('click', () => {
        document.querySelectorAll('.dock-item').forEach((n) => n.classList.remove('active'));
        document.querySelectorAll('.tab-panel').forEach((p) => p.classList.remove('active'));
        item.classList.add('active');
        $('#tab-' + item.dataset.tab).classList.add('active');
        if (item.dataset.tab === 'settings') loadSettings();
        if (item.dataset.tab === 'configs') loadConfigsTab();
      });
    });
  }

  const SETTING_SECTIONS = [
    { title: '接口配置', open: true, fields: [
      { key: 'api_url', label: '点播配置地址', type: 'text' },
      { key: 'live_api_url', label: '直播配置地址', type: 'text' },
      { key: 'danmu_api', label: '弹幕接口地址', type: 'text' }
    ]},
    { title: '首页', open: false, fields: [
      { key: 'home_api', label: '首页站源', type: 'home_sources' },
      { key: 'home_rec', label: '首页推荐', type: 'home_rec' },
      { key: 'home_rec_style', label: '首页多行展示', type: 'toggle' },
      { key: 'default_load_live', label: '下次进入直播页', type: 'toggle' }
    ]},
    { title: '搜索', open: false, fields: [
      { key: 'fast_search_mode', label: '聚合搜索', type: 'toggle' },
      { key: 'search_view', label: '搜索展示', type: 'search_view' },
      { key: 'history_num', label: '历史记录条数', type: 'history_num' }
    ]},
    { title: '播放', open: false, fields: [
      { key: 'play_type', label: '播放器', type: 'play_types' },
      { key: 'play_render', label: '渲染方式', type: 'play_renders' },
      { key: 'play_scale', label: '画面缩放', type: 'play_scales' },
      { key: 'ijk_codec', label: 'IJK 解码', type: 'ijk_codecs' },
      { key: 'm3u8_purify', label: '去广告', type: 'toggle' },
      { key: 'auto_switch_line', label: '自动换线', type: 'toggle' },
      { key: 'show_preview', label: '窗口预览', type: 'toggle' },
      { key: 'ijk_cache_play', label: 'IJK 缓存', type: 'toggle' }
    ]},
    { title: '弹幕与网络', open: false, fields: [
      { key: 'danmu_open', label: '弹幕', type: 'toggle' },
      { key: 'doh_url', label: '安全 DNS', type: 'doh' },
      { key: 'parse_webview', label: '嗅探用系统 WebView', type: 'toggle' }
    ]},
    { title: '界面布局', open: false, fields: [
      { key: 'ui_card_width', label: '卡片宽度 (mm)', type: 'text' },
      { key: 'ui_card_height', label: '卡片高度 (mm)', type: 'text' },
      { key: 'ui_grid_span', label: '网格列数 (0=自动)', type: 'text' },
      { key: 'ui_grid_spacing', label: '卡片间距 (mm)', type: 'text' },
      { key: 'ui_item_margin', label: '卡片外边距 (mm)', type: 'text' },
      { key: 'ui_focus_scale', label: '选中放大 (%)', type: 'text' }
    ]},
    { title: '维护', open: false, fields: [
      { key: 'clear_cache', label: '清空应用缓存', type: 'action', actionLabel: '清空并重启' }
    ]}
  ];

  async function saveSetting(key, value) {
    const r = await sendCommand({ type: 'setting', key: key, value: String(value) });
    if (r && r.ok) { showToast('已保存到电视'); return true; }
    return false;
  }

  function renderSelectRow(key, label, options, current, valueKey, labelKey) {
    const row = document.createElement('div');
    row.className = 'setting-row';
    const lbl = document.createElement('label');
    lbl.textContent = label;
    const select = document.createElement('select');
    (options || []).forEach((opt) => {
      const option = document.createElement('option');
      const val = typeof opt === 'object' ? opt[valueKey || 'value'] : opt;
      const text = typeof opt === 'object' ? opt[labelKey || 'label'] : opt;
      option.value = val;
      option.textContent = text;
      if (String(val) === String(current)) option.selected = true;
      select.appendChild(option);
    });
    select.addEventListener('change', () => {
      saveSetting(key, select.value).then((ok) => { if (ok) setTimeout(() => loadSettings(true), 800); });
    });
    row.appendChild(lbl);
    row.appendChild(select);
    return row;
  }

  function renderTextRow(key, label, value) {
    const row = document.createElement('div');
    row.className = 'setting-row';
    const lbl = document.createElement('label');
    lbl.textContent = label;
    const input = document.createElement('input');
    input.type = 'url';
    input.value = value || '';
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-primary';
    btn.textContent = '保存';
    btn.addEventListener('click', () => {
      saveSetting(key, input.value.trim()).then((ok) => { if (ok) setTimeout(() => loadSettings(true), 800); });
    });
    row.appendChild(lbl);
    row.appendChild(input);
    row.appendChild(btn);
    return row;
  }

  function renderToggleRow(key, label, value) {
    const row = document.createElement('div');
    row.className = 'setting-row setting-inline';
    const lbl = document.createElement('label');
    lbl.textContent = label;
    const wrap = document.createElement('label');
    wrap.className = 'toggle';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = !!value;
    const slider = document.createElement('span');
    slider.className = 'toggle-slider';
    input.addEventListener('change', () => {
      saveSetting(key, input.checked ? '1' : '0').then((ok) => {
        if (!ok) input.checked = !input.checked;
      });
    });
    wrap.appendChild(input);
    wrap.appendChild(slider);
    row.appendChild(lbl);
    row.appendChild(wrap);
    return row;
  }

  function renderActionRow(key, label, actionLabel) {
    const row = document.createElement('div');
    row.className = 'setting-row';
    const lbl = document.createElement('label');
    lbl.textContent = label;
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-danger';
    btn.textContent = actionLabel || '执行';
    btn.addEventListener('click', () => {
      if (!confirm('确定要' + label + '？电视将重启。')) return;
      saveSetting(key, '1');
    });
    row.appendChild(lbl);
    row.appendChild(btn);
    return row;
  }

  function renderSettingsForm(data) {
    const form = $('#settingsForm');
    form.innerHTML = '';
    const values = data.values || {};
    const lists = data.lists || {};
    SETTING_SECTIONS.forEach((section) => {
      const fold = document.createElement('details');
      fold.className = 'setting-fold';
      if (section.open) fold.open = true;
      const summary = document.createElement('summary');
      summary.textContent = section.title;
      const body = document.createElement('div');
      body.className = 'setting-fold-body';
      section.fields.forEach((field) => {
        let row = null;
        switch (field.type) {
          case 'text': row = renderTextRow(field.key, field.label, values[field.key]); break;
          case 'toggle': row = renderToggleRow(field.key, field.label, values[field.key]); break;
          case 'action': row = renderActionRow(field.key, field.label, field.actionLabel); break;
          case 'home_sources': row = renderSelectRow(field.key, field.label, lists.home_sources, values.home_api, 'key', 'name'); break;
          case 'home_rec': row = renderSelectRow(field.key, field.label, lists.home_rec_options, values.home_rec); break;
          case 'search_view': row = renderSelectRow(field.key, field.label, lists.search_view_options, values.search_view); break;
          case 'history_num': row = renderSelectRow(field.key, field.label, lists.history_num_options, values.history_num); break;
          case 'play_types': row = renderSelectRow(field.key, field.label, lists.play_types, values.play_type); break;
          case 'play_renders': row = renderSelectRow(field.key, field.label, lists.play_renders, values.play_render); break;
          case 'play_scales': row = renderSelectRow(field.key, field.label, lists.play_scales, values.play_scale); break;
          case 'ijk_codecs': row = renderSelectRow(field.key, field.label, lists.ijk_codecs, values.ijk_codec, null, null); break;
          case 'doh': row = renderSelectRow(field.key, field.label, lists.doh, values.doh_url); break;
          default: break;
        }
        if (row) body.appendChild(row);
      });
      fold.appendChild(summary);
      fold.appendChild(body);
      form.appendChild(fold);
    });
  }

  async function loadSettings(silent) {
    if (!authToken) return;
    const loading = $('#settingsLoading');
    const form = $('#settingsForm');
    if (!silent) {
      loading.hidden = false;
      form.hidden = true;
    }
    try {
      const data = await apiFetch('/api/settings');
      renderSettingsForm(data);
      loading.hidden = true;
      form.hidden = false;
    } catch (e) {
      loading.hidden = false;
      form.hidden = true;
      loading.innerHTML = '<span>' + (e.message || '加载失败，请确认电视已登录同一账号') + '</span>';
    }
  }

  async function doSearch() {
    const word = $('#searchInput').value.trim();
    if (!word) { showToast('请输入关键词'); return; }
    const r = await sendCommand({ type: 'search', word: word });
    if (r && r.ok) showToast('已搜索：' + word);
  }

  async function doPush() {
    const url = $('#pushInput').value.trim();
    if (!url) { showToast('请输入地址'); return; }
    const r = await sendCommand({ type: 'push', url: url });
    if (r && r.ok) showToast('已推送');
  }

  function parseApiLine(line) {
    const raw = String(line || '');
    const idx = raw.indexOf('\t');
    if (idx > 0) {
      return { name: raw.substring(0, idx).trim(), url: raw.substring(idx + 1).trim() };
    }
    return { name: '', url: raw.trim() };
  }

  function getCustomLines() {
    try {
      const data = JSON.parse(localStorage.getItem(CUSTOM_LINES_KEY) || '[]');
      const list = Array.isArray(data) ? data : [];
      const presetKeys = new Set(presetLines.map((p) => normalizeConfigUrl(p.url)));
      return dedupeLinesByUrl(list).filter((l) => !presetKeys.has(normalizeConfigUrl(l.url)));
    } catch (e) {
      return [];
    }
  }

  function saveCustomLines(lines) {
    localStorage.setItem(CUSTOM_LINES_KEY, JSON.stringify(dedupeLinesByUrl(lines)));
  }

  function normalizeConfigUrl(url) {
    const raw = String(url || '').trim();
    if (!raw) return '';
    try {
      const u = new URL(raw);
      let pathPart = u.pathname || '/';
      if (pathPart.length > 1) pathPart = pathPart.replace(/\/+$/, '');
      return u.protocol + '//' + u.host + pathPart + (u.search || '');
    } catch (e) {
      return raw.replace(/\/+$/, '');
    }
  }

  function dedupeLinesByUrl(lines) {
    const map = new Map();
    (lines || []).forEach((line) => {
      const key = normalizeConfigUrl(line.url);
      if (!key) return;
      const item = Object.assign({}, line, { url: key });
      if (!map.has(key)) map.set(key, item);
    });
    return Array.from(map.values());
  }

  function isUrlKnown(url) {
    const key = normalizeConfigUrl(url);
    if (!key) return false;
    if (presetLines.some((p) => normalizeConfigUrl(p.url) === key)) return true;
    if (excludedLines.some((p) => normalizeConfigUrl(p.url) === key)) return true;
    return getCustomLines().some((p) => normalizeConfigUrl(p.url) === key);
  }

  function shortenUrl(url, max) {
    const s = String(url || '');
    if (s.length <= (max || 56)) return s;
    return s.substring(0, (max || 56) - 3) + '…';
  }

  function formatTestMeta(result) {
    if (!result) return '';
    if (!result.ok) return result.error || '测试失败';
    if (result.type === 'index') {
      let msg = '多仓索引 · ' + result.childCount + ' 条子线路';
      if (result.title) msg = result.title + ' · ' + msg;
      return msg;
    }
    if (result.type === 'full') {
      const parts = [];
      if (result.sites) parts.push(result.sites + ' 站点');
      if (result.lives) parts.push(result.lives + ' 直播');
      if (result.parses) parts.push(result.parses + ' 解析');
      if (result.hasSpider) parts.push('含 spider');
      let msg = parts.join(' · ');
      if (result.bytes) msg += ' · ' + Math.round(result.bytes / 1024) + ' KB';
      return msg;
    }
    return '可用';
  }

  function badgeForResult(result, testing) {
    if (testing) return { text: '测试中', cls: 'pending' };
    if (!result) return { text: '未测', cls: 'idle' };
    if (result.ok) return { text: '可用', cls: 'ok' };
    return { text: '失败', cls: 'fail' };
  }

  async function testConfigUrl(url) {
    if (configTestCache[url]) return configTestCache[url];
    const data = await apiFetch('/api/config/test', { method: 'POST', body: { url: url } });
    configTestCache[url] = data;
    return data;
  }

  function isCurrentConfigUrl(url) {
    if (!currentApiUrl || !url) return false;
    return normalizeConfigUrl(currentApiUrl) === normalizeConfigUrl(url);
  }

  function applyCurrentConfigUrl(url) {
    currentApiUrl = normalizeConfigUrl(url);
    pendingSwitchUrl = currentApiUrl;
    pendingSwitchAt = Date.now();
    renderCurrentHero();
    renderConfigCatalog();
    renderTvLinesFromCache();
  }

  function mergeCurrentApiFromSettings(values) {
    const fromTv = values && values.api_url ? normalizeConfigUrl(String(values.api_url)) : '';
    const recentSwitch = pendingSwitchUrl && (Date.now() - pendingSwitchAt < 45000);
    if (fromTv) {
      if (!recentSwitch || fromTv === pendingSwitchUrl) {
        currentApiUrl = fromTv;
        if (fromTv === pendingSwitchUrl) {
          pendingSwitchUrl = '';
          pendingSwitchAt = 0;
        }
      } else {
        currentApiUrl = pendingSwitchUrl;
      }
    } else if (recentSwitch) {
      currentApiUrl = pendingSwitchUrl;
    }
  }

  function renderTvLinesFromCache() {
    const tvSection = $('#configsTvSection');
    const tvList = $('#configsTvLinesList');
    if (!tvSection || !tvList || configAvailFilter !== 'available') return;
    const apiLines = configsTabLoaded._apiLines || [];
    if (!apiLines.length) {
      tvSection.hidden = true;
      return;
    }
    tvSection.hidden = false;
    tvList.innerHTML = '';
    const presetKeys = new Set(presetLines.map((p) => normalizeConfigUrl(p.url)));
    dedupeLinesByUrl(apiLines.map(parseApiLine).filter((l) => l.url))
      .filter((line) => !presetKeys.has(normalizeConfigUrl(line.url)))
      .forEach((line) => {
        renderConfigRow(Object.assign({ type: 'full', group: '电视' }, line), tvList);
      });
    if (!tvList.children.length) tvSection.hidden = true;
  }

  async function switchConfigUrl(url, name) {
    const normUrl = normalizeConfigUrl(url);
    const label = name || shortenUrl(normUrl, 40);
    if (!confirm('切换为「' + label + '」？\n\nTVBox 将重启并加载新配置。')) return;
    const r1 = await saveSetting('api_url', normUrl);
    if (!r1) return;
    await saveSetting('live_api_url', normUrl);
    applyCurrentConfigUrl(normUrl);
    showToast('已切换配置，电视正在重启…');
    setTimeout(() => loadConfigsTab(true), 4000);
  }

  function formatPresetStats(line) {
    if (line.type === 'index') return '多仓索引';
    const parts = [];
    if (line.sites && line.sites !== '-') {
      const s = String(line.sites).replace(/^urls=/, '');
      parts.push(s.indexOf('=') >= 0 ? s : s + ' 站点');
    }
    if (line.lives && line.lives !== '-') parts.push(line.lives + ' 直播');
    if (line.parses && line.parses !== '-') parts.push(line.parses + ' 解析');
    return parts.join(' · ') || '完整配置';
  }

  function presetTypeLabel(type) {
    if (type === 'index') return '索引';
    return '完整';
  }

  function lineWarehouse(line) {
    if (line.warehouse === 'multi' || line.warehouse === 'single') return line.warehouse;
    if (line.type === 'index') return 'multi';
    return 'single';
  }

  function excludedAsLine(item) {
    return {
      name: item.name || shortenUrl(item.url, 32),
      url: item.url,
      type: 'unknown',
      availability: 'unavailable',
      warehouse: item.warehouse || 'single',
      reason: item.reason || '不可用',
      verified: 'FAIL'
    };
  }

  function matchesConfigFilter(line) {
    if (!configSearchQuery) return true;
    const q = configSearchQuery.toLowerCase();
    return (line.name || '').toLowerCase().indexOf(q) >= 0 ||
      (line.url || '').toLowerCase().indexOf(q) >= 0 ||
      (line.reason || '').toLowerCase().indexOf(q) >= 0;
  }

  function getCatalogSourceLines() {
    if (configAvailFilter === 'unavailable') {
      return dedupeLinesByUrl(excludedLines.map(excludedAsLine));
    }
    let lines = dedupeLinesByUrl(presetLines.slice());
    if (configWarehouseFilter === 'multi') {
      lines = lines.filter((l) => lineWarehouse(l) === 'multi');
    } else if (configWarehouseFilter === 'single') {
      lines = lines.filter((l) => lineWarehouse(l) === 'single');
    }
    return lines;
  }

  function getFilteredPresets() {
    return getCatalogSourceLines().filter(matchesConfigFilter);
  }

  function renderSegControl(container, options, activeValue, onChange) {
    if (!container) return;
    container.innerHTML = '';
    options.forEach((opt) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'cfg-seg-btn' + (activeValue === opt.value ? ' active' : '');
      btn.setAttribute('role', 'tab');
      btn.setAttribute('aria-selected', activeValue === opt.value ? 'true' : 'false');
      btn.textContent = opt.label;
      btn.addEventListener('click', () => onChange(opt.value));
      container.appendChild(btn);
    });
  }

  function updateCatalogChrome() {
    const title = $('#configsCatalogTitle');
    const desc = $('#configsCatalogDesc');
    const batch = $('#btnBatchTest');
    const warehouseRow = $('#configWarehouseRow');
    const tvSection = $('#configsTvSection');
    const excludedFold = $('#configsExcludedFold');

    if (warehouseRow) warehouseRow.hidden = configAvailFilter === 'unavailable';
    if (tvSection && configAvailFilter === 'unavailable') tvSection.hidden = true;
    if (excludedFold) excludedFold.hidden = configAvailFilter === 'unavailable';

    if (batch) {
      batch.hidden = configAvailFilter === 'unavailable';
      batch.disabled = configAvailFilter === 'unavailable';
    }

    if (title && desc) {
      if (configAvailFilter === 'unavailable') {
        title.textContent = '不可用配置';
        desc.textContent = '检测失败或 JSON 无效，不建议作为 TVBox 配置地址';
      } else if (configWarehouseFilter === 'multi') {
        title.textContent = '多仓配置';
        desc.textContent = '索引型配置，加载后可在电视端切换子线路';
      } else if (configWarehouseFilter === 'single') {
        title.textContent = '单仓配置';
        desc.textContent = '完整配置，直接包含站点 / 直播 / 解析';
      } else {
        title.textContent = '全部可用配置';
        desc.textContent = '已验证可用的 JSON 配置地址';
      }
    }
  }

  function renderClassifyFilters() {
    renderSegControl($('#configAvailFilter'), [
      { value: 'available', label: '可用 ' + (presetCounts.available || presetLines.length) },
      { value: 'unavailable', label: '不可用 ' + (presetCounts.unavailable || excludedLines.length) }
    ], configAvailFilter, (value) => {
      configAvailFilter = value;
      if (value === 'unavailable') configWarehouseFilter = 'all';
      renderClassifyFilters();
      updateCatalogChrome();
      renderConfigCatalog();
    });

    const multiN = presetCounts.multi || presetLines.filter((l) => lineWarehouse(l) === 'multi').length;
    const singleN = presetCounts.single || presetLines.filter((l) => lineWarehouse(l) === 'single').length;
    renderSegControl($('#configWarehouseFilter'), [
      { value: 'all', label: '全部 ' + (presetCounts.available || presetLines.length) },
      { value: 'multi', label: '多仓 ' + multiN },
      { value: 'single', label: '单仓 ' + singleN }
    ], configWarehouseFilter, (value) => {
      configWarehouseFilter = value;
      renderClassifyFilters();
      updateCatalogChrome();
      renderConfigCatalog();
    });

    updateCatalogChrome();
  }

  function renderFilterChips() {
    renderClassifyFilters();
  }

  function updateConfigStats() {
    const el = $('#configStats');
    if (!el) return;
    const shown = getFilteredPresets().length;
    const total = getCatalogSourceLines().length;
    if (configAvailFilter === 'unavailable') {
      el.textContent = '显示 ' + shown + ' / ' + total + ' 条不可用';
      return;
    }
    let msg = '显示 ' + shown + ' / ' + total + ' 条';
    if (configWarehouseFilter === 'multi') msg += '多仓';
    else if (configWarehouseFilter === 'single') msg += '单仓';
    else msg += '可用';
    el.textContent = msg;
  }

  function renderConfigRow(line, container, options) {
    const opts = options || {};
    const url = line.url;
    const name = line.name || shortenUrl(url, 36);
    const isUnavailable = line.availability === 'unavailable' || configAvailFilter === 'unavailable';
    const cached = !isUnavailable ? configTestCache[url] : null;
    const liveBadge = isUnavailable
      ? { text: '不可用', cls: 'fail' }
      : (cached ? badgeForResult(cached) : (line.verified === 'OK' || line.verified === 'WARN' || line.verified === 'local'
        ? { text: '已验证', cls: 'ok' } : { text: '未测', cls: 'idle' }));
    const isActive = !isUnavailable && isCurrentConfigUrl(url);
    const wh = lineWarehouse(line);

    const row = document.createElement('article');
    row.className = 'cfg-row' + (isActive ? ' is-active' : '') + (isUnavailable ? ' is-unavailable' : '');
    row.dataset.url = url;

    const main = document.createElement('div');
    main.className = 'cfg-row-main';

    const type = document.createElement('span');
    type.className = 'cfg-type' + (isUnavailable ? ' cfg-type-bad' : (line.type === 'index' ? ' cfg-type-index' : ''));
    type.textContent = isUnavailable ? '失效' : (wh === 'multi' ? '多仓' : '单仓');

    const text = document.createElement('div');
    text.className = 'cfg-row-text';
    const title = document.createElement('h4');
    title.className = 'cfg-row-name';
    title.textContent = name;
    const stats = document.createElement('p');
    stats.className = 'cfg-row-stats';
    stats.textContent = isUnavailable
      ? (line.reason || '不可用')
      : (cached && cached.ok ? formatTestMeta(cached) : formatPresetStats(line));
    text.appendChild(title);
    text.appendChild(stats);

    const badgeEl = document.createElement('span');
    badgeEl.className = 'cfg-badge ' + liveBadge.cls;
    badgeEl.textContent = isActive ? '当前' : liveBadge.text;

    main.appendChild(type);
    main.appendChild(text);
    main.appendChild(badgeEl);

    const urlEl = document.createElement('p');
    urlEl.className = 'cfg-row-url';
    urlEl.textContent = url;
    urlEl.title = url;

    const actions = document.createElement('div');
    actions.className = 'cfg-row-actions';

    if (!isUnavailable) {
      const btnTest = document.createElement('button');
      btnTest.type = 'button';
      btnTest.className = 'cfg-btn-ghost';
      btnTest.textContent = '测试';
      btnTest.addEventListener('click', async (e) => {
        e.preventDefault();
        e.stopPropagation();
        btnTest.disabled = true;
        btnTest.textContent = '…';
        badgeEl.className = 'cfg-badge pending';
        badgeEl.textContent = '测试中';
        try {
          delete configTestCache[url];
          const result = await testConfigUrl(url);
          badgeEl.className = 'cfg-badge ' + badgeForResult(result).cls;
          badgeEl.textContent = badgeForResult(result).text;
          stats.textContent = formatTestMeta(result);
          if (result.ok && result.type === 'index' && result.children && result.children.length) {
            renderIndexChildren(row, result.children);
          }
          if (!result.ok) showToast(result.error || '测试失败');
          else showToast(result.type === 'index' ? '多仓索引可用' : '配置可用');
        } catch (err) {
          badgeEl.className = 'cfg-badge fail';
          badgeEl.textContent = '失败';
          showToast(err.message || '测试失败');
        } finally {
          btnTest.disabled = false;
          btnTest.textContent = '测试';
        }
      });

      const btnSwitch = document.createElement('button');
      btnSwitch.type = 'button';
      btnSwitch.className = 'cfg-btn-primary';
      btnSwitch.textContent = isActive ? '使用中' : '切换';
      btnSwitch.disabled = !!isActive;
      btnSwitch.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        switchConfigUrl(url, name);
      });

      actions.appendChild(btnTest);
      actions.appendChild(btnSwitch);

      if (wh === 'multi' && cached && cached.ok && cached.type === 'index' && cached.children && cached.children.length) {
        renderIndexChildren(row, cached.children);
      }
    } else {
      const hint = document.createElement('span');
      hint.className = 'cfg-row-unavail-hint';
      hint.textContent = '不可切换';
      actions.appendChild(hint);
    }

    if (opts.onRemove) {
      const btnDel = document.createElement('button');
      btnDel.type = 'button';
      btnDel.className = 'cfg-btn-ghost cfg-btn-danger';
      btnDel.textContent = '删除';
      btnDel.addEventListener('click', opts.onRemove);
      actions.appendChild(btnDel);
    }

    row.appendChild(main);
    row.appendChild(urlEl);
    row.appendChild(actions);
    container.appendChild(row);
  }

  function renderIndexChildren(row, children) {
    let wrap = row.querySelector('.cfg-children');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'cfg-children';
      row.appendChild(wrap);
    }
    wrap.innerHTML = '';
    const title = document.createElement('p');
    title.className = 'cfg-children-title';
    title.textContent = '子线路（' + children.length + '）';
    wrap.appendChild(title);

    children.forEach((child) => {
      const item = document.createElement('div');
      item.className = 'cfg-child-item';
      const label = document.createElement('span');
      label.className = 'cfg-child-name';
      label.textContent = child.name || shortenUrl(child.url, 40);
      label.title = child.url;

      const acts = document.createElement('div');
      acts.className = 'cfg-child-actions';

      const btnSwitch = document.createElement('button');
      btnSwitch.type = 'button';
      btnSwitch.className = 'cfg-child-btn';
      btnSwitch.textContent = '切换';
      btnSwitch.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        switchConfigUrl(child.url, child.name);
      });

      acts.appendChild(btnSwitch);
      item.appendChild(label);
      item.appendChild(acts);
      wrap.appendChild(item);
    });
  }

  function renderConfigCatalog() {
    const catalog = $('#configsCatalog');
    const empty = $('#configsEmpty');
    if (!catalog) return;
    catalog.innerHTML = '';
    const lines = getFilteredPresets();
    if (empty) empty.hidden = lines.length > 0;
    lines.forEach((line) => renderConfigRow(line, catalog));
    updateConfigStats();
  }

  function renderExcludedList() {
    const list = $('#configsExcludedList');
    const countEl = $('#excludedCount');
    if (countEl) countEl.textContent = String(excludedLines.length);
    if (!list) return;
    list.innerHTML = '';
    excludedLines.forEach((item) => {
      const row = document.createElement('div');
      row.className = 'cfg-excluded-row';
      row.innerHTML =
        '<span class="cfg-excluded-name">' + escapeHtml(item.name) + '</span>' +
        '<span class="cfg-excluded-reason">' + escapeHtml(item.reason || '不可用') + '</span>' +
        '<code class="cfg-excluded-url">' + escapeHtml(shortenUrl(item.url, 48)) + '</code>';
      list.appendChild(row);
    });
  }

  function escapeHtml(s) {
    return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  async function batchTestFiltered() {
    if (configAvailFilter === 'unavailable') {
      showToast('不可用配置无需测试');
      return;
    }
    const lines = getFilteredPresets();
    if (!lines.length) { showToast('没有可测试的线路'); return; }
    const btn = $('#btnBatchTest');
    if (btn) { btn.disabled = true; btn.textContent = '测试中…'; }
    let ok = 0;
    let fail = 0;
    for (let i = 0; i < lines.length; i++) {
      try {
        delete configTestCache[lines[i].url];
        const r = await testConfigUrl(lines[i].url);
        if (r.ok) ok++; else fail++;
      } catch (e) {
        fail++;
      }
      if (i % 5 === 4) renderConfigCatalog();
    }
    renderConfigCatalog();
    if (btn) { btn.disabled = false; btn.textContent = '测试筛选结果'; }
    showToast('批量完成：' + ok + ' 可用，' + fail + ' 失败');
  }

  function renderCurrentHero() {
    const urlEl = $('#configsCurrentUrl');
    if (!urlEl) return;
    if (!currentApiUrl) {
      urlEl.textContent = '（未设置或电视离线）';
      return;
    }
    const norm = normalizeConfigUrl(currentApiUrl);
    const match = presetLines.find((p) => normalizeConfigUrl(p.url) === norm);
    urlEl.textContent = match ? match.name + '\n' + norm : norm;
  }

  async function loadPresetLines() {
    try {
      const data = await apiFetch('/api/config/presets');
      if (data.presets && data.presets.length) presetLines = dedupeLinesByUrl(data.presets);
      if (data.excluded) excludedLines = dedupeLinesByUrl(data.excluded);
      presetCounts = {
        available: data.availableCount || presetLines.length,
        unavailable: data.unavailableCount || excludedLines.length,
        multi: data.multiCount || presetLines.filter((l) => lineWarehouse(l) === 'multi').length,
        single: data.singleCount || presetLines.filter((l) => lineWarehouse(l) === 'single').length
      };
    } catch (e) {
      presetLines = FALLBACK_PRESETS.slice();
      presetCounts = { available: presetLines.length, unavailable: 0, multi: 1, single: 1 };
    }
    return presetLines;
  }

  async function loadConfigsTab(silent) {
    const urlEl = $('#configsCurrentUrl');
    if (!silent && urlEl) urlEl.textContent = '读取中…';

    let settingsData = null;
    try {
      settingsData = await apiFetch('/api/settings');
      mergeCurrentApiFromSettings(settingsData.values);
    } catch (e) {
      /* keep previous */
    }

    await loadPresetLines();
    renderCurrentHero();

    const apiLines = settingsData && settingsData.lists && settingsData.lists.api_lines ? settingsData.lists.api_lines : [];
    configsTabLoaded._apiLines = apiLines;
    renderTvLinesFromCache();

    renderFilterChips();
    renderConfigCatalog();
    if (configAvailFilter !== 'unavailable') renderExcludedList();

    const customList = $('#configsCustomList');
    if (customList) {
      customList.innerHTML = '';
      const custom = getCustomLines();
      if (!custom.length) {
        customList.innerHTML = '<p class="cfg-empty-inline">暂无自定义线路</p>';
      } else {
        custom.forEach((line) => {
          renderConfigRow(Object.assign({ type: 'full', verified: 'local' }, line), customList, {
            onRemove: () => {
              saveCustomLines(getCustomLines().filter((l) => l.url !== line.url));
              loadConfigsTab(true);
            }
          });
        });
      }
    }

    configsTabLoaded._ready = true;
  }

  function bindConfigsTab() {
    const search = $('#configSearch');
    if (search) {
      search.addEventListener('input', () => {
        configSearchQuery = search.value.trim();
        renderConfigCatalog();
      });
    }
    const batch = $('#btnBatchTest');
    if (batch) batch.addEventListener('click', batchTestFiltered);
  }

  function addCustomLine() {
    const nameInput = $('#customLineName');
    const urlInput = $('#customLineUrl');
    const name = (nameInput && nameInput.value || '').trim();
    const url = normalizeConfigUrl((urlInput && urlInput.value || '').trim());
    if (!url) { showToast('请输入 JSON 地址'); return; }
    if (!/^https?:\/\//i.test(url)) { showToast('地址需以 http:// 或 https:// 开头'); return; }
    if (isUrlKnown(url)) { showToast('该地址已在列表中'); return; }
    const lines = getCustomLines();
    lines.unshift({ name: name || shortenUrl(url, 28), url: url, group: '自定义' });
    saveCustomLines(lines);
    if (nameInput) nameInput.value = '';
    if (urlInput) urlInput.value = '';
    loadConfigsTab(true);
    showToast('已添加');
  }

  function setupServerField() {
    const defaultServer = getDefaultServer();
    $('#serverInput').value = localStorage.getItem(SERVER_KEY) || defaultServer;
    if (serverAdvanced && (location.port === '3080' || location.origin === defaultServer)) {
      serverAdvanced.open = false;
    } else if (serverAdvanced) {
      serverAdvanced.open = true;
    }
  }

  async function initApp() {
    setupServerField();
    authToken = localStorage.getItem(TOKEN_KEY) || '';
    apiBase = normalizeServer($('#serverInput').value);

    if (authToken) {
      try {
        await refreshStatus();
        startPoll();
      } catch (e) {
        logout();
      }
    } else {
      setLoggedIn(false);
    }
  }

  $('#tabLogin').addEventListener('click', () => {
    isRegister = false;
    $('#tabLogin').classList.add('active');
    $('#tabRegister').classList.remove('active');
    $('#btnAuth').textContent = '登录';
    $('#tabLogin').setAttribute('aria-selected', 'true');
    $('#tabRegister').setAttribute('aria-selected', 'false');
  });
  $('#tabRegister').addEventListener('click', () => {
    isRegister = true;
    $('#tabRegister').classList.add('active');
    $('#tabLogin').classList.remove('active');
    $('#btnAuth').textContent = '注册';
    $('#tabRegister').setAttribute('aria-selected', 'true');
    $('#tabLogin').setAttribute('aria-selected', 'false');
  });
  $('#btnAuth').addEventListener('click', doAuth);
  $('#btnReconnect').addEventListener('click', (e) => {
    e.stopPropagation();
    if (authToken) {
      const name = localStorage.getItem(USER_KEY) || '';
      toggleUserMenu(name);
    } else {
      closeUserMenu();
      authPanel.hidden = false;
    }
  });
  $('#btnLogout').addEventListener('click', (e) => {
    e.stopPropagation();
    logout();
  });
  document.addEventListener('click', (e) => {
    const wrap = document.querySelector('.topbar-user-wrap');
    if (wrap && !wrap.contains(e.target)) closeUserMenu();
  });
  $('#btnSearch').addEventListener('click', doSearch);
  $('#btnPush').addEventListener('click', doPush);
  const btnAddCustomLine = $('#btnAddCustomLine');
  if (btnAddCustomLine) btnAddCustomLine.addEventListener('click', addCustomLine);
  const customLineUrl = $('#customLineUrl');
  if (customLineUrl) {
    customLineUrl.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); addCustomLine(); }
    });
  }
  $('#searchInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); doSearch(); }
  });
  $('#pushInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); doPush(); }
  });

  bindKeyButtons();
  bindNav();
  bindConfigsTab();
  initApp();
})();
