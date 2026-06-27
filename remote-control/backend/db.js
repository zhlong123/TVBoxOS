const Database = require('better-sqlite3');
const path = require('path');
const fs = require('fs');

const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, 'tvbox.db'));

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
  CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE,
    device_uuid TEXT NOT NULL UNIQUE,
    device_name TEXT NOT NULL DEFAULT 'TVBox',
    last_activity TEXT DEFAULT '',
    settings_json TEXT DEFAULT '{}',
    last_seen TEXT,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
  );
`);

module.exports = {
  db,
  findUserByUsername(username) {
    return db.prepare('SELECT * FROM users WHERE username = ?').get(username);
  },
  findUserById(id) {
    return db.prepare('SELECT id, username, created_at FROM users WHERE id = ?').get(id);
  },
  createUser(username, passwordHash) {
    const info = db.prepare('INSERT INTO users (username, password_hash) VALUES (?, ?)').run(username, passwordHash);
    return this.findUserById(info.lastInsertRowid);
  },
  getDeviceByUserId(userId) {
    return db.prepare('SELECT * FROM devices WHERE user_id = ?').get(userId);
  },
  upsertDevice(userId, deviceUuid, deviceName) {
    const existing = this.getDeviceByUserId(userId);
    if (existing) {
      db.prepare(`
        UPDATE devices SET device_uuid = ?, device_name = ?, last_seen = datetime('now')
        WHERE user_id = ?
      `).run(deviceUuid, deviceName || existing.device_name, userId);
      return this.getDeviceByUserId(userId);
    }
    db.prepare(`
      INSERT INTO devices (user_id, device_uuid, device_name, last_seen)
      VALUES (?, ?, ?, datetime('now'))
    `).run(userId, deviceUuid, deviceName || 'TVBox');
    return this.getDeviceByUserId(userId);
  },
  touchDevice(userId, activity) {
    db.prepare(`
      UPDATE devices SET last_seen = datetime('now'), last_activity = ?
      WHERE user_id = ?
    `).run(activity || '', userId);
  },
  saveSettings(userId, settingsJson) {
    db.prepare('UPDATE devices SET settings_json = ?, last_seen = datetime(\'now\') WHERE user_id = ?')
      .run(typeof settingsJson === 'string' ? settingsJson : JSON.stringify(settingsJson), userId);
  },
  getSettings(userId) {
    const row = this.getDeviceByUserId(userId);
    if (!row || !row.settings_json) return null;
    try { return JSON.parse(row.settings_json); } catch (e) { return null; }
  }
};
