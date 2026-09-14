#!/usr/bin/env python3
"""Exercise the exact distributed APK on a clean emulator, without credentials/API calls."""
import hashlib, json, re, subprocess, sys, time, traceback, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

APP = "cz.ctuprotebe.vyletnikviz.quality26"
ACTIVITY = APP + "/cz.ctuprotebe.vyletnikviz.MainActivity"
APK = Path("releases/VyletniKviz-2.6.0.apk")
OUT = Path("release-test-results")
OUT.mkdir(exist_ok=True)
result = {"apk_sha256": hashlib.sha256(APK.read_bytes()).hexdigest(), "api_calls": 0,
          "scenario": "Signed APK, 5 named players, 30 offline questions, UI taps", "checks": []}

def adb(*args, binary=False):
    r = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45)
    if r.returncode:
        raise RuntimeError("adb command failed: " + " ".join(args[:3]) + " " + r.stderr.decode(errors="replace")[-600:])
    return r.stdout if binary else r.stdout.decode(errors="replace")

def shell(*args):
    return adb("shell", *args)

def tree():
    for attempt in range(4):
        shell("uiautomator", "dump", "/sdcard/quiz-ui.xml")
        xml = shell("cat", "/sdcard/quiz-ui.xml")
        try:
            return ET.fromstring(xml[xml.index("<?xml"):])
        except (ET.ParseError, ValueError):
            time.sleep(.3)
    raise RuntimeError("No readable UI hierarchy")

def texts():
    return [n.get("text", "") for n in tree().iter("node") if n.get("text")]

def coords(n):
    x1,y1,x2,y2 = map(int, re.findall(r"\d+", n.get("bounds", "")))
    return (x1+x2)//2, (y1+y2)//2

def swipe(down):
    shell("input", "swipe", "500", "1750" if down else "500", "500", "500" if down else "1750", "160")

def seek(text, exact=False, scroll=True, cls=None):
    def match(n):
        t = n.get("text", "")
        return (t == text if exact else text in t) and n.get("enabled") == "true" and (cls is None or n.get("class") == cls)
    for direction in ([None, True, False] if scroll else [None]):
        previous = None
        for attempt in range(1 if direction is None else 20):
            if direction is not None:
                swipe(direction)
            nodes = list(tree().iter("node"))
            hits = [n for n in nodes if match(n)]
            if hits:
                return hits[0]
            signature = [(n.get("text"),n.get("bounds")) for n in nodes]
            if signature == previous:
                break
            previous = signature
    raise AssertionError("UI text not found: " + text + "; visible: " + repr(texts())[:1600])

def tap(text, exact=False):
    n = seek(text, exact)
    x,y = coords(n)
    shell("input", "tap", str(x), str(y))

def assert_text(text, exact=False):
    seek(text, exact)
    result["checks"].append(text)

def replace(old, new):
    n = seek(old, exact=True, cls="android.widget.EditText")
    x,y = coords(n)
    shell("input", "tap", str(x), str(y))
    shell("input", "keycombination", "113", "29")  # Ctrl+A; fields only use test data
    shell("input", "text", new)
    shell("input", "keyevent", "111")  # Escape closes keyboard without leaving Activity

def shot(name):
    (OUT/(name+".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    ET.ElementTree(tree()).write(OUT/(name+".xml"), encoding="utf-8")

def launch():
    shell("am", "start", "-W", "-n", ACTIVITY)

try:
    assert result["apk_sha256"] == "e5e5e054b202ec892974c99bbcbf219b6404eb6a7abc6b5145036005c2f2b1b5"
    result["android_api"] = shell("getprop", "ro.build.version.sdk").strip()
    adb("install", str(APK))
    shell("svc", "wifi", "disable")
    shell("svc", "data", "disable")
    launch()
    assert_text("VÝLETNÍ KVÍZ")
    shot("01-home")
    tap("Připojení k AI")
    assert_text("OpenAI klíč zatím není uložený")
    tap("Zpět", exact=True)
    tap("Začít nový výlet")
    assert_text("NOVÝ VÝLET")
    tap("Přidat fotky")
    assert any(n.get("package") not in (None, APP, "com.android.systemui") for n in tree().iter("node"))
    shot("02-photo-picker")
    shell("input", "keyevent", "4")
    assert_text("NOVÝ VÝLET")
    replace("Výlet", "Audit-Okoř".replace("ř","r"))
    replace("Barča", "Dan")
    replace("Dominik", "Kaja")
    for name in ["Barca", "Honza", "Petr"]:
        tap("+ Přidat hráče", exact=True)
        replace("Jméno hráče", name)
    tap("10 otázek", exact=True)
    tap("30 otázek", exact=True)
    tap("Připravit offline kvíz", exact=True)
    assert_text("Offline kvíz", exact=True)
    tap("Hrát všeobecný kvíz", exact=True)
    assert_text("Na tahu: Dan", exact=True)
    assert_text("Otázka 1 z 30")
    shot("03-five-player-game")
    tap("Neví – předat hráči Kaja", exact=True)
    assert_text("Přebírá: Kaja", exact=True)
    assert_text("za 0,6 bodu")
    with zipfile.ZipFile(APK) as z:
        questions = json.loads(z.read("assets/default_quiz.json"))["questions"][:30]
    def answer(q):
        tap("ABCD"[q["correct"]] + ")  " + q["options"][q["correct"]], exact=True)
        assert_text("PROČ:")
    answer(questions[0])
    assert_text("Kaja 0,6")
    tap("↶ Zpět", exact=True)
    assert_text("Přebírá: Kaja", exact=True)
    assert not any(t.startswith("PROČ:") for t in texts())
    tap("Vpřed ↷", exact=True)
    assert_text("Kaja 0,6")
    tap("Další otázka", exact=True)
    tap("Uložit rozehranou hru a skončit", exact=True)
    tap("Uložit a skončit", exact=True)
    shell("am", "force-stop", APP)
    launch()
    tap("Pokračovat v rozehrané hře")
    assert_text("Otázka 2 z 30")
    assert_text("Na tahu: Kaja", exact=True)
    assert_text("Kaja 0,6")
    shot("04-resumed-game")
    names = ["Dan", "Kaja", "Barca", "Honza", "Petr"]
    for i in range(1, 30):
        assert_text("Na tahu: " + names[i % 5], exact=True)
        assert_text("Otázka " + str(i+1) + " z 30")
        answer(questions[i])
        tap("Zobrazit výsledky" if i == 29 else "Další otázka", exact=True)
        print("Completed question", i+1, flush=True)
    assert_text("VÝSLEDKY", exact=True)
    assert_text("Kaja  •  6,6 bodu")
    assert_text("Vítězí Kaja")
    shot("05-results")
    tap("Otevřít kroniku", exact=True)
    assert_text("KRONIKA VÝLETŮ", exact=True)
    assert_text("Audit-Okor", exact=True)
    tap("Audit-Okor", exact=True)
    assert_text("vítěz: Kaja")
    assert_text("Otázky z výletu", exact=True)
    shot("06-chronicle")
    tap("Zpět do kroniky", exact=True)
    tap("Zpět", exact=True)
    tap("Dlouhodobá tabulka")
    assert_text("Kaja  •  1 výher")
    shot("07-leaderboard")
    shell("am", "force-stop", APP)
    adb("install", "-r", str(APK))  # same-signature reinstall must preserve local history
    launch()
    tap("Kronika výletů")
    assert_text("Audit-Okor", exact=True)
    result["passed"] = True
except Exception as e:
    result["passed"] = False
    result["error"] = str(e)
    traceback.print_exc()
    try: shot("failure")
    except Exception: pass
finally:
    result["checks_passed"] = len(result["checks"])
    (OUT/"summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({k:v for k,v in result.items() if k != "checks"}, ensure_ascii=False), flush=True)
if not result["passed"]:
    sys.exit(1)
