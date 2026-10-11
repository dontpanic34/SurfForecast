import numpy as np, sys, time
from PIL import Image
from load import load
P = load('general.pth'); P = P.get('params', P)
NC = 32
W = []
for i in range(0, 2*NC+3, 2):
    w = P[f'body.{i}.weight']; b = P[f'body.{i}.bias']
    W.append((w.transpose(2,3,1,0).reshape(-1, w.shape[0]).astype(np.float32), b.astype(np.float32)))  # (9*cin, cout)
A = [P[f'body.{i}.weight'].astype(np.float32) for i in range(1, 2*NC+2, 2)]   # PReLU
def conv(x, wb):   # x: H,W,C (already padded by 1)
    w, b = wb; H = x.shape[0]-2; Wd = x.shape[1]-2
    cols = np.concatenate([x[dy:dy+H, dx:dx+Wd, :] for dy in range(3) for dx in range(3)], axis=2)  # H,W,9C  (order dy,dx,c)
    return (cols.reshape(-1, cols.shape[2]) @ w + b).reshape(H, Wd, -1)
def net(x):  # x: H,W,3 in 0..1
    f = x
    for i, wb in enumerate(W[:-1]):
        f = conv(np.pad(f, ((1,1),(1,1),(0,0)), mode='edge'), wb)
        a = A[i]; f = np.where(f > 0, f, f * a)
    out = conv(np.pad(f, ((1,1),(1,1),(0,0)), mode='edge'), W[-1])      # H,W,48
    H, Wd = out.shape[:2]
    out = out.reshape(H, Wd, 3, 4, 4).transpose(0, 3, 1, 4, 2).reshape(H*4, Wd*4, 3)   # pixel shuffle (c, ry, rx)
    return out + np.repeat(np.repeat(x, 4, 0), 4, 1)
def upscale(img, tile=144, halo=34):
    H, Wd, _ = img.shape
    pad = np.pad(img, ((halo, halo+tile), (halo, halo+tile), (0, 0)), mode='reflect')
    out = np.zeros((H*4, Wd*4, 3), np.float32)
    t0 = time.time(); n = 0
    for y in range(0, H, tile):
        for x in range(0, Wd, tile):
            t = pad[y:y+tile+2*halo, x:x+tile+2*halo]
            o = net(t)
            h = min(tile, H-y); w = min(tile, Wd-x)
            out[y*4:(y+h)*4, x*4:(x+w)*4] = o[halo*4:(halo+h)*4, halo*4:(halo+w)*4]
            n += 1
        print(f'ligne {y}/{H} {time.time()-t0:.0f}s', flush=True)
    return np.clip(out, 0, 1)
if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    img = np.asarray(Image.open(src).convert('RGB'), dtype=np.float32) / 255
    if len(sys.argv) > 3:   # test : petit morceau
        img = img[:int(sys.argv[3]), :int(sys.argv[4])]
    up = upscale(img)
    Image.fromarray((up * 255 + .5).astype(np.uint8)).save(dst)
    print('ok', up.shape)
