# Servidor de la comunidad

Cloudflare Worker + base de datos D1. El plan gratuito de Cloudflare cubre de sobra el arranque.

```bash
cd apps/filtro-400/servidor
npm install
npx wrangler login
npx wrangler d1 create filtro400            # copia el database_id en wrangler.toml
npx wrangler d1 execute filtro400 --remote --file=schema.sql
openssl rand -hex 32 | npx wrangler secret put PEPPER
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT   # pega el JSON completo de la cuenta de servicio
npm run deploy
```

## Cuenta de servicio de Google (para verificar suscripciones)

1. Google Cloud Console → crea un proyecto → activa **Google Play Android Developer API**.
2. IAM → Cuentas de servicio → crea una y descarga su clave JSON.
3. Play Console → Usuarios y permisos → invita el correo de la cuenta de servicio con permiso **Ver información financiera** y **Gestionar pedidos y suscripciones**.
4. La concesión de permisos puede tardar hasta unas horas en propagarse.

## Retirar un número denunciado por error

```bash
npx wrangler d1 execute filtro400 --remote --command "INSERT OR REPLACE INTO excluded(number, note, created_at) VALUES ('+34612345678','reclamado por titular', strftime('%s','now')*1000)"
```

## Desarrollo

`npm run typecheck` y `npm test` (Node 22). `npm run dev` levanta el Worker en local con D1 simulada.
