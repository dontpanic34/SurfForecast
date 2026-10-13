#!/usr/bin/env python3
"""Relève les prévisions de chaque modèle et la mesure des bouées, pour comparer plus tard le prévu et le réel.

Une exécution écrit un fichier data/runs/AAAA-MM-JJ/HHZ.json.gz (heures en UTC, cinq jours de prévision).
Sources : Open-Meteo (modèles) et Candhis / Cerema (bouées, licence Etalab 2.0).
Aucune clé : les deux services sont ouverts. Usage : collect.py [dossier de sortie]
"""
import base64, concurrent.futures as cf, datetime as dt, gzip, json, os, re, sys, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, "spots.json"), encoding="utf-8"))
DAYS = 5
WAVE_VARS = ["wave_height", "wave_period", "wave_direction", "swell_wave_height", "swell_wave_period", "secondary_swell_wave_height", "secondary_swell_wave_period"]
WX_VARS = ["wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "temperature_2m", "precipitation", "cloud_cover", "weather_code"]
ROUND = {"wave_height": 2, "swell_wave_height": 2, "secondary_swell_wave_height": 2, "wave_period": 1, "swell_wave_period": 1,
         "secondary_swell_wave_period": 1, "wave_direction": 0, "wind_speed_10m": 0, "wind_direction_10m": 0, "wind_gusts_10m": 0,
         "temperature_2m": 1, "precipitation": 1, "cloud_cover": 0, "weather_code": 0}


def get(url, binary=False, tries=5, timeout=120):
    last = None
    for a in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "SurfLog-collector (https://surflog.fr)"})
            raw = urllib.request.urlopen(req, timeout=timeout).read()
            return raw if binary else json.loads(raw)
        except Exception as e:  # réseau ou quota : on réessaie
            last = e
            time.sleep(2 + 3 * a)
    raise RuntimeError(f"{url} : {last}")


def series(hourly, var, model, model_count):
    """Open-Meteo suffixe les clés par le modèle quand plusieurs sont demandés."""
    for key in (f"{var}_{model}", var):
        if key in hourly:
            d = ROUND.get(var)
            return [None if v is None else (round(v, d) if d else int(round(v))) for v in hourly[key]]
    return None


def collect_models(spot):
    out = {"wave": {}, "wx": {}}
    models = CFG["wave_models"]
    url = ("https://marine-api.open-meteo.com/v1/marine?latitude=%s&longitude=%s&hourly=%s&models=%s&forecast_days=%d&timezone=GMT"
           % (spot["lat"], spot["lon"], ",".join(WAVE_VARS), ",".join(models.values()), DAYS))
    h = get(url)["hourly"]
    out["time"] = h["time"]
    for name, api in models.items():
        out["wave"][name] = {v: s for v in WAVE_VARS if (s := series(h, v, api, len(models))) is not None and any(x is not None for x in s)}
    wx = CFG["wx_models"]
    url = ("https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&hourly=%s&models=%s&forecast_days=%d&timezone=GMT"
           % (spot["lat"], spot["lon"], ",".join(WX_VARS), ",".join(wx.values()), DAYS))
    h = get(url)["hourly"]
    for name, api in wx.items():
        out["wx"][name] = {v: s for v in WX_VARS if (s := series(h, v, api, len(wx))) is not None and any(x is not None for x in s)}
    return out


def collect_buoy(code):
    """Candhis n'a pas d'API : les mesures sont dans la page de la station (heures en UTC)."""
    token = base64.b64encode(f"camp={code}".encode()).decode()
    html = get(f"https://candhis.cerema.fr/_public_/campagne.php?{token}", binary=True).decode("utf-8", "replace")
    names = {0: "hs", 1: "th", 2: "dir", 3: "spread", 4: "temp"}
    res = {}
    for m in re.finditer(r"arrDataPHP\[(\d)\]\s*=\s*eval\('(\[\[.*?\]\])'\);", html):
        idx = int(m.group(1))
        if idx not in names:
            continue
        raw = re.sub(r"\\u([0-9a-fA-F]{4})", lambda x: chr(int(x.group(1), 16)), m.group(2).replace("\\/", "/"))
        try:
            rows = json.loads(raw)
        except ValueError:
            continue
        for r in rows:
            t = r[0].replace(" ", "T")
            e = res.setdefault(t, {})
            e[names[idx]] = r[1]
            if idx == 0 and len(r) > 3:
                e["hmax"] = r[3]
    return dict(sorted(res.items()))


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "data")
    now = dt.datetime.now(dt.timezone.utc)
    run = {"run_utc": now.strftime("%Y-%m-%dT%H:%MZ"), "sources": "Open-Meteo (CC BY 4.0), Candhis / Cerema (Etalab 2.0)", "spots": {}, "buoys": {}}
    def one_spot(spot):
        try:
            r = collect_models(spot)
            r["buoy"] = spot["buoy"]
            return spot["id"], r
        except Exception as e:
            print(f"! {spot['id']} : {e}", file=sys.stderr)
            return spot["id"], None

    def one_buoy(code):
        try:
            return code, collect_buoy(code)
        except Exception as e:
            print(f"! bouée {code} : {e}", file=sys.stderr)
            return code, None

    codes = sorted({s["buoy"] for s in CFG["spots"]})
    with cf.ThreadPoolExecutor(max_workers=6) as pool:
        spot_jobs = [pool.submit(one_spot, s) for s in CFG["spots"]]
        buoy_jobs = [pool.submit(one_buoy, c) for c in codes]
        for j in spot_jobs:
            k, v = j.result()
            if v:
                run["spots"][k] = v
        for j in buoy_jobs:
            k, v = j.result()
            if v:
                run["buoys"][k] = v
    if not run["spots"]:
        sys.exit("aucun spot relevé")
    folder = os.path.join(out_dir, "runs", now.strftime("%Y-%m-%d"))
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, now.strftime("%H") + "Z.json.gz")
    with gzip.open(path, "wt", encoding="utf-8") as f:
        json.dump(run, f, separators=(",", ":"), ensure_ascii=False)
    print(path, os.path.getsize(path), "octets")


if __name__ == "__main__":
    main()
