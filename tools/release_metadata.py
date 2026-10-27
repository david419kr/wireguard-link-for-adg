"""Write dependency notices, a CycloneDX SBOM and checksums for a built release."""
import hashlib
import json
import pathlib
import re
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent


def sha256(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=pathlib.Path)
    args = parser.parse_args()
    apk = args.apk.resolve()
    build = json.loads((ROOT / "artifacts/build-info.json").read_text(encoding="utf-8"))
    if build["sha256"] != sha256(apk):
        raise ValueError("Build verification metadata does not match this APK")
    version = re.search(r'android:versionName="([^"]+)"', (ROOT / "app/AndroidManifest.xml").read_text(encoding="utf-8")).group(1)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    components = []
    for name, dep in json.loads((ROOT / "third-party/java-dependencies.json").read_text(encoding="utf-8")).items():
        components.append({"type": "library", "name": name, "version": dep["version"],
                           "licenses": [{"license": {"id": dep["license"]}}],
                           "hashes": [{"alg": "SHA-256", "content": dep["sha256"]}],
                           "externalReferences": [{"type": "distribution", "url": dep["url"]}],
                           "description": dep.get("scope", "Packaged Java dependency")})
    module_licenses = {"golang.zx2c4.com/wireguard": "MIT", "gvisor.dev/gvisor": "Apache-2.0",
                       "github.com/google/btree": "Apache-2.0"}
    module_licenses.update({"golang.org/x/" + name: "BSD-3-Clause" for name in ("crypto", "net", "sys", "time")})
    libraries = {}
    dependency_sets = []
    go_versions = set()
    for abi in build["abis"]:
        file = ROOT / f"native/out/{abi}/libwgbridge.so"
        libraries[abi] = sha256(file)
        output = subprocess.check_output(["go", "version", "-m", str(file)], text=True)
        go_versions.add(output.splitlines()[0].split()[-1].removeprefix("go"))
        dependency_sets.append({tuple(line.strip().split()[1:]) for line in output.splitlines() if line.strip().startswith("dep\t")})
    if any(deps != dependency_sets[0] for deps in dependency_sets[1:]) or len(go_versions) != 1:
        raise ValueError("Native dependencies differ across ABIs")
    for module, mod_version, checksum in sorted(dependency_sets[0]):
        components.append({"type": "library", "name": module, "version": mod_version,
                           "purl": f"pkg:golang/{module}@{mod_version}",
                           "licenses": [{"license": {"id": module_licenses[module]}}],
                           "properties": [{"name": "go:module:h1", "value": checksum}]})
    components.append({"type": "library", "name": "Go runtime and standard library", "version": next(iter(go_versions)),
                       "licenses": [{"license": {"id": "BSD-3-Clause"}}]})
    out = ROOT / "artifacts"
    notices = out / f"wireguard-link-for-adg-{version}-notices.zip"
    with zipfile.ZipFile(notices, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in ("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"):
            archive.write(ROOT / name, name)
        for file in sorted((ROOT / "third-party").rglob("*")):
            if file.is_file():
                archive.write(file, file.relative_to(ROOT).as_posix())
    sbom = {"bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
            "metadata": {"component": {"type": "application", "name": "WireGuard Link for AdG", "version": version,
                         "licenses": [{"license": {"id": "Apache-2.0"}}],
                         "hashes": [{"alg": "SHA-256", "content": sha256(apk)}],
                         "externalReferences": [{"type": "vcs", "url": "https://github.com/david419kr/wireguard-link-for-adg/tree/" + commit}]}},
            "components": components}
    sbom_file = out / f"wireguard-link-for-adg-{version}-sbom.json"
    sbom_file.write_text(json.dumps(sbom, indent=2) + "\n", encoding="utf-8")
    build.update(source_commit=commit, version=version, native_sha256=libraries)
    (out / "build-info.json").write_text(json.dumps(build, indent=2) + "\n", encoding="utf-8")
    files = [apk, sbom_file, notices, out / "build-info.json"]
    (out / "SHA256SUMS.txt").write_text("".join(f"{sha256(file)}  {file.name}\n" for file in files), encoding="utf-8")
    print(f"Release metadata: {version}, source {commit}, {len(components)} dependencies")


if __name__ == "__main__":
    main()
