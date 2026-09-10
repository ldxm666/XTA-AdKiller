"""Inject classes.dex and META-INF/xposed/* into a base APK zip."""
import sys, zipfile, os

apk_path, dex_path, res_root = sys.argv[1], sys.argv[2], sys.argv[3]

tmp = apk_path + ".tmp"
with zipfile.ZipFile(apk_path, 'r') as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        zout.writestr(item, data)
    # classes.dex stored with deflate is fine
    with open(dex_path, 'rb') as f:
        zout.writestr('classes.dex', f.read())
    meta_dir = os.path.join(res_root, 'META-INF', 'xposed')
    for name in sorted(os.listdir(meta_dir)):
        with open(os.path.join(meta_dir, name), 'rb') as f:
            zout.writestr('META-INF/xposed/' + name, f.read())
os.replace(tmp, apk_path)
print("packed:", apk_path)
