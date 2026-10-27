"""Package only the app's own resources/code, pinned OSS classes and the app-owned backend."""
import hashlib
import json
import pathlib
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def prepare():
    with zipfile.ZipFile(ROOT / "build/dependencies/wireguard-config.jar") as src, zipfile.ZipFile(ROOT / "build/wireguard-config-only.jar", "w") as dst:
        for name in src.namelist():
            if name.endswith(".class") and name.startswith(("com/wireguard/config/", "com/wireguard/crypto/", "com/wireguard/util/NonNullForAll")):
                dst.writestr(name, src.read(name))


def assemble():
    with zipfile.ZipFile(ROOT / "build/resources.apk") as src, zipfile.ZipFile(ROOT / "build/unsigned.apk", "w") as dst:
        for name in src.namelist():
            dst.writestr(name, src.read(name))
        dst.write(ROOT / "build/dex/classes.dex", "classes.dex")
        for abi in ABIS:
            dst.write(ROOT / f"native/out/{abi}/libwgbridge.so", f"lib/{abi}/libwgbridge.so")
        for file in (ROOT / "third-party/licenses").iterdir():
            if file.is_file():
                dst.write(file, "assets/licenses/" + file.name)
        for name in ("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"):
            dst.write(ROOT / name, "assets/licenses/" + name)


def verify(file):
    file = pathlib.Path(file)
    with zipfile.ZipFile(file) as archive:
        names = set(archive.namelist())
        assert {n for n in names if n.endswith(".so")} == {f"lib/{abi}/libwgbridge.so" for abi in ABIS}
        assert {n for n in names if n.endswith(".dex")} == {"classes.dex"}
        assert archive.read("classes.dex") == (ROOT / "build/dex/classes.dex").read_bytes()
        with zipfile.ZipFile(ROOT / "build/resources.apk") as resources:
            for name in resources.namelist():
                assert archive.read(name) == resources.read(name)
        for abi in ABIS:
            assert archive.read(f"lib/{abi}/libwgbridge.so") == (ROOT / f"native/out/{abi}/libwgbridge.so").read_bytes()
        assert "assets/licenses/THIRD_PARTY_NOTICES.md" in names
    report = {"apk": file.name, "sha256": hashlib.sha256(file.read_bytes()).hexdigest(), "bytes": file.stat().st_size,
              "abis": list(ABIS), "own_compiled_resources_verified": True, "only_app_backend_packaged": True,
              "original_app_apk_required": False}
    (ROOT / "artifacts/build-info.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    {"prepare": prepare, "assemble": assemble, "verify": lambda: verify(sys.argv[2])}[sys.argv[1]]()
