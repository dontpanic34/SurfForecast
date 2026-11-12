// Rapports de problème (Cloudflare Pages Function) :
//   POST /api/report        : le site envoie { v, title, text } quand un écran vide ou une erreur graphique est détecté
//                             (texte technique : navigateur, WebGL, erreurs ; jamais de nom, de position ni d'identifiant).
//   GET  /api/report        : les derniers rapports, réservés au propriétaire (en-tête x-stats-key = secret STATS_KEY).
// Stockage : espace KV « STATS » (clés r:…, gardées 30 jours). Notification : push ntfy sur le canal secret NTFY_TOPIC
// (variable secrète Cloudflare, jamais dans le dépôt). Garde-fous : taille limitée, 5 rapports par heure et par adresse (gardée hachée),
// et une même panne n'envoie qu'une notification par jour.
const MAX_BODY = 8000;
const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });
const sha = async (s) => [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)))].map(b => b.toString(16).padStart(2, '0')).join('').slice(0, 24);

export function clean(body) {
  if (!body || typeof body !== 'object') return null;
  const text = String(body.text || '').slice(0, 6000);
  if (text.length < 10) return null;
  return { v: String(body.v || '').slice(0, 20), title: String(body.title || '').slice(0, 200), text };
}

/** Une ligne par panne : le navigateur, le titre et la première erreur, pour ne notifier qu'une fois par jour. */
export function signature(rec) {
  const lines = rec.text.split('\n');
  const ua = (lines.find(l => l.startsWith('Navigateur')) || '').slice(0, 200);
  const err = lines[lines.findIndex(l => l.startsWith('Erreurs')) + 1] || '';
  return rec.title + '|' + ua + '|' + err.replace(/^[\d.]+s /, '').slice(0, 120);
}

export async function onRequestPost({ request, env }) {
  if (!env.STATS) return new Response('relais non configuré', { status: 503 });
  const raw = await request.text();
  if (raw.length > MAX_BODY) return new Response('trop gros', { status: 413 });
  let body;
  try { body = JSON.parse(raw); } catch (e) { return new Response('bad request', { status: 400 }); }
  const rec = clean(body);
  if (!rec) return new Response('bad request', { status: 400 });

  const now = new Date();
  const hour = now.toISOString().slice(0, 13);
  const ip = await sha(request.headers.get('cf-connecting-ip') || 'inconnu');
  const rlKey = 'rl:' + ip + ':' + hour;
  const count = parseInt(await env.STATS.get(rlKey) || '0', 10);
  if (count >= 5) return json({ ok: false, limited: true }, 429);
  await env.STATS.put(rlKey, String(count + 1), { expirationTtl: 7200 });

  const id = 'r:' + now.toISOString() + ':' + (await sha(rec.text + Math.random())).slice(0, 6);
  await env.STATS.put(id, JSON.stringify({ ...rec, at: now.toISOString() }), { expirationTtl: 30 * 86400 });

  let notified = false;
  const dupKey = 'rd:' + await sha(signature(rec)) + ':' + now.toISOString().slice(0, 10);
  const firstToday = !(await env.STATS.get(dupKey));
  if (firstToday) {
    await env.STATS.put(dupKey, '1', { expirationTtl: 2 * 86400 });
    if (env.NTFY_TOPIC) {
      const ua = (rec.text.split('\n').find(l => l.startsWith('Navigateur')) || '').replace('Navigateur : ', '').slice(0, 140);
      try {
        const r = await fetch('https://ntfy.sh/' + encodeURIComponent(env.NTFY_TOPIC), {
          method: 'POST',
          headers: { 'Title': 'Surf Log ' + rec.v + ' : probleme signale', 'Priority': 'default', 'Tags': 'warning' },
          body: (rec.title + '\n' + ua).slice(0, 400),
        });
        notified = r.ok;
      } catch (e) { /* la notification est un plus : le rapport est déjà gardé */ }
    }
  }
  return json({ ok: true, notified });
}

export async function onRequestGet({ request, env }) {
  const key = request.headers.get('x-stats-key') || '';
  if (!env.STATS_KEY || key.length !== env.STATS_KEY.length || key !== env.STATS_KEY) {
    await new Promise(r => setTimeout(r, 1500));
    return new Response('accès refusé', { status: 401 });
  }
  if (!env.STATS) return new Response('relais non configuré', { status: 503 });
  const list = await env.STATS.list({ prefix: 'r:', limit: 50 });
  const items = await Promise.all(list.keys.map(k => env.STATS.get(k.name, 'json')));
  return json({ reports: items.filter(Boolean).sort((a, b) => (a.at < b.at ? 1 : -1)) });
}
