"""Check standalone UI translations and their English fallback."""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = json.loads((ROOT / "localization/messages.json").read_text(encoding="utf-8"))
LITERAL = re.compile(r'"(?:\\.|[^"\\\r\n])*"')
HANGUL = re.compile("[가-힣]")


def internal_key(file, line):
    # Internal states are localized by their getters, never stored as translated text.
    if file == "Bridge.java":
        return "detail =" in line or "fail(" in line
    if file == "WireGuardPanel.java":
        return "static final String STORAGE_ERROR" in line
    if file == "TunnelController.java":
        return "state =" in line or "transport =" in line or "update(false," in line
    if file == "TunnelService.java":
        return any(token in line for token in ("TunnelController.update(", "TunnelController.failure(", "String mode ="))
    return False


def main():
    if "--sync" in sys.argv:
        block = "    static {\n" + "\n".join(
            "        EN.put(" + json.dumps(ko, ensure_ascii=False) + ", "
            + json.dumps(en, ensure_ascii=False) + ");" for ko, en in CATALOG.items()
        ) + "\n    }"
        for directory in ("app/src",):
            file = ROOT / directory / "com/adguard/vpn/wireguard/UiText.java"
            source, count = re.subn(r"    static \{.*?\n    \}", lambda _: block,
                                    file.read_text(encoding="utf-8"), count=1, flags=re.S)
            assert count == 1, "UiText initializer not found"
            file.write_text(source, encoding="utf-8")
    for key, value in CATALOG.items():
        assert value and not HANGUL.search(value), f"Invalid English translation for {key!r}"
    count = 0
    copies = []
    for directory in ("app/src",):
        ui = ROOT / directory / "com/adguard/vpn/wireguard/UiText.java"
        copies.append(ui.read_bytes())
        entries = {}
        for line in ui.read_text(encoding="utf-8").splitlines():
            if "EN.put(" in line:
                strings = LITERAL.findall(line)
                entries[json.loads(strings[0])] = json.loads(strings[1])
        assert entries == CATALOG, f"UiText catalog drift in {directory}"
        for file in (ROOT / directory).rglob("*.java"):
            if file.name == "UiText.java":
                continue
            for number, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
                for literal in LITERAL.finditer(line):
                    if not HANGUL.search(literal.group()):
                        continue
                    key = json.loads(literal.group())
                    assert key in CATALOG, f"Missing translation: {file}:{number}"
                    wrapped = re.search(r"UiText\.(?:t|format)\([^)]*$", line[:literal.start()])
                    assert wrapped or internal_key(file.name, line), f"Unlocalized UI literal: {file}:{number}"
                    count += 1
    assert all(copy == copies[0] for copy in copies), "Translation helper drift"
    print(f"UI translation audit passed: {len(CATALOG)} messages, {count} string uses in the app")


if __name__ == "__main__":
    main()
