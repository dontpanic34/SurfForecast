// Lien direct vers une webcam GoSurf : /api/cam?u=<adresse GoSurf du catalogue>&q=<recherche de secours>.
// GoSurf change parfois les numéros de ses pages (une vieille adresse répond 404). On vérifie donc l'adresse du
// catalogue (réponse gardée 6 h par Cloudflare) : si elle répond, on y va tout droit (une seule étape, rien à
// choisir) ; sinon on retombe sur la recherche DuckDuckGo « !ducky » qui ouvre le premier résultat.
const GOSURF = /^https:\/\/gosurf\.fr\/webcam\/fr\/\d+\/[A-Za-z0-9-]+$/;

export function go(location) {
  return new Response(null, { status: 302, headers: { Location: location, 'Cache-Control': 'public, max-age=300' } });
}

export async function onRequestGet({ request }) {
  const params = new URL(request.url).searchParams;
  const u = params.get('u') || '';
  const q = (params.get('q') || 'webcam gosurf').slice(0, 120);
  const fallback = 'https://duckduckgo.com/?q=' + encodeURIComponent('!ducky ' + q);
  if (!GOSURF.test(u)) return go(fallback);
  try {
    const r = await fetch(u, { redirect: 'manual', signal: AbortSignal.timeout(4000), cf: { cacheTtl: 6 * 3600, cacheEverything: true } });
    if (r.status === 200) return go(u);
  } catch (e) { /* GoSurf ne répond pas : recherche de secours */ }
  return go(fallback);
}
