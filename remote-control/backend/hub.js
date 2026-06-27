const { EventEmitter } = require('events');

/** userId -> { ws, deviceUuid, activity } */
const devices = new Map();
/** userId -> { ws } web clients */
const clients = new Map();
/** requestId -> { resolve, reject, timer } */
const pendingSettings = new Map();

const events = new EventEmitter();

function setDevice(userId, ws, meta) {
  devices.set(userId, { ws, ...meta });
  events.emit('device_change', userId);
  broadcastToClients(userId, { type: 'device_online', online: true, activity: meta.activity || '' });
}

function removeDevice(userId) {
  if (devices.has(userId)) {
    devices.delete(userId);
    events.emit('device_change', userId);
    broadcastToClients(userId, { type: 'device_online', online: false });
  }
}

function setClient(userId, ws) {
  clients.set(userId, ws);
}

function removeClient(userId) {
  clients.delete(userId);
}

function isDeviceOnline(userId) {
  const d = devices.get(userId);
  return !!(d && d.ws && d.ws.readyState === 1);
}

function getDeviceActivity(userId) {
  const d = devices.get(userId);
  return d ? (d.activity || '') : '';
}

function sendToDevice(userId, message) {
  const d = devices.get(userId);
  if (!d || !d.ws || d.ws.readyState !== 1) {
    return false;
  }
  d.ws.send(JSON.stringify(message));
  return true;
}

function broadcastToClients(userId, message) {
  const c = clients.get(userId);
  if (c && c.readyState === 1) {
    c.send(JSON.stringify(message));
  }
}

function updateActivity(userId, activity) {
  const d = devices.get(userId);
  if (d) {
    d.activity = activity;
    broadcastToClients(userId, { type: 'device_activity', activity });
  }
}

function requestSettings(userId, timeoutMs) {
  return new Promise((resolve, reject) => {
    const requestId = `${userId}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const timer = setTimeout(() => {
      pendingSettings.delete(requestId);
      reject(new Error('电视未响应设置请求'));
    }, timeoutMs || 8000);

    pendingSettings.set(requestId, { resolve, reject, timer });
    const ok = sendToDevice(userId, { type: 'get_settings', requestId });
    if (!ok) {
      clearTimeout(timer);
      pendingSettings.delete(requestId);
      reject(new Error('电视未在线'));
    }
  });
}

function resolveSettings(requestId, data) {
  const pending = pendingSettings.get(requestId);
  if (!pending) return false;
  clearTimeout(pending.timer);
  pendingSettings.delete(requestId);
  pending.resolve(data);
  return true;
}

module.exports = {
  devices,
  events,
  setDevice,
  removeDevice,
  setClient,
  removeClient,
  isDeviceOnline,
  getDeviceActivity,
  sendToDevice,
  updateActivity,
  requestSettings,
  resolveSettings
};
