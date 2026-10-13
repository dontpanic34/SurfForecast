// Relais des mesures Météo-France (Cloudflare Pages Function) : /api/obs/near?lat=45.38&lon=-1.17 donne la dernière mesure
// horaire de la station la plus proche qui mesure le vent (à moins de 35 km) : vent, rafales, direction, pluie, température.
// La clé de l'API « Données d'observation » (portail-api.meteofrance.fr) reste ici, côté serveur : variable secrète
// METEOFRANCE_API_KEY du projet Pages. Réponses gardées en cache (liste des stations : 7 jours, mesures : 10 minutes).
// Données Météo-France sous licence ouverte (citer la source, champ `source`). Les heures sont en UTC.
const BASE = 'https://public-api.meteofrance.fr/public/DPObs/v2';
const MAX_KM = 35;
const TRY_STATIONS = 3;
const LIST_TTL = 7 * 24 * 3600;
const OBS_TTL = 10 * 60;

export function parseStations(text) {
  const lines = text.split(/\r?\n/).filter(l => l.trim());
  if (lines.length < 2) return [];
  const sep = (lines[0].match(/;/g) || []).length > (lines[0].match(/,/g) || []).length ? ';' : ',';
  const head = lines[0].split(sep).map(h => h.trim().toLowerCase());
  const col = (...names) => head.findIndex(h => names.some(n => h.includes(n)));
  const iId = col('id_station', 'geo_id', 'id'), iName = col('nom', 'name'), iLat = col('lat'), iLon = col('lon');
  if (iId < 0 || iLat < 0 || iLon < 0) return [];
  const out = [];
  for (const line of lines.slice(1)) {
    const c = line.split(sep).map(x => x.trim());
    const lat = parseFloat(c[iLat]), lon = parseFloat(c[iLon]);
    if (Number.isNaN(lat) || Number.isNaN(lon)) continue;
    out.push({ id: String(c[iId]).padStart(8, '0'), name: iName >= 0 ? c[iName] : '', lat, lon });
  }
  return out;
}

export function km(a, b, c, d) {
  const p = Math.PI / 180;
  const x = Math.sin((c - a) * p / 2) ** 2 + Math.cos(a * p) * Math.cos(c * p) * Math.sin((d - b) * p / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(x));
}

export function nearest(stations, lat, lon, n = TRY_STATIONS, maxKm = MAX_KM) {
  return stations.map(s => ({ ...s, km: km(lat, lon, s.lat, s.lon) })).filter(s => s.km <= maxKm).sort((a, b) => a.km - b.km).slice(0, n);
}

// Une mesure horaire (m/s, kelvin) en unités de l'appli (km/h, °C) ; null si la station ne mesure pas le vent.
export function toReading(station, rec) {
  if (!rec || rec.ff == null) return null;
  const kmh = v => (v == null ? null : Math.round(v * 3.6));
  return {
    station: { id: station.id, name: station.name, km: Math.round(station.km * 10) / 10 },
    time: rec.validity_time,
    windKmh: kmh(rec.ff),
    gustKmh: kmh(rec.raf),
    dirDeg: rec.dd == null ? null : Math.round(rec.dd),
    rainMm: rec.rr1 == null ? null : rec.rr1,
    tempC: rec.t == null ? null : Math.round((rec.t - 273.15) * 10) / 10,
    source: 'Météo-France, API Données d\'observation (licence ouverte)'
  };
}

async function mf(path, params, env) {
  const url = `${BASE}${path}?${new URLSearchParams(params)}`;
  return fetch(url, { headers: { apikey: env.METEOFRANCE_API_KEY, accept: 'application/json' } });
}

async function cached(key, ttl, make) {
  const cache = caches.default;
  const req = new Request(`https://surflog.fr/_cache/obs/${key}`);
  const hit = await cache.match(req);
  if (hit) return hit.text();
  const text = await make();
  if (text != null) {
    await cache.put(req, new Response(text, { headers: { 'cache-control': `public, max-age=${ttl}` } }));
  }
  return text;
}

export async function onRequestGet({ request, env, params }) {
  const path = Array.isArray(params.path) ? params.path.join('/') : String(params.path || '');
  if (path !== 'near') return new Response('not found', { status: 404 });
  if (!env.METEOFRANCE_API_KEY) return new Response('relais non configuré', { status: 503 });

  const u = new URL(request.url);
  const lat = parseFloat(u.searchParams.get('lat')), lon = parseFloat(u.searchParams.get('lon'));
  if (Number.isNaN(lat) || Number.isNaN(lon) || lat < 41 || lat > 52 || lon < -6 || lon > 10) return new Response('hors de France', { status: 404 });

  const list = await cached('stations', LIST_TTL, async () => {
    const r = await mf('/liste-stations', { format: 'csv' }, env);
    return r.ok ? r.text() : null;
  });
  if (list == null) return new Response('météo-france liste', { status: 502 });

  for (const s of nearest(parseStations(list), lat, lon)) {
    const text = await cached(`last-${s.id}`, OBS_TTL, async () => {
      const r = await mf('/station/horaire', { id_station: s.id, format: 'json' }, env);
      return r.ok ? r.text() : null;
    });
    if (text == null) continue;
    let rec;
    try { const data = JSON.parse(text); rec = Array.isArray(data) ? data[0] : null; } catch (e) { continue; }
    const reading = toReading(s, rec);
    if (reading) {
      return new Response(JSON.stringify(reading), {
        status: 200,
        headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': `public, max-age=${OBS_TTL}`, 'access-control-allow-origin': '*' }
      });
    }
  }
  return new Response('pas de station qui mesure le vent près de ce point', { status: 404, headers: { 'access-control-allow-origin': '*' } });
}
