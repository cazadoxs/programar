-- Denuncias anónimas. reporter_hash = SHA-256(PEPPER + id aleatorio de la instalación).
CREATE TABLE IF NOT EXISTS reports (
  number TEXT NOT NULL,
  reporter_hash TEXT NOT NULL,
  category TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  -- SHA-256(PEPPER + día + IP): no recuperable. Sirve para que una misma red no
  -- cuente varias veces el mismo día para meter un número en la lista.
  ip_hash TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (number, reporter_hash)
);
CREATE INDEX IF NOT EXISTS reports_created ON reports(created_at);
CREATE INDEX IF NOT EXISTS reports_reporter ON reports(reporter_hash, created_at);

-- Límite diario por IP (hash con sal y fecha, nunca la IP en claro).
CREATE TABLE IF NOT EXISTS ip_usage (
  ip_day_hash TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  day TEXT NOT NULL
);

-- Números retirados a mano (p. ej. un particular denunciado por error o por venganza).
CREATE TABLE IF NOT EXISTS excluded (
  number TEXT PRIMARY KEY,
  note TEXT,
  created_at INTEGER NOT NULL
);

-- Caché de suscripciones verificadas con Google. Solo el hash del token.
CREATE TABLE IF NOT EXISTS subscriptions (
  token_hash TEXT PRIMARY KEY,
  valid_until INTEGER NOT NULL
);
