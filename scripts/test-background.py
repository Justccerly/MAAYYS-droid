"""Run real MAA-Meow display tests on an explicitly specified, root-adbd test emulator.
Does not change the app's production Root/Shizuku authorization path.
"""
import argparse
import os
import pathlib
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--method', default='virtualDisplayCapturesAndInjectsInputWithoutReplacingForeground')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
adb = pathlib.Path(os.environ.get('ANDROID_HOME', pathlib.Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))/'platform-tools/adb.exe'
base = [str(adb), '-s', args.serial]
if subprocess.check_output(base+['shell','id','-u'],text=True).strip() != '0':
    raise SystemExit('Use an isolated emulator with adb root enabled, not a production phone.')
out = root/'build/background-validation'; out.mkdir(parents=True,exist_ok=True)
command_file = '/data/user/0/com.maayys.android/cache/background-test-command.txt'
subprocess.run(base+['shell','rm','-f',command_file],check=True)
with (out/(args.method+'.txt')).open('w',encoding='utf-8') as log:
    run = subprocess.Popen(base+['shell','am','instrument','-w','-e','class',
        'com.maayys.host.BackgroundPlatformTest#'+args.method,
        'com.maayys.android.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT)
    server = None
    server_log = (out/(args.method+'-server.txt')).open('w',encoding='utf-8')
    try:
        deadline = time.monotonic()+120
        while run.poll() is None:
            if time.monotonic()>deadline: run.terminate(); raise RuntimeError('Device test timed out; inspect logs')
            if server is None:
                command = subprocess.run(base+['shell','cat',command_file],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
                if command.returncode == 0 and command.stdout.strip():
                    server = subprocess.Popen(base+['shell',command.stdout.strip()],stdout=server_log,stderr=subprocess.STDOUT)
            time.sleep(.4)
    finally:
        if server is not None:
            try: server.wait(timeout=5)
            except subprocess.TimeoutExpired: server.terminate()
        server_log.close()
result = (out/(args.method+'.txt')).read_text(encoding='utf-8')
print(result)
if 'OK (1 test)' not in result: raise SystemExit(1)
