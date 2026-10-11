import zipfile, pickle, numpy as np, collections
def load(path):
    z = zipfile.ZipFile(path)
    names = z.namelist()
    pkl = [n for n in names if n.endswith('data.pkl')][0]
    root = pkl[:-len('data.pkl')]
    class Storage:
        def __init__(self, key, dtype): self.key=key; self.dtype=dtype
    class U(pickle.Unpickler):
        def find_class(self, mod, name):
            if name == '_rebuild_tensor_v2':
                def rebuild(storage, offset, size, stride, *a):
                    raw = np.frombuffer(z.read(root+'data/'+storage.key), dtype=storage.dtype)
                    n = int(np.prod(size)) if len(size) else 1
                    return raw[offset:offset+n].reshape(size).copy()
                return rebuild
            if name == 'OrderedDict': return collections.OrderedDict
            if name.endswith('Storage'):
                dt = {'FloatStorage':np.float32,'HalfStorage':np.float16,'LongStorage':np.int64}[name]
                return dt
            return super().find_class(mod, name)
        def persistent_load(self, pid):
            # ('storage', storage_type, key, location, numel)
            return Storage(pid[2], pid[1])
    obj = U(z.open(pkl)).load()
    return obj
if __name__ == '__main__':
    o = load('general.pth')
    p = o.get('params', o)
    for k, v in list(p.items())[:6]: print(k, v.shape, v.dtype)
    print(len(p))
