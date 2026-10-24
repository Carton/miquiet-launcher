"""Exercise recovery with a fake ADB; these tests never address a real TV."""

import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
FAKE_ADB = r'''
import json, os, pathlib, sys
args = sys.argv[1:]
root = pathlib.Path(os.environ['TV_TEST_DIR'])
with (root/'calls.jsonl').open('a', encoding='utf-8') as f:
    f.write(json.dumps(args)+'\n')
command = args[2:]
scenario = os.environ['TV_TEST_SCENARIO']
if command == ['shell', 'getprop', 'ro.product.model']:
    print('MiTV4-ANSM0')
elif command[:3] == ['shell', 'am', 'broadcast']:
    print('Broadcast completed: result=1')
elif command == ['shell', 'settings', 'get', 'secure', 'enabled_accessibility_services']:
    reads = root/'reads.txt'
    count = int(reads.read_text()) if reads.exists() else 0
    reads.write_text(str(count+1))
    if scenario == 'read_failure' or (scenario == 'readback_failure' and count > 0):
        print('error: device disconnected', file=sys.stderr)
        sys.exit(1)
    if scenario == 'empty_read':
        print('')
    else:
        state = root/'services.txt'
        print(state.read_text() if state.exists() else 'voice/.Service:local.mitv.launcher/local.mitv.launcher.HomeBridgeService:microphone/.Service')
elif command[:5] == ['shell', 'settings', 'put', 'secure', 'enabled_accessibility_services']:
    (root/'services.txt').write_text(command[5])
elif command == ['shell', 'appops', 'set', 'local.mitv.launcher', 'GET_USAGE_STATS', 'default']:
    pass
else:
    print('Unexpected fake command: '+repr(command), file=sys.stderr)
    sys.exit(2)
'''


class RestoreTests(unittest.TestCase):
    def execute(self, scenario):
        with tempfile.TemporaryDirectory(prefix="quiet-tv-recovery-") as folder:
            work = pathlib.Path(folder)
            (work / "fake_adb.py").write_text(FAKE_ADB, encoding="utf-8")
            (work / "adb.cmd").write_text(
                '@echo off\n"' + sys.executable + '" "' + str(work / "fake_adb.py") + '" %*\n',
                encoding="utf-8",
            )
            env = dict(os.environ, TV_TEST_DIR=folder, TV_TEST_SCENARIO=scenario)
            env["PATH"] = folder + os.pathsep + env["PATH"]
            result = subprocess.run(
                ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                 str(ROOT / "tools/restore_tv.ps1"), "-Serial", "test-device:5555"],
                env=env, capture_output=True, timeout=30,
            )
            calls = [json.loads(line) for line in (work / "calls.jsonl").read_text("utf-8").splitlines()]
            state = (work / "services.txt").read_text() if (work / "services.txt").exists() else None
            return result.returncode, calls, state

    def test_failed_read_never_writes_or_deletes_service_settings(self):
        code, calls, _ = self.execute("read_failure")
        self.assertNotEqual(code, 0)
        self.assertFalse(any("put" in call or "delete" in call for call in calls))

    def test_empty_successful_read_is_rejected_instead_of_clearing_services(self):
        code, calls, _ = self.execute("empty_read")
        self.assertNotEqual(code, 0)
        self.assertFalse(any("put" in call or "delete" in call for call in calls))

    def test_success_removes_only_own_service_and_reads_back_result(self):
        code, calls, state = self.execute("success")
        self.assertEqual(code, 0)
        self.assertEqual(state, "voice/.Service:microphone/.Service")
        self.assertEqual(sum(call[2:] == ["shell", "settings", "get", "secure", "enabled_accessibility_services"] for call in calls), 2)
        self.assertFalse(any("accessibility_enabled" in call for call in calls))
        self.assertIn(['-s', 'test-device:5555', 'shell', 'appops', 'set', 'local.mitv.launcher', 'GET_USAGE_STATS', 'default'], calls)

    def test_failed_readback_is_reported(self):
        code, _, state = self.execute("readback_failure")
        self.assertNotEqual(code, 0)
        self.assertEqual(state, "voice/.Service:microphone/.Service")


if __name__ == "__main__":
    unittest.main()
