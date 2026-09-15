"""Download and verify official MaaFramework Android native libraries, pinned to 5.13.0."""
import hashlib
import pathlib
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/downloads'
CACHE.mkdir(parents=True, exist_ok=True)
for arch, abi, digest in [
    ('aarch64', 'arm64-v8a', 'a3511c179d985118f7db0f8cc0fa98f3b2c3c17084186962997f8b29e7a8da41'),
    ('x86_64', 'x86_64', 'f85998149743291d3254f43aed8a1cee815da5542d136db292ab3dbdfb219875'),
]:
    name = f'MAA-android-{arch}-v5.13.0.zip'
    archive = CACHE / name
    if not archive.exists():
        urllib.request.urlretrieve('https://github.com/MaaXYZ/MaaFramework/releases/download/v5.13.0/' + name, archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise SystemExit('Checksum mismatch: ' + name)
    dest = ROOT / 'app/src/main/jniLibs' / abi
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for member in z.namelist():
            path = pathlib.PurePosixPath(member)
            if path.suffix == '.so' and path.name != 'libMaaPluginDemo.so':
                (dest / path.name).write_bytes(z.read(member))
    print('Prepared MaaFramework 5.13.0: ' + abi)
