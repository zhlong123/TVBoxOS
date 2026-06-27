/**
 * TVBox 云遥控后端
 * - 账户注册/登录（JWT）
 * - 一账号绑定一台电视（user_id UNIQUE on devices）
 * - WebSocket：电视端 device / 手机端 client
 * - HTTP API：前端遥控与设置
 */

const http = require('http');
const path = require('path');
const express = require('express');
const cors = require('cors');
const { WebSocketServer } = require('ws');
const { v4: uuidv4 } = require('uuid');

const store = require('./db');
const auth = require('./auth');
const hub = require('./hub');

const PORT = Number(process.env.PORT || 3080);
const frontendDir = path.join(__dirname, '..', 'frontend');

const app = express();
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.use(express.static(frontendDir));

// ─── Auth ───────────────────────────────────────────────
app.post('/api/auth/register', (req, res) => {
  const username = (req.body.username || '').trim();
  const password = req.body.password || '';
  if (username.length < 3) {
    return res.status(400).json({ ok: false, message: '用户名至少 3 个字符' });
  }
  if (password.length < 6) {
    return res.status(400).json({ ok: false, message: '密码至少 6 位' });
  }
  if (store.findUserByUsername(username)) {
    return res.status(409).json({ ok: false, message: '用户名已存在' });
  }
  const user = store.createUser(username, auth.hashPassword(password));
  const token = auth.signToken(user);
  res.json({ ok: true, token, user: { id: user.id, username: user.username } });
});

app.post('/api/auth/login', (req, res) => {
  const username = (req.body.username || '').trim();
  const password = req.body.password || '';
  const row = store.findUserByUsername(username);
  if (!row || !auth.verifyPassword(password, row.password_hash)) {
    return res.status(401).json({ ok: false, message: '用户名或密码错误' });
  }
  const user = store.findUserById(row.id);
  const token = auth.signToken(user);
  res.json({ ok: true, token, user: { id: user.id, username: user.username } });
});

app.get('/api/me', auth.authMiddleware, (req, res) => {
  const user = store.findUserById(req.user.sub);
  const device = store.getDeviceByUserId(req.user.sub);
  res.json({
    ok: true,
    user,
    device: device ? {
      name: device.device_name,
      lastActivity: device.last_activity,
      lastSeen: device.last_seen
    } : null,
    online: hub.isDeviceOnline(req.user.sub)
  });
});

// ─── Device status ──────────────────────────────────────
app.get('/api/device/status', auth.authMiddleware, (req, res) => {
  const userId = req.user.sub;
  res.json({
    ok: true,
    online: hub.isDeviceOnline(userId),
    activity: hub.getDeviceActivity(userId) || store.getDeviceByUserId(userId)?.last_activity || ''
  });
});

// ─── Commands ───────────────────────────────────────────
app.post('/api/command', auth.authMiddleware, (req, res) => {
  const userId = req.user.sub;
  const body = req.body || {};
  const type = body.type || body.do;

  if (!type) {
    return res.status(400).json({ ok: false, message: '缺少 type' });
  }

  if (!hub.isDeviceOnline(userId)) {
    return res.status(503).json({ ok: false, message: '电视未在线，请先在 TVBox 设置里登录云账号' });
  }

  let msg = { type };
  switch (type) {
    case 'key':
      msg.key = body.key;
      break;
    case 'search':
      msg.word = body.word;
      break;
    case 'push':
      msg.url = body.url;
      break;
    case 'setting':
      msg.key = body.key;
      msg.value = body.value != null ? String(body.value) : '';
      break;
    default:
      return res.status(400).json({ ok: false, message: '未知指令: ' + type });
  }

  const sent = hub.sendToDevice(userId, msg);
  if (!sent) {
    return res.status(503).json({ ok: false, message: '发送失败，电视可能已断开' });
  }
  res.json({ ok: true, message: '已发送' });
});

// ─── Settings ───────────────────────────────────────────
app.get('/api/settings', auth.authMiddleware, async (req, res) => {
  const userId = req.user.sub;
  const cached = store.getSettings(userId);
  if (hub.isDeviceOnline(userId)) {
    try {
      const live = await hub.requestSettings(userId, 8000);
      if (live) {
        store.saveSettings(userId, live);
        return res.json(live);
      }
    } catch (e) {
      if (cached) return res.json({ ...cached, ok: true, stale: true });
      return res.status(503).json({ ok: false, message: e.message });
    }
  }
  if (cached) return res.json({ ...cached, ok: true, stale: true });
  return res.status(503).json({ ok: false, message: '电视未在线，暂无设置缓存' });
});

app.post('/api/settings', auth.authMiddleware, (req, res) => {
  const userId = req.user.sub;
  const { key, value } = req.body || {};
  if (!key) return res.status(400).json({ ok: false, message: '缺少 key' });
  if (!hub.isDeviceOnline(userId)) {
    return res.status(503).json({ ok: false, message: '电视未在线' });
  }
  hub.sendToDevice(userId, { type: 'setting', key, value: value != null ? String(value) : '' });
  res.json({ ok: true, message: '已发送到电视' });
});

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'tvbox-cloud', port: PORT });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

function parseAuthToken(url) {
  try {
    const u = new URL(url, 'http://localhost');
    const token = u.searchParams.get('token');
    if (token) return auth.verifyToken(token);
  } catch (e) { /* ignore */ }
  return null;
}

wss.on('connection', (ws, req) => {
  let userId = null;
  let role = null;
  let deviceUuid = null;

  const authFromQuery = parseAuthToken(req.url);
  if (authFromQuery) {
    userId = authFromQuery.sub;
  }

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch (e) {
      return;
    }

    if (msg.type === 'auth') {
      try {
        const payload = auth.verifyToken(msg.token);
        userId = payload.sub;
        role = msg.role;
        if (role === 'device') {
          deviceUuid = msg.deviceUuid || uuidv4();
          store.upsertDevice(userId, deviceUuid, msg.deviceName || 'TVBox');
          hub.setDevice(userId, ws, { deviceUuid, activity: msg.activity || '' });
          ws.send(JSON.stringify({ type: 'auth_ok', deviceUuid }));
        } else if (role === 'client') {
          hub.setClient(userId, ws);
          ws.send(JSON.stringify({
            type: 'auth_ok',
            online: hub.isDeviceOnline(userId),
            activity: hub.getDeviceActivity(userId)
          }));
        }
      } catch (e) {
        ws.send(JSON.stringify({ type: 'auth_fail', message: '登录无效' }));
        ws.close();
      }
      return;
    }

    if (!userId) return;

    if (role === 'device') {
      switch (msg.type) {
        case 'hello':
          hub.updateActivity(userId, msg.activity || '');
          store.touchDevice(userId, msg.activity || '');
          break;
        case 'settings':
          if (msg.requestId) {
            hub.resolveSettings(msg.requestId, msg.data);
          }
          if (msg.data) store.saveSettings(userId, msg.data);
          break;
        case 'setting_result':
          break;
        case 'pong':
          break;
        default:
          break;
      }
    }
  });

  ws.on('close', () => {
    if (userId && role === 'device') hub.removeDevice(userId);
    if (userId && role === 'client') hub.removeClient(userId);
  });

  ws.on('error', () => {
    if (userId && role === 'device') hub.removeDevice(userId);
  });

  // ping keepalive
  const pingIv = setInterval(() => {
    if (ws.readyState === 1) {
      ws.send(JSON.stringify({ type: 'ping' }));
    }
  }, 25000);
  ws.on('close', () => clearInterval(pingIv));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('');
  console.log('  TVBox Cloud Backend');
  console.log('  -------------------');
  console.log('  HTTP  http://0.0.0.0:' + PORT);
  console.log('  WS    ws://0.0.0.0:' + PORT + '/ws');
  console.log('  前端  http://localhost:' + PORT);
  console.log('  模拟器连本机: http://10.0.2.2:' + PORT);
  console.log('');
});
