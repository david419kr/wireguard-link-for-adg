"""Download only pinned public build dependencies; no original app APK is needed."""
import hashlib
import json
import pathlib
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEPENDENCIES = json.loads((ROOT / "third-party/java-dependencies.json").read_text())


def main():
    destination = ROOT / "build/dependencies"
    destination.mkdir(parents=True, exist_ok=True)
    for key, item in DEPENDENCIES.items():
        name = item["url"].rsplit("/", 1)[-1]
        file = destination / name
        if not file.exists() or hashlib.sha256(file.read_bytes()).hexdigest() != item["sha256"]:
            request = urllib.request.Request(item["url"], headers={"User-Agent": "WireGuardLink-build/1.0"})
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != item["sha256"]:
                raise RuntimeError(f"Dependency checksum mismatch: {name}")
            temporary = file.with_suffix(file.suffix + ".tmp")
            temporary.write_bytes(data)
            temporary.replace(file)
        if key == "wireguard_android":
            with zipfile.ZipFile(file) as archive:
                (destination / "wireguard-config.jar").write_bytes(archive.read("classes.jar"))
        print(f"Verified {name}")


if __name__ == "__main__":
    main()
