(function () {
  'use strict';

  const TOKEN_KEY = 'tvbox_cloud_token';
  const SERVER_KEY = 'tvbox_cloud_server';
  const USER_KEY = 'tvbox_cloud_user';

  let apiBase = '';
  let authToken = '';
  let authMode = true;
  let pollTimer = null;
  let isRegister = false;

  const $ = (sel) => document.querySelector(sel);
  const authPanel = $('#authPanel');
  const connectPanel = $('#connectPanel');
  const remotePanel = $('#remotePanel');
  const bottomNav = $('#bottomNav');
  const liveDot = $('#liveDot');
  const headerSub = $('#headerSub');
  const toastEl = $('#toast');

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

  function setLoggedIn(loggedIn, username, online, activity) {
    authPanel.hidden = loggedIn;
    connectPanel.hidden = true;
    remotePanel.hidden = !loggedIn;
    bottomNav.hidden = !loggedIn;
    liveDot.className = 'live-dot ' + (online ? 'on' : 'off');
    if (loggedIn) {
      headerSub.textContent = (online ? (activity || '电视在线') : '电视离线') + ' · ' + (username || '');
    } else {
      headerSub.textContent = '未登录';
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
    document.querySelectorAll('.nav-item').forEach((item) => {
      item.addEventListener('click', () => {
        document.querySelectorAll('.nav-item').forEach((n) => n.classList.remove('active'));
        document.querySelectorAll('.tab-panel').forEach((p) => p.classList.remove('active'));
        item.classList.add('active');
        $('#tab-' + item.dataset.tab).classList.add('active');
        if (item.dataset.tab === 'settings') loadSettings();
      });
    });
  }

  const SETTING_SECTIONS = [
    { title: '接口配置', fields: [
      { key: 'api_url', label: '点播配置地址', type: 'text' },
      { key: 'live_api_url', label: '直播配置地址', type: 'text' },
      { key: 'danmu_api', label: '弹幕接口地址', type: 'text' },
      { key: 'api_history', label: '快速切换历史配置', type: 'api_history' },
      { key: 'api_lines', label: '配置线路', type: 'api_lines' }
    ]},
    { title: '首页', fields: [
      { key: 'home_api', label: '首页站源', type: 'home_sources' },
      { key: 'home_rec', label: '首页推荐', type: 'home_rec' },
      { key: 'home_rec_style', label: '首页多行展示', type: 'toggle' },
      { key: 'default_load_live', label: '下次进入直播页', type: 'toggle' }
    ]},
    { title: '搜索', fields: [
      { key: 'fast_search_mode', label: '聚合搜索', type: 'toggle' },
      { key: 'search_view', label: '搜索展示', type: 'search_view' },
      { key: 'history_num', label: '历史记录条数', type: 'history_num' }
    ]},
    { title: '播放', fields: [
      { key: 'play_type', label: '播放器', type: 'play_types' },
      { key: 'play_render', label: '渲染方式', type: 'play_renders' },
      { key: 'play_scale', label: '画面缩放', type: 'play_scales' },
      { key: 'ijk_codec', label: 'IJK 解码', type: 'ijk_codecs' },
      { key: 'm3u8_purify', label: '去广告', type: 'toggle' },
      { key: 'auto_switch_line', label: '自动换线', type: 'toggle' },
      { key: 'show_preview', label: '窗口预览', type: 'toggle' },
      { key: 'ijk_cache_play', label: 'IJK 缓存', type: 'toggle' }
    ]},
    { title: '弹幕与网络', fields: [
      { key: 'danmu_open', label: '弹幕', type: 'toggle' },
      { key: 'doh_url', label: '安全 DNS', type: 'doh' },
      { key: 'parse_webview', label: '嗅探用系统 WebView', type: 'toggle' }
    ]},
    { title: '界面布局', fields: [
      { key: 'ui_card_width', label: '卡片宽度 (mm)', type: 'text' },
      { key: 'ui_card_height', label: '卡片高度 (mm)', type: 'text' },
      { key: 'ui_grid_span', label: '网格列数 (0=自动)', type: 'text' },
      { key: 'ui_grid_spacing', label: '卡片间距 (mm)', type: 'text' },
      { key: 'ui_item_margin', label: '卡片外边距 (mm)', type: 'text' },
      { key: 'ui_focus_scale', label: '选中放大 (%)', type: 'text' }
    ]},
    { title: '维护', fields: [
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

  function renderSettingsForm(data) {
    const form = $('#settingsForm');
    form.innerHTML = '';
    const values = data.values || {};
    const lists = data.lists || {};
    SETTING_SECTIONS.forEach((section) => {
      const group = document.createElement('div');
      group.className = 'setting-group';
      group.innerHTML = '<h3>' + section.title + '</h3>';
      section.fields.forEach((field) => {
        let row = null;
        switch (field.type) {
          case 'text': row = renderTextRow(field.key, field.label, values[field.key]); break;
          case 'toggle': row = renderToggleRow(field.key, field.label, values[field.key]); break;
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
        if (row) group.appendChild(row);
      });
      form.appendChild(group);
    });
  }

  async function loadSettings(silent) {
    if (!authToken) return;
    const loading = $('#settingsLoading');
    const form = $('#settingsForm');
    if (!silent) {
      loading.hidden = false;
      form.hidden = true;
      loading.textContent = '正在从电视拉取设置…';
    }
    try {
      const data = await apiFetch('/api/settings');
      renderSettingsForm(data);
      loading.hidden = true;
      form.hidden = false;
    } catch (e) {
      loading.textContent = e.message || '加载失败，请确认电视已登录同一账号';
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

  async function initApp() {
    $('#serverInput').value = localStorage.getItem(SERVER_KEY) || getDefaultServer();
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
  });
  $('#tabRegister').addEventListener('click', () => {
    isRegister = true;
    $('#tabRegister').classList.add('active');
    $('#tabLogin').classList.remove('active');
    $('#btnAuth').textContent = '注册';
  });
  $('#btnAuth').addEventListener('click', doAuth);
  $('#btnReconnect').addEventListener('click', () => {
    if (authToken) logout();
    else authPanel.hidden = false;
  });
  $('#btnSearch').addEventListener('click', doSearch);
  $('#btnPush').addEventListener('click', doPush);

  bindKeyButtons();
  bindNav();
  initApp();
})();
