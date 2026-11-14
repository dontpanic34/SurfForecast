#!/usr/bin/env python3
"""Bilan des modèles : compare les prévisions relevées (data/runs) aux mesures (bouées Candhis, stations Météo-France dans data/obs).

Usage : analyse.py <dossier data>

Pour chaque modèle et chaque tranche d'échéance, l'écart moyen (prévu moins mesuré) et l'erreur absolue moyenne.
- Vagues : hauteur significative et période, contre la bouée rattachée au spot.
- Vent : vent moyen et rafales (km/h), contre la station la plus proche du spot. Les stations donnent des m/s : on multiplie par 3,6.
Les règles de l'appli sont rejouées : houle = moyenne MF-WAM et ECMWF ; vent = médiane des modèles, rafales = moyenne des deux plus fortes.
Bibliothèque standard seulement.
"""
import glob
import gzip
import json
import os
import statistics
import sys
from collections import defaultdict
from datetime import datetime

BUCKETS = [(0, 24, "0-24 h"), (24, 72, "24-72 h"), (72, 121, "72-120 h")]


def parse(t):
    return datetime.strptime(t[:16], "%Y-%m-%dT%H:%M")


def bucket(h):
    for a, b, name in BUCKETS:
        if a <= h < b:
            return name
    return None


class Stat:
    def __init__(self):
        self.n = 0
        self.sum = 0.0
        self.abs = 0.0

    def add(self, forecast, observed):
        self.n += 1
        self.sum += forecast - observed
        self.abs += abs(forecast - observed)

    def text(self, digits=1):
        return f"écart {self.sum / self.n:+.{digits}f}, erreur {self.abs / self.n:.{digits}f} (n={self.n})" if self.n else "aucune donnée"


def main():
    data = sys.argv[1]
    # Mesures des bouées : réunies depuis tous les relevés.
    buoys = defaultdict(dict)
    runs = []
    for path in sorted(glob.glob(os.path.join(data, "runs", "*", "*.json.gz"))):
        run = json.load(gzip.open(path))
        runs.append(run)
        for code, series in (run.get("buoys") or {}).items():
            for t, m in series.items():
                buoys[code][t[:16]] = m
    # Mesures des stations : la plus proche de chaque spot.
    stations = defaultdict(dict)   # spot -> heure -> (vent km/h, rafale km/h)
    nearest = {}
    for path in sorted(glob.glob(os.path.join(data, "obs", "*", "*.json.gz"))):
        obs = json.load(gzip.open(path))
        for sid, st in (obs.get("stations") or {}).items():
            for spot, dist in (st.get("spots") or {}).items():
                if spot not in nearest or dist < nearest[spot][0]:
                    nearest[spot] = (dist, sid, st.get("name"))
    for path in sorted(glob.glob(os.path.join(data, "obs", "*", "*.json.gz"))):
        obs = json.load(gzip.open(path))
        for sid, st in (obs.get("stations") or {}).items():
            for spot in (st.get("spots") or {}):
                if nearest[spot][1] != sid:
                    continue
                for h in st.get("hourly") or []:
                    if h.get("ff") is None:
                        continue
                    stations[spot][h["validity_time"][:16]] = (h["ff"] * 3.6, h["raf"] * 3.6 if h.get("raf") is not None else None)

    wave_h = defaultdict(Stat)
    wave_t = defaultdict(Stat)
    wind = defaultdict(Stat)
    gust = defaultdict(Stat)
    strong = defaultdict(Stat)   # vent mesuré de 25 km/h et plus
    per_spot = defaultdict(Stat)
    for run in runs:
        t0 = parse(run["run_utc"])
        for spot, s in run["spots"].items():
            times = s["time"]
            for i, t in enumerate(times):
                lead = (parse(t) - t0).total_seconds() / 3600
                b = bucket(lead)
                if b is None:
                    continue
                # Vagues
                m = buoys.get(s.get("buoy"), {}).get(t[:16])
                if m and m.get("hs") is not None:
                    hs = {}
                    for model, v in s["wave"].items():
                        h = (v.get("wave_height") or [None] * len(times))[i]
                        p = (v.get("wave_period") or [None] * len(times))[i]
                        if h is not None:
                            hs[model] = h
                            wave_h[(model, b)].add(h, m["hs"])
                        if p is not None and m.get("th") is not None:
                            wave_t[(model, b)].add(p, m["th"])
                    if "MF-WAM" in hs and "ECMWF" in hs:
                        wave_h[("Appli (MF-WAM + ECMWF)", b)].add((hs["MF-WAM"] + hs["ECMWF"]) / 2, m["hs"])
                # Vent
                o = stations.get(spot, {}).get(t[:16])
                if o:
                    speeds, gusts = {}, {}
                    for model, v in s["wx"].items():
                        w = (v.get("wind_speed_10m") or [None] * len(times))[i]
                        g = (v.get("wind_gusts_10m") or [None] * len(times))[i]
                        if w is not None:
                            speeds[model] = w
                            wind[(model, b)].add(w, o[0])
                            if o[0] >= 25:
                                strong[(model, b)].add(w, o[0])
                        if g is not None and o[1] is not None:
                            gusts[model] = g
                            gust[(model, b)].add(g, o[1])
                    if len(speeds) >= 2:
                        med = statistics.median(speeds.values())
                        wind[("Appli (médiane)", b)].add(med, o[0])
                        per_spot[(spot, b)].add(med, o[0])
                        if o[0] >= 25:
                            strong[("Appli (médiane)", b)].add(med, o[0])
                    if len(gusts) >= 2 and o[1] is not None:
                        top = sorted(gusts.values())[-2:]
                        gust[("Appli (2 plus fortes)", b)].add(sum(top) / 2, o[1])
                        gust[("Médiane des modèles", b)].add(statistics.median(gusts.values()), o[1])

    def table(title, stats, digits=1, order=None):
        print("\n== " + title)
        models = order or sorted({k[0] for k in stats})
        for model in models:
            print(f"  {model}")
            for _, _, name in BUCKETS:
                st = stats.get((model, name))
                if st and st.n:
                    print(f"    {name:9s} {st.text(digits)}")

    first = min(r["run_utc"] for r in runs)[:10]
    last = max(r["run_utc"] for r in runs)[:10]
    print(f"{len(runs)} relevés du {first} au {last}")
    print("Stations retenues : " + " ; ".join(f"{spot} : {name} ({dist:.0f} km)" for spot, (dist, _, name) in sorted(nearest.items())))
    table("Hauteur des vagues (m), contre les bouées", wave_h, 2)
    table("Période des vagues (s), contre les bouées", wave_t, 1)
    table("Vent moyen (km/h), contre les stations", wind)
    table("Vent moyen quand la station mesure 25 km/h ou plus", strong)
    table("Rafales (km/h), contre les stations", gust)
    table("Vent de l'appli, spot par spot", per_spot)


if __name__ == "__main__":
    main()
