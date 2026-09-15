"""Exercise independent update buttons against a held HTTPS proxy on a test emulator.
Restores the emulator proxy afterwards; does not inspect CDKs or decrypt HTTPS.
"""
import argparse
import http.server
import json
import os
from pathlib import Path
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = root / 'build/update-button-validation'
out.mkdir(parents=True, exist_ok=True)
adb = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
base = [str(adb), '-s', args.serial]
def run(*args):
    return subprocess.check_output(base + list(args), text=True, encoding='utf-8').strip()

gate = threading.Event()
requests = []
class Proxy(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_CONNECT(self):
        requests.append(time.monotonic())
        gate.wait(22)
        try: self.send_error(503, 'Intentional test response')
        except OSError: pass

server = http.server.ThreadingHTTPServer(('127.0.0.1', 18765), Proxy)
server.daemon_threads = True
threading.Thread(target=server.serve_forever, daemon=True).start()
previous = run('shell', 'settings', 'get', 'global', 'http_proxy')

def hierarchy(name):
    # MuMu's uiautomator sometimes crashes during JVM teardown after writing XML.
    for attempt in range(4):
        run('shell', 'rm', '-f', '/sdcard/update-buttons.xml')
        subprocess.run(base+['shell','uiautomator','dump','/sdcard/update-buttons.xml'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        exists = subprocess.run(base+['shell','test','-s','/sdcard/update-buttons.xml']).returncode == 0
        if exists: break
        time.sleep(.5)
    assert exists, 'UI hierarchy unavailable'
    run('pull', '/sdcard/update-buttons.xml', str(out / (name + '.xml')))
    return ET.parse(out / (name + '.xml')).getroot()

def buttons(tree):
    # Capture each actual clickable ancestor, not only the Text composable.
    labels = {'检查更新', '再次检查', '检查中…'}
    parents = {child: parent for parent in tree.iter() for child in parent}
    result = []
    for text in tree.iter('node'):
        if text.get('text') not in labels: continue
        node = text
        while node.get('clickable') != 'true' and node in parents: node = parents[node]
        # Disabled Compose buttons retain an enabled=false ancestor.
        chain = text
        enabled = True
        while chain is not node:
            enabled &= chain.get('enabled') != 'false'
            chain = parents[chain]
        enabled &= node.get('enabled') != 'false'
        bounds = list(map(int, re.findall(r'\d+', text.get('bounds'))))
        result.append({'text': text.get('text'), 'enabled': enabled, 'bounds': bounds})
    return sorted(result, key=lambda b: b['bounds'][1])

def tap(button):
    x1, y1, x2, y2 = button['bounds']
    run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

try:
    run('reverse', 'tcp:18765', 'tcp:18765')
    run('shell', 'settings', 'put', 'global', 'http_proxy', '127.0.0.1:18765')
    for first in [1, 0]:
        label = 'core-first' if first == 1 else 'resource-first'
        run('shell', 'am', 'force-stop', 'com.maayys.android')
        run('shell', 'am', 'start', '-n', 'com.maayys.android/com.maayys.host.MainActivity')
        found = []
        for i in range(10):
            tree = hierarchy('initial')
            found = buttons(tree)
            if len(found) == 2: break
            bounds = list(map(int, re.findall(r'\d+', next(tree.iter('node')).get('bounds'))))
            _, _, width, height = bounds
            run('shell', 'input', 'swipe', str(width//2), str(height*3//4), str(width//2), str(height//4), '400')
        assert len(found) == 2, 'Both update buttons must be visible'
        gate.clear()
        start_count = len(requests)
        found = buttons(hierarchy(label+'-before'))
        assert all(b['enabled'] for b in found), found
        tap(found[first])
        deadline = time.monotonic()+8
        while len(requests) == start_count and time.monotonic() < deadline: time.sleep(.1)
        assert len(requests) > start_count, 'Request did not reach the controlled proxy'
        found = buttons(hierarchy(label+'-waiting'))
        assert found[first]['text'] == '检查中…' and not found[first]['enabled'], found
        assert found[1-first]['text'] != '检查中…' and found[1-first]['enabled'], found
        tap(found[1-first])
        deadline = time.monotonic()+8
        while len(requests) < start_count+2 and time.monotonic() < deadline: time.sleep(.1)
        assert len(requests) >= start_count+2, 'Other request was blocked by first request'
        found = buttons(hierarchy(label+'-both'))
        assert all(b['text'] == '检查中…' and not b['enabled'] for b in found), found
        gate.set()
        for attempt in range(5):
            found = buttons(hierarchy(label+'-completed'))
            if all(b['enabled'] for b in found): break
        assert all(b['enabled'] and b['text'] != '检查中…' for b in found), found
        print(label + ': PASS', flush=True)
    (out/'result.json').write_text(json.dumps({'core_first':'passed','resource_first':'passed','concurrent_requests':'passed'},indent=2))
finally:
    gate.set()
    if previous in ('null', ''): run('shell','settings','delete','global','http_proxy')
    else: run('shell','settings','put','global','http_proxy',previous)
    run('reverse','--remove','tcp:18765')
    server.shutdown()
