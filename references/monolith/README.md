# Flash Drop Backend

Backend Express desplegado como Vercel Function y conectado a Supabase Postgres.

## Produccion

- API: `https://flash-drop-delivery.vercel.app`
- Panel: `https://flash-drop-delivery.vercel.app/admin`
- Estado: `https://flash-drop-delivery.vercel.app/health`

## Desarrollo local

```bash
npm install
npm start
```

Variables requeridas en `.env`:

```env
SUPABASE_URL=
SUPABASE_SERVICE_ROLE_KEY=
SUPABASE_BUCKET=fotos_delivery
JWT_SECRET=
```

El esquema y seed migrado se encuentran en `supabase_migration.sql`.
