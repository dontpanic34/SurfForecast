// Relais des bouées (Cloudflare Pages Function) : /api/bouee/03302 donne les dernières mesures de la bouée Candhis (Cerema).
// Candhis n'a pas d'API et n'autorise pas l'appel depuis un navigateur : on lit ici la page de la station, on garde les
// chiffres et on les rend en JSON, avec un cache de 15 minutes pour tous les utilisateurs. Les heures sont en UTC.
// Données sous licence ouverte Etalab 2.0 : citer le Cerema et la date (champ `source`).
const TTL = 15 * 60;
const MAX_POINTS = 96; // 48 h de mesures toutes les 30 minutes

// Bouées temps réel (liste du 13 octobre 2026) : on refuse tout autre code.
const BUOYS = {
  '08002': 'Baie de Somme', '05008': 'Cherbourg', '07609': 'Le Havre', '02922': 'Ile de Batz', '02911': 'Les Pierres Noires',
  '05602': 'Belle-Ile', '04403': 'SEM-REV Plateau du Four', '08505': 'Noirmoutier', '08504': "Ile d'Yeu Nord", '01705': 'Royan',
  '03302': 'Cap Ferret', '03001': 'Espiguette', '06403': 'Saint-Jean-de-Luz', '03404': 'Sète', '01305': 'Le Planier',
  '01102': 'Port La Nouvelle', '08302': 'Porquerolles', '01101': 'Leucate', '06601': 'Banyuls', '02B05': 'Alistro', '02A01': 'Bonifacio'
};

export function parseCandhis(html) {
  const names = { 0: 'hs', 1: 'th', 2: 'dir', 3: 'spread', 4: 'temp' };
  const byTime = {};
  const re = /arrDataPHP\[(\d)\]\s*=\s*eval\('(\[\[.*?\]\])'\);/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    const idx = Number(m[1]);
    if (!(idx in names)) continue;
    let rows;
    try {
      const raw = m[2].replace(/\\\//g, '/').replace(/\\u([0-9a-fA-F]{4})/g, (_, h) => String.fromCharCode(parseInt(h, 16)));
      rows = JSON.parse(raw);
    } catch (e) { continue; }
    for (const r of rows) {
      const t = String(r[0]).replace(' ', 'T');
      const e = byTime[t] || (byTime[t] = {});
      e[names[idx]] = r[1];
      if (idx === 0 && r.length > 3) e.hmax = r[3];
    }
  }
  return Object.keys(byTime).sort().slice(-MAX_POINTS).map(t => ({ t, ...byTime[t] }));
}

export async function onRequestGet({ params }) {
  const code = Array.isArray(params.path) ? params.path[0] : String(params.path || '');
  if (!(code in BUOYS)) return new Response('bouée inconnue', { status: 404 });

  const cacheKey = new Request(`https://surflog.fr/_cache/bouee/${code}`);
  const cache = caches.default;
  const hit = await cache.match(cacheKey);
  if (hit) return withCors(hit);

  const token = btoa(`camp=${code}`);
  const res = await fetch(`https://candhis.cerema.fr/_public_/campagne.php?${token}`, { headers: { 'user-agent': 'SurfLog (https://surflog.fr)' } });
  if (!res.ok) return new Response(`candhis ${res.status}`, { status: 502 });
  const points = parseCandhis(await res.text());
  if (points.length === 0) return new Response('pas de mesure', { status: 502 });

  const body = JSON.stringify({
    code, name: BUOYS[code], utc: true,
    source: 'Candhis, Cerema (licence ouverte Etalab 2.0)',
    updated: points[points.length - 1].t,
    points
  });
  const out = new Response(body, {
    status: 200,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': `public, max-age=${TTL}` }
  });
  await cache.put(cacheKey, out.clone());
  return withCors(out);
}

function withCors(response) {
  const r = new Response(response.body, response);
  r.headers.set('access-control-allow-origin', '*');
  return r;
}
