import numpy as np, json, sys
from PIL import Image
from scipy import ndimage as ndi
X = 2   # facteur par rapport aux photos d'origine (1376 x 768) : 2752 x 1536
def load_x2(path):
    im = Image.open(path).convert('RGB'); return im.resize((1376*X, 768*X), Image.LANCZOS)
def unmix(im):
    P = np.asarray(im, dtype=np.float64) / 255.0
    R = P[..., 0]; sky = np.median(P[R < np.percentile(R, 30)], axis=0)
    A = np.stack([np.ones(3), sky], axis=1); pinv = np.linalg.pinv(A)
    tu = P @ pinv.T; t, u = tu[..., 0], tu[..., 1]
    a = np.clip(1 - u, 0, 1); c = np.clip(np.where(a > 0.02, t / np.maximum(a, 1e-3), 1.0), 0, 1)
    return a.astype(np.float32), c.astype(np.float32)
def cut(a, c, y0, y1, x0, x1, keep=None, name=""):
    al = a[y0:y1, x0:x1].copy()
    if keep is not None: al = al * ndi.gaussian_filter(keep[y0:y1, x0:x1], 3 * X)
    hh, ww = al.shape; f = 14.0 * X
    yy = np.minimum(np.arange(hh), np.arange(hh)[::-1]) / f; xx = np.minimum(np.arange(ww), np.arange(ww)[::-1]) / f
    edge = np.clip(np.minimum(yy[:, None], xx[None, :]), 0, 1)
    al = al * np.where(al > 0.0, np.clip(edge, 0, 1) ** 0.7, 0)
    cc = c[y0:y1, x0:x1]
    ys, xs = np.where(al > 0.04)
    if len(ys) == 0: return None
    ty0, ty1, tx0, tx1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    return dict(a=al[ty0:ty1, tx0:tx1], c=cc[ty0:ty1, tx0:tx1], area=float((al > 0.3).sum()) / (X * X), name=name)
def build(pa, pb):
    aa, ca = unmix(load_x2(pa)); ab, cb = unmix(load_x2(pb)); S = []
    lab, n = ndi.label(aa > 0.4)
    for i, sl in enumerate(ndi.find_objects(lab), start=1):
        area = (lab[sl] == i).sum() / (X * X)
        if area < 3000: continue
        reg = (lab == i); grown = ndi.binary_dilation(reg, iterations=10 * X) & ((lab == 0) | reg)
        ys, xs = sl; m = 14 * X
        s = cut(aa, ca, max(0, ys.start - m), ys.stop + m, max(0, xs.start - m), xs.stop + m, keep=grown.astype(np.float32), name=f"a{i}")
        if s: S.append(s)
    lb, nb = ndi.label(ab > 0.4); sizes = ndi.sum(ab > 0.4, lb, range(1, nb + 1)); mega = 1 + int(np.argmax(sizes))
    keep = ndi.binary_dilation(lb == mega, iterations=6 * X).astype(np.float32)
    s = cut(ab, cb, 15 * X, 482 * X, 160 * X, 1336 * X, keep=keep, name="massif")
    if s: S.append(s)
    return sorted(S, key=lambda s: -s["area"])
def pack(sprites, key, W=4096, gap=4):
    x = y = rowh = 0; pos = []
    for s in sprites:
        h, w = s[key].shape[:2]
        if x + w > W: x = 0; y += rowh + gap; rowh = 0
        pos.append((x, y, w, h)); x += w + gap; rowh = max(rowh, h)
    return pos, y + rowh
if __name__ == "__main__":
    S = build(sys.argv[1], sys.argv[2])
    for s in S:
        t = np.clip((s["c"] - 0.80) / 0.20, 0, 1) ** 1.5; s["t"] = t.astype(np.float32)
        h = s["a"].shape[0]
        # brume : version floue, à la taille d'origine (x1), gain 1,6 déjà intégré
        a1 = np.asarray(Image.fromarray(s["a"], mode="F").resize((max(2, s["a"].shape[1] // X), max(2, s["a"].shape[0] // X)), Image.LANCZOS))
        b = ndi.gaussian_filter(a1, max(1.0, a1.shape[0] * 0.05))
        s["hz"] = np.nan_to_num(np.clip(b * 1.6, 0, 1)).astype(np.float32)
    pos, H = pack(S, "a"); atlas = np.zeros((H, 4096, 4), np.uint8)
    for s, (x, y, w, h) in zip(S, pos):
        atlas[y:y+h, x:x+w, 0:3] = (s["t"] * 255 + .5).astype(np.uint8)[..., None]; atlas[y:y+h, x:x+w, 3] = (s["a"] * 255 + .5).astype(np.uint8)
    hpos, HH = pack(S, "hz"); hz = np.zeros((HH, 4096, 4), np.uint8)
    for s, (x, y, w, h) in zip(S, hpos):
        hz[y:y+h, x:x+w, 0:3] = 255; hz[y:y+h, x:x+w, 3] = (s["hz"] * 255 + .5).astype(np.uint8)
    Image.fromarray(atlas, "RGBA").save("atlas.png"); Image.fromarray(hz, "RGBA").save("atlas_flou.png")
    meta = dict(atlasWidth=4096, atlasHeight=int(H), hazeWidth=4096, hazeHeight=int(HH), x=X, sprites=[
        dict(id=i, name=s["name"], x=p[0], y=p[1], w=p[2], h=p[3], hx=q[0], hy=q[1], hw=q[2], hh=q[3], area=int(s["area"]))
        for i, (s, p, q) in enumerate(zip(S, pos, hpos))])
    json.dump(meta, open("atlas.json", "w"), indent=1)
    print(len(S), "sprites", atlas.shape, hz.shape, [ (s["name"], s["a"].shape) for s in S[:4] ])
