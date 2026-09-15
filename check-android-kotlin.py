#!/usr/bin/env python3
"""Source type-check against Robolectric Android API jar, NOT an APK build."""
import os
import pathlib
import subprocess
root = pathlib.Path(__file__).resolve().parent
tools = pathlib.Path(os.environ.get('MAAYYS_JVM_TOOLS', '/opt/maayys-jvm'))
if not (tools / 'android-all-15.jar').is_file():
    raise SystemExit('Missing android-all-15.jar (Robolectric 15-robolectric-12650502)')
cp = os.pathsep.join(str(p) for p in sorted(tools.glob('*.jar')))
out = root / 'build/android-source-check'
out.mkdir(parents=True, exist_ok=True)
base = ['java', '-cp', cp, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17']
with (out / 'result.txt').open('w') as report:
    for name, source, dependencies in [
        ('library', root / 'src/main/kotlin', cp),
        ('app', root / 'app/src/main/kotlin', cp + os.pathsep + str(out / 'library.jar'))
    ]:
        command = base + ['-classpath', dependencies, '-d', str(out / (name + '.jar'))] + [str(p) for p in sorted(source.rglob('*.kt'))]
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        text = name + ': exit=' + str(result.returncode) + '\n' + result.stdout
        print(text, end=''); report.write(text); report.flush()
        if result.returncode:
            raise SystemExit(result.returncode)
