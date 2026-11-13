#!/usr/bin/env python3
"""Vue satellite : découpe, à chaque passage de Sentinel-2, une bande de côte autour d'un spot.

Usage : satellite.py <dossier data> [jours en arrière, 12 par défaut]

Pour chaque zone de ZONES, range dans <data>/sat/<zone>/ :
  - AAAAMMJJ.jpg : la bande de côte (nord en haut, 10 m par pixel) ;
  - index.json   : le cadre de la bande (latitudes, longitudes, taille) et, pour chaque photo, sa date, son heure (UTC)
                   la part de nuages par tranche de 500 m du nord au sud (0 à 100), pour juger les nuages point par point,
                   et la marée du moment (niveau, état, marnage du jour) d'après le niveau de la mer d'Open-Meteo.
Les photos déjà rangées ne sont pas refaites.

Sources : Copernicus Sentinel-2 (niveau 2A), catalogue Earth Search (Element 84), fichiers en libre accès chez Amazon, sans compte ;
niveau de la mer : Open-Meteo (CC BY 4.0).
Dépendances : rasterio, numpy, pillow.
"""
import json
import math
import os
import sys
import urllib.request
from datetime import datetime, timedelta, timezone

import numpy as np
import rasterio
from affine import Affine
from PIL import Image
from rasterio.warp import Resampling, reproject

STAC = "https://earth-search.aws.element84.com/v1/search"
PIXEL_M = 10.0
BLOCK_ROWS = 50  # tranche de 500 m pour la part de nuages

# Une zone : le point de repère, la longueur de côte de chaque côté (km), et la largeur gardée à l'ouest et à l'est (km).
ZONES = {
    "montalivet": {"lat": 45.380, "lon": -1.159, "half_km": 15.0, "west_km": 2.2, "east_km": 3.0},
}


def frame(zone):
    """Cadre de la bande : nord en haut, pixels de 10 m, coordonnées linéaires en latitude et longitude."""
    dlat = PIXEL_M / 111320.0
    dlon = PIXEL_M / (111320.0 * math.cos(math.radians(zone["lat"])))
    height = int(round(2 * zone["half_km"] * 1000 / PIXEL_M))
    width = int(round((zone["west_km"] + zone["east_km"]) * 1000 / PIXEL_M))
    lat_top = zone["lat"] + zone["half_km"] * 1000 / PIXEL_M * dlat
    lon_left = zone["lon"] - zone["west_km"] * 1000 / PIXEL_M * dlon
    return {
        "width": width, "height": height,
        "lat_top": lat_top, "lat_bottom": lat_top - height * dlat,
        "lon_left": lon_left, "lon_right": lon_left + width * dlon,
        "transform": Affine(dlon, 0, lon_left, 0, -dlat, lat_top),
    }


def search(zone, since):
    body = {
        "collections": ["sentinel-2-l2a"],
        "intersects": {"type": "Point", "coordinates": [zone["lon"], zone["lat"]]},
        "datetime": since.strftime("%Y-%m-%dT00:00:00Z") + "/..",
        "limit": 100,
        "sortby": [{"field": "properties.datetime", "direction": "asc"}],
    }
    items = []
    url = STAC
    while url:
        req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json", "User-Agent": "SurfLog-collector"})
        with urllib.request.urlopen(req, timeout=60) as r:
            page = json.load(r)
        items += page.get("features", [])
        nxt = next((l for l in page.get("links", []) if l.get("rel") == "next"), None)
        if not nxt:
            break
        url, body = nxt["href"], nxt.get("body", body)
    return items


def tide_at(zone, date, time):
    """La marée au moment de la photo, d'après le niveau de la mer d'Open-Meteo (heure par heure, UTC) :
    niveau (m), état (basse, haute, montante, descendante) et marnage du jour (m). None si la donnée manque."""
    day = f"{date[:4]}-{date[4:6]}-{date[6:]}"
    url = (f"https://marine-api.open-meteo.com/v1/marine?latitude={zone['lat']}&longitude={zone['lon']}"
           f"&hourly=sea_level_height_msl&start_date={day}&end_date={day}&timezone=UTC")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "SurfLog-collector (https://surflog.fr)"})
        with urllib.request.urlopen(req, timeout=30) as r:
            levels = json.load(r)["hourly"]["sea_level_height_msl"]
        if len(levels) < 24 or any(v is None for v in levels):
            return None
        hour = int(time[:2]) + int(time[3:5]) / 60.0
        h0 = min(int(hour), 22)
        level = levels[h0] + (levels[h0 + 1] - levels[h0]) * (hour - h0)
        low, high = min(levels), max(levels)
        pos = (level - low) / (high - low) if high > low else 0.5
        rising = levels[h0 + 1] > levels[h0]
        state = "basse" if pos < 0.25 else "haute" if pos > 0.75 else ("montante" if rising else "descendante")
        return {"level": round(level, 2), "state": state, "range": round(high - low, 1)}
    except Exception as e:
        print(f"marée {date} : indisponible ({e})")
        return None


def warp(href, fr, bands, resampling):
    out = np.zeros((len(bands), fr["height"], fr["width"]), dtype=np.uint8)
    with rasterio.open(href) as src:
        for k, b in enumerate(bands):
            reproject(rasterio.band(src, b), out[k], dst_transform=fr["transform"], dst_crs="EPSG:4326", resampling=resampling)
    return out


def main():
    data_dir = sys.argv[1]
    days = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    since = datetime.now(timezone.utc) - timedelta(days=days)
    for name, zone in ZONES.items():
        fr = frame(zone)
        folder = os.path.join(data_dir, "sat", name)
        os.makedirs(folder, exist_ok=True)
        index_path = os.path.join(folder, "index.json")
        index = json.load(open(index_path)) if os.path.exists(index_path) else {"images": []}
        known = {i["date"] for i in index["images"]}
        added = 0
        for item in search(zone, since):
            stamp = item["properties"]["datetime"]
            date = stamp[:10].replace("-", "")
            if date in known:
                continue
            try:
                rgb = warp(item["assets"]["visual"]["href"], fr, [1, 2, 3], Resampling.bilinear)
                valid = float((rgb.max(axis=0) > 0).mean())
                if valid < 0.9:  # bord de fauchée : la bande n'est pas entière
                    print(f"{name} {date} : bande incomplète ({valid:.0%}), sautée")
                    continue
                scl = warp(item["assets"]["scl"]["href"], fr, [1], Resampling.nearest)[0]
                cloud = np.isin(scl, (8, 9))  # nuages moyens et épais ; les cirrus fins (10) laissent voir la côte
                blocks = [int(round(100 * float(cloud[r:r + BLOCK_ROWS].mean()))) for r in range(0, fr["height"], BLOCK_ROWS)]
                Image.fromarray(np.transpose(rgb, (1, 2, 0))).save(os.path.join(folder, date + ".jpg"), "JPEG", quality=78, optimize=True)
                index["images"].append({"date": date, "time": stamp[11:16], "file": date + ".jpg", "cloud": blocks, "tide": tide_at(zone, date, stamp[11:16])})
                known.add(date)
                added += 1
                print(f"{name} {date} : rangée, nuages {int(round(100 * float(cloud.mean())))} %")
            except Exception as e:  # une photo illisible ne bloque pas les autres
                print(f"{name} {date} : échec ({e})")
        for image in index["images"]:  # marée manquante (donnée indisponible le jour de la collecte) : nouvel essai
            if not image.get("tide"):
                image["tide"] = tide_at(zone, image["date"], image["time"])
        index.update({k: fr[k] for k in ("width", "height", "lat_top", "lat_bottom", "lon_left", "lon_right")})
        index.update({"zone": name, "lat": zone["lat"], "lon": zone["lon"], "pixel_m": PIXEL_M, "block_rows": BLOCK_ROWS,
                      "source": "Copernicus Sentinel-2 (ESA), niveau 2A"})
        index["images"].sort(key=lambda i: i["date"])
        json.dump(index, open(index_path, "w"), separators=(",", ":"))
        print(f"{name} : {added} photo(s) ajoutée(s), {len(index['images'])} en tout")


if __name__ == "__main__":
    main()
