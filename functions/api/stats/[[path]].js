// Statistiques anonymes (Cloudflare Pages Function) :
//   POST /api/stats/report  : l'appli envoie { id, v, lang, level, a:{…réponses…} } (identifiant aléatoire, aucune identité).
//   GET  /api/stats/        : la page de statistiques (publique, sans donnée : elle demande la clé).
//   GET  /api/stats/summary : les totaux, réservés au propriétaire (en-tête x-stats-key = variable secrète STATS_KEY).
// Stockage : espace KV lié au projet Pages sous le nom STATS (un enregistrement par utilisateur + un total tenu à jour).
// Rien de secret dans le dépôt : STATS_KEY et la liaison STATS se règlent dans Cloudflare.
const ANSWER_KEYS = ['what', 'barre', 'tk', 'peak', 'man', 'want', 'feel', 'train'];
const MULTI_KEYS = ['goal', 'where'];

const emptyAgg = () => ({ users: 0, levels: {}, versions: {}, langs: {}, q: {} });
const bump = (o, k, d) => { if (k === undefined || k === null || k === '') return; o[k] = (o[k] || 0) + d; if (o[k] <= 0) delete o[k]; };

export function contributions(rec) {
  const items = [];
  if (rec.level) items.push(['levels', rec.level]);
  if (rec.v) items.push(['versions', rec.v]);
  if (rec.lang) items.push(['langs', rec.lang]);
  const a = rec.a || {};
  for (const k of ANSWER_KEYS) if (Number.isInteger(a[k])) items.push(['q', k, a[k]]);
  for (const k of MULTI_KEYS) for (const x of Array.isArray(a[k]) ? a[k] : []) if (Number.isInteger(x)) items.push(['q', k, x]);
  return items;
}

export function applyRecord(agg, rec, d) {
  agg.users += d;
  for (const [kind, k, v] of contributions(rec)) {
    if (kind === 'q') { agg.q[k] = agg.q[k] || {}; bump(agg.q[k], v, d); if (!Object.keys(agg.q[k]).length) delete agg.q[k]; }
    else bump(agg[kind], k, d);
  }
}

export function clean(body) {
  const id = String(body.id || '');
  if (!/^[0-9a-f]{8,40}$/.test(id)) return null;
  const rec = { v: String(body.v || '').slice(0, 20), lang: String(body.lang || '').slice(0, 5), level: body.level ? String(body.level).slice(0, 30) : '' };
  if (body.a && typeof body.a === 'object') {
    rec.a = {};
    for (const k of ANSWER_KEYS) if (Number.isInteger(body.a[k]) && body.a[k] >= 0 && body.a[k] < 20) rec.a[k] = body.a[k];
    for (const k of MULTI_KEYS) if (Array.isArray(body.a[k])) rec.a[k] = body.a[k].filter(x => Number.isInteger(x) && x >= 0 && x < 20).slice(0, 8);
  }
  return { id, rec };
}

const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });

export async function onRequestPost({ request, env, params }) {
  const path = Array.isArray(params.path) ? params.path.join('/') : String(params.path || '');
  if (path !== 'report') return new Response('not found', { status: 404 });
  if (!env.STATS) return new Response('relais non configuré', { status: 503 });
  let body;
  try { body = await request.json(); } catch (e) { return new Response('bad request', { status: 400 }); }
  const c = clean(body);
  if (!c) return new Response('bad request', { status: 400 });
  const old = await env.STATS.get('u:' + c.id, 'json');
  if (old && JSON.stringify(old) === JSON.stringify(c.rec)) return json({ ok: true, changed: false });
  const agg = (await env.STATS.get('agg', 'json')) || emptyAgg();
  if (old) applyRecord(agg, old, -1);
  applyRecord(agg, c.rec, +1);
  await env.STATS.put('u:' + c.id, JSON.stringify(c.rec));
  await env.STATS.put('agg', JSON.stringify(agg));
  return json({ ok: true, changed: true });
}

export async function onRequestGet({ request, env, params }) {
  const path = Array.isArray(params.path) ? params.path.join('/') : String(params.path || '');
  if (path === '') return new Response(PAGE, { headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
  if (path !== 'summary') return new Response('not found', { status: 404 });
  const key = request.headers.get('x-stats-key') || '';
  if (!env.STATS_KEY || key.length !== env.STATS_KEY.length || key !== env.STATS_KEY) return new Response('accès refusé', { status: 401 });
  if (!env.STATS) return new Response('relais non configuré', { status: 503 });
  return json((await env.STATS.get('agg', 'json')) || emptyAgg());
}

const PAGE = `<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<title>Statistiques Surf Log</title>
<style>:root{--card:#13222a;--t:#e8f0f3;--m:#8fa6af;--ac:#f6c453;--bar:#3aa6c9}body{margin:0;background:#0b1519;color:var(--t);font:15px system-ui,sans-serif;display:flex;justify-content:center}
main{width:min(720px,100%);padding:16px;box-sizing:border-box}h1{font-size:20px}.card{background:var(--card);border-radius:16px;padding:14px;margin-bottom:12px}
h2{font-size:13px;color:var(--m);text-transform:uppercase;letter-spacing:.06em;margin:0 0 10px}.kpis{display:flex;gap:10px}.kpi{flex:1;background:#1c3340;border-radius:12px;padding:10px}.kpi b{display:block;font-size:22px}.kpi span{color:var(--m);font-size:12px}
.row{display:flex;align-items:center;gap:8px;margin:6px 0;font-size:13px}.row .l{width:46%}.bar{flex:1;background:#1c3340;border-radius:6px;height:14px;overflow:hidden}.bar i{display:block;height:100%;background:var(--bar)}.n{width:76px;text-align:right;color:var(--m);font-size:12px}
input,select,button{background:#1c3340;color:var(--t);border:1px solid #2a4452;border-radius:10px;padding:8px;font-size:14px}select{width:100%;margin-bottom:8px}</style>
<main><h1>Surf Log · Statistiques</h1><div id="gate" class="card"><h2>Accès réservé</h2><input id="k" type="password" placeholder="Clé"> <button onclick="go()">Entrer</button> <span id="err" style="color:#e8844a"></span></div><div id="out" hidden></div></main>
<script>
const LEVELS=["Débutant","Débutant +","Intermédiaire","Intermédiaire +","Confirmé","Confirmé +","Avancé","Avancé +","Expert"];
const Q={what:["Tu surfes…",["La mousse","La reforme","Le large"]],
barre:["Hauteur de vague où tu passes la barre",["Pas encore la barre","Genoux","Taille","Poitrine","Tête","Au-dessus de la tête"]],
tk:["Ton take-off",["Un seul temps, tout le temps","Un seul temps, souvent","Parfois en plusieurs temps"]],
peak:["Où tu te places",["Je me fie aux gens","Je repère le pic","Je sais où est le pic"]],
goal:["Ce que tu cherches sur la vague (plusieurs réponses)",["Réussir les take-offs","Enchaîner quelques manœuvres","Devenir radical","Affiner le style"]],
man:["Tes manœuvres",["Pas encore","Brouillon","Détails à régler","Replaque à chaque fois"]],
want:["Veux passer au large ?",["Oui, bientôt","Pas pour l'instant"]],
feel:["À la barre, tu te sens…",["Prêt à surfer","Impressionné","En galère","Au bon endroit"]],
train:["Entraînement hors surf",["Non","De temps en temps","Régulièrement"]],
where:["Où tu surfes (plusieurs réponses)",["Chez moi, avec du monde","Autour de chez moi","Je me déplace","Surf trips"]]};
const bars=(rows,tot)=>rows.map(([l,n])=>'<div class="row"><span class="l">'+l+'</span><div class="bar"><i style="width:'+(tot?n/tot*100:0)+'%"></i></div><span class="n">'+n+' · '+(tot?Math.round(n/tot*100):0)+' %</span></div>').join('');
async function go(){const k=document.getElementById('k').value;const r=await fetch('summary',{headers:{'x-stats-key':k}});
if(!r.ok){document.getElementById('err').textContent=r.status==401?'Clé refusée':'Erreur '+r.status;return}
try{localStorage.setItem('statsKey',k)}catch(e){}
const a=await r.json();document.getElementById('gate').hidden=true;const o=document.getElementById('out');o.hidden=false;
const lv=LEVELS.map(n=>[n,a.levels[n]||0]);const known=lv.reduce((s,x)=>s+x[1],0);
const avgIdx=known?lv.reduce((s,x,i)=>s+i*x[1],0)/known:0;
const top=(m)=>Object.entries(m||{}).sort((x,y)=>y[1]-x[1])[0];
let h='<div class="card"><h2>Vue d\\'ensemble</h2><div class="kpis"><div class="kpi"><b>'+a.users+'</b><span>utilisateurs</span></div><div class="kpi"><b>'+(known?LEVELS[Math.round(avgIdx)]:'-')+'</b><span>niveau moyen ('+known+' réponses)</span></div><div class="kpi"><b>'+((top(a.versions)||['-'])[0])+'</b><span>version la plus utilisée</span></div></div></div>';
h+='<div class="card"><h2>Répartition par niveau</h2>'+bars(lv,known)+'</div>';
h+='<div class="card"><h2>Réponses à chaque question</h2><select id="q" onchange="showQ()">'+Object.keys(Q).map(k=>'<option value="'+k+'">'+Q[k][0]+'</option>').join('')+'</select><div id="qa"></div></div>';
const vs=Object.entries(a.versions).sort((x,y)=>y[1]-x[1]);
h+='<div class="card"><h2>Versions de l\\'appli</h2>'+bars(vs,a.users)+'</div>';
o.innerHTML=h;window.__a=a;showQ();}
function showQ(){const k=document.getElementById('q').value,d=(window.__a.q||{})[k]||{},opts=Q[k][1];const tot=Object.values(d).reduce((s,x)=>s+x,0);
document.getElementById('qa').innerHTML=bars(opts.map((l,i)=>[l,d[i]||0]),tot)+'<small style="color:var(--m)">'+tot+' réponses</small>';}
try{const s=localStorage.getItem('statsKey');if(s){document.getElementById('k').value=s;go()}}catch(e){}
</script></html>`;
