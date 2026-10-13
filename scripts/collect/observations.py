#!/usr/bin/env python3
"""Relève les mesures réelles (vent, température, pluie) des stations Météo-France proches de chaque spot.

API « Données d'observation » du portail Météo-France (licence ouverte, clé gratuite) : la clé est lue dans la
variable d'environnement METEOFRANCE_API_KEY (secret GitHub) et n'est jamais écrite dans un fichier ni affichée.
Les données gardées 24 h côté Météo-France sont recopiées chaque jour dans data/obs/AAAA-MM-JJ/HHZ.json.gz.
Usage : observations.py [dossier de sortie]
"""
import csv, datetime as dt, gzip, io, json, math, os, sys, time, urllib.error, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, "spots.json"), encoding="utf-8"))
BASE = "https://public-api.meteofrance.fr/public/DPObs"
MAX_KM = 35        # on garde les stations à moins de 35 km du spot
PER_SPOT = 3       # au plus trois stations par spot
KEY = os.environ.get("METEOFRANCE_API_KEY", "").strip()
AUTH = []          # en-tête qui marche, trouvé au premier appel
VERSION = [""]     # préfixe de version qui marche ("", "/v1" ou "/v2")


def call(path, params, accept="application/json"):
    """Renvoie (statut, texte). Essaie l'en-tête « apikey » puis « Authorization: Bearer »."""
    url = f"{BASE}{VERSION[0]}{path}?{urllib.parse.urlencode(params)}"
    variants = AUTH or [{"apikey": KEY}, {"Authorization": f"Bearer {KEY}"}]
    status, text = 0, ""
    for hdr in variants:
        req = urllib.request.Request(url, headers={**hdr, "accept": accept, "User-Agent": "SurfLog-collector (https://surflog.fr)"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                status, text = r.status, r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            status, text = e.code, e.read().decode("utf-8", "replace")[:200]
        except Exception as e:
            status, text = 0, str(e)[:200]
        if status == 200:
            if not AUTH:
                AUTH.append(hdr)
                print("auth :", "apikey" if "apikey" in hdr else "bearer")
            return status, text
        if status in (401, 403):
            continue
        break
    return status, text


def km(a, b, c, d):
    p = math.pi / 180
    x = math.sin((c - a) * p / 2) ** 2 + math.cos(a * p) * math.cos(c * p) * math.sin((d - b) * p / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(x))


def stations():
    for version in ("", "/v1", "/v2"):
      VERSION[0] = version
      for fmt in ("csv", "json"):
        st, text = call("/liste-stations", {"format": fmt}, "text/csv" if fmt == "csv" else "application/json")
        print("liste-stations", version or "(sans version)", fmt, st, len(text), "" if st == 200 else text[:160].replace(chr(10), " "))
        if st != 200:
            continue
        rows = []
        if fmt == "csv":
            for r in csv.DictReader(io.StringIO(text), delimiter=";" if text.count(";") > text.count(",") else ","):
                rows.append({k.strip().lower(): (v or "").strip() for k, v in r.items() if k})
        else:
            data = json.loads(text)
            rows = [{k.lower(): str(v) for k, v in (x.get("properties", x) if isinstance(x, dict) else {}).items()} for x in (data if isinstance(data, list) else data.get("features", []))]
        out = []
        for r in rows:
            def pick(*names):
                for n in names:
                    for k in r:
                        if n in k:
                            return r[k]
                return ""
            try:
                out.append({"id": pick("id_station", "geo_id", "id").zfill(8), "name": pick("nom", "name"),
                            "lat": float(pick("lat")), "lon": float(pick("lon"))})
            except ValueError:
                continue
        if out:
            return out
    return []


KEEP = ("validity_time", "t", "td", "u", "dd", "ff", "dxy", "fxy", "ddraf", "raf", "rr1", "vv", "n", "pres", "insolh")


def hourly(station_id, date=None):
    """Mesures horaires d'une station (la dernière, ou l'heure `date` AAAA-MM-JJTHH:00:00Z), vides retirées."""
    params = {"id_station": station_id, "format": "json"}
    if date:
        params["date"] = date
    for path in ("/station/horaire", "/station-horaire"):
        st, text = call(path, params)
        if st == 200:
            try:
                data = json.loads(text)
            except ValueError:
                return None
            return [{k: r[k] for k in KEEP if r.get(k) is not None} for r in data if isinstance(r, dict)]
        print(path, station_id, date or "dernière", st, text[:120].replace(chr(10), " "))
    return None


def main():
    if not KEY:
        print("pas de clé METEOFRANCE_API_KEY : relevé des observations ignoré")
        return
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "data")
    now = dt.datetime.now(dt.timezone.utc)
    lst = stations()
    if not lst:
        sys.exit("liste des stations introuvable (voir les statuts ci-dessus)")
    print(len(lst), "stations dans la liste")
    chosen = {}
    for spot in CFG["spots"]:
        near = sorted(((km(spot["lat"], spot["lon"], s["lat"], s["lon"]), s) for s in lst), key=lambda t: t[0])
        print(spot["id"], " ; ".join(f"{s['name']} {s['id']} {d:.0f} km" for d, s in near[:5]))
        for d, s in near[:PER_SPOT]:
            if d <= MAX_KM:
                e = chosen.setdefault(s["id"], {"name": s["name"], "lat": s["lat"], "lon": s["lon"], "spots": {}})
                e["spots"][spot["id"]] = round(d, 1)
    run = {"run_utc": now.strftime("%Y-%m-%dT%H:%MZ"), "source": "Météo-France, API Données d'observation (licence ouverte)", "stations": {}}
    latest = {}
    for sid in chosen:
        latest[sid] = hourly(sid)
        time.sleep(0.8)  # reste sous la limite d'appels par minute
    # Pour chaque spot : les deux stations les plus proches qui mesurent le vent. On remonte ensuite les 8 dernières heures.
    keep = {}
    for spot in CFG["spots"]:
        cands = sorted(((info["spots"][spot["id"]], sid) for sid, info in chosen.items() if spot["id"] in info["spots"]))
        good = [sid for d, sid in cands if latest.get(sid) and any("ff" in r for r in latest[sid])][:2]
        print(spot["id"], "stations avec vent :", good or "aucune")
        for sid in good:
            keep[sid] = chosen[sid]
    for sid, info in keep.items():
        series = {r["validity_time"]: r for r in (latest.get(sid) or []) if "validity_time" in r}
        for back in range(1, 9):
            when = (now - dt.timedelta(hours=back)).replace(minute=0, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")
            for r in hourly(sid, when) or []:
                if "validity_time" in r:
                    series[r["validity_time"]] = r
            time.sleep(0.8)
        info["hourly"] = [series[k] for k in sorted(series)]
        run["stations"][sid] = info
        print(sid, info["name"], len(info["hourly"]), "heures")
    if not run["stations"]:
        sys.exit("aucune station avec vent")
    folder = os.path.join(out_dir, "obs", now.strftime("%Y-%m-%d"))
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, now.strftime("%H") + "Z.json.gz")
    with gzip.open(path, "wt", encoding="utf-8") as f:
        json.dump(run, f, separators=(",", ":"), ensure_ascii=False)
    print(path, os.path.getsize(path), "octets,", len(run["stations"]), "stations")


if __name__ == "__main__":
    main()
