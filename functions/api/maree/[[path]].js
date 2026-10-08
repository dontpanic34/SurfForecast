// Relais des marées (Cloudflare Pages Function) : /api/maree/sites et /api/maree/tide-extrema.
// Le jeton api-maree.fr reste ici, côté serveur (variable secrète MAREE_TOKEN du projet Pages) et
// les réponses sont gardées en cache (sites : 7 jours, marées : 12 h) pour tous les utilisateurs.
const TTL = { 'sites': 7 * 24 * 3600, 'tide-extrema': 12 * 3600 };

export async function onRequestGet({ request, env, params }) {
  const path = Array.isArray(params.path) ? params.path.join('/') : String(params.path || '');
  if (!(path in TTL)) return new Response('not found', { status: 404 });

  const incoming = new URL(request.url);
  const publicParams = new URLSearchParams();
  [...incoming.searchParams.entries()]
    .filter(([k]) => k !== 'key')
    .sort(([a], [b]) => a.localeCompare(b))
    .forEach(([k, v]) => publicParams.set(k, v));

  // Clé de cache sans jeton.
  const cacheKey = new Request(`https://surflog.fr/_cache/maree/${path}?${publicParams}`);
  const cache = caches.default;
  const hit = await cache.match(cacheKey);
  if (hit) return withCors(hit);

  const upstream = new URL(`https://api-maree.fr/${path}`);
  publicParams.forEach((v, k) => upstream.searchParams.set(k, v));
  if (path !== 'sites') {
    if (!env.MAREE_TOKEN) return new Response('relais non configuré', { status: 503 });
    upstream.searchParams.set('key', env.MAREE_TOKEN);
  }

  const res = await fetch(upstream.toString(), { headers: { 'accept': 'application/json' } });
  if (!res.ok) return new Response(`api-maree ${res.status}`, { status: 502 });
  const body = await res.text();
  const out = new Response(body, {
    status: 200,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': `public, max-age=${TTL[path]}`
    }
  });
  await cache.put(cacheKey, out.clone());
  return withCors(out);
}

function withCors(response) {
  const r = new Response(response.body, response);
  r.headers.set('access-control-allow-origin', '*');
  return r;
}
