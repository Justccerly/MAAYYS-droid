#!/usr/bin/env python3
"""Run pure JVM tests with preinstalled compiler jars; no Android SDK or su required."""
import os
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parent
jars = pathlib.Path(os.environ.get('MAAYYS_JVM_TOOLS', '/opt/maayys-jvm'))
classpath = os.pathsep.join(str(p) for p in sorted(jars.glob('*.jar')))
if not classpath:
    raise SystemExit('Missing compiler/JUnit jars: set MAAYYS_JVM_TOOLS')
main = root / 'src/main/kotlin/com/maayys'
sources = [str(p) for folder in ('agent', 'resource') for p in sorted((main / folder).glob('*.kt'))]
sources += [str(main / 'runtime' / name) for name in ('RuntimeApi.kt', 'RuntimeEvents.kt')]
sources += [str(main / 'app' / name) for name in ('ControllerMode.kt', 'AgentHost.kt', 'AgentService.kt', 'RootAgentFactory.kt', 'RuntimeStatusBus.kt')]
tests = sorted((root / 'src/test/kotlin').rglob('*Test.kt'))
out = root / 'build/jvm-validation'
out.mkdir(parents=True, exist_ok=True)
artifact = out / 'tests.jar'
commands = [
    ['java', '-cp', classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
     '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', classpath,
     '-d', str(artifact), *sources, *map(str, tests)],
    ['java', '-cp', str(artifact) + os.pathsep + classpath, 'org.junit.runner.JUnitCore',
     *['.'.join(p.relative_to(root / 'src/test/kotlin').with_suffix('').parts) for p in tests]]
]
with (out / 'result.txt').open('w') as log:
    for command in commands:
        result = subprocess.run(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        print(result.stdout, end='')
        log.write(result.stdout)
        log.flush()
        if result.returncode:
            raise SystemExit(result.returncode)
