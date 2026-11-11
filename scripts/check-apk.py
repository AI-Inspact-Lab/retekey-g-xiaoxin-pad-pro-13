#!/usr/bin/env python3
"""Checks a built APK for the mistakes that make Android refuse it with "There was a problem parsing
the package", before anyone has to find out on a device:

  - every activity, service, receiver and provider names a class that is in classes.dex;
  - every activity-alias targets an activity the manifest declares (a relative target such as
    ".ShareTargetActivity" resolves against the application id, not the code's package);
  - stored entries are aligned and every ZIP extra field is well-formed (scripts/zipalign.py).

    check-apk.py <app.apk> <path/to/aapt2>
"""
import os
import re
import subprocess
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import zipalign  # noqa: E402


def manifest_problems(apk, aapt2):
    tree = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk],
                          capture_output=True, text=True, check=True).stdout
    package = re.search(r'package="([^"]+)"', tree).group(1)
    elements, current = [], None
    for line in tree.splitlines():
        tag = re.match(r"\s*E: (\S+)", line)
        if tag:
            current = {"tag": tag.group(1)}
            elements.append(current)
            continue
        attr = re.search(r'android:(name|targetActivity)\(0x[0-9a-f]+\)="([^"]+)"', line)
        if attr and current is not None and attr.group(1) not in current:
            current[attr.group(1)] = attr.group(2)

    def full(name):
        if name.startswith("."):
            return package + name
        return name if "." in name else package + "." + name

    activities = {full(e["name"]) for e in elements if e["tag"] == "activity" and "name" in e}
    dex = zipfile.ZipFile(apk).read("classes.dex")
    problems = []
    for e in elements:
        if e["tag"] in ("activity", "service", "receiver", "provider") and "name" in e:
            cls = full(e["name"])
            if ("L" + cls.replace(".", "/") + ";").encode() not in dex:
                problems.append(f"{e['tag']} {cls}: class not in classes.dex")
        if e["tag"] == "activity-alias":
            target = full(e.get("targetActivity", ""))
            if target not in activities:
                problems.append(f"activity-alias {e.get('name')}: target {target} not declared")
    return problems


if __name__ == "__main__":
    apk_path, aapt2_path = sys.argv[1], sys.argv[2]
    found = manifest_problems(apk_path, aapt2_path) + zipalign.check(apk_path)
    if found:
        sys.exit("APK check failed:\n  " + "\n  ".join(found))
    print("APK check passed:", apk_path)
