"""Offline checks for deployment guardrails and public-repository scanning."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import audit_public
import enable_home
import screensaver_tv
import research_news


class EnableHomeTests(unittest.TestCase):
    def run_activation(self, original, model="TestTV", read_fails=False, service_label="简净桌面 Home 接管"):
        calls = []
        current = original

        def run(argv, **kwargs):
            nonlocal current
            command = argv[3:]
            calls.append(command)
            if command == ["shell", "getprop", "ro.product.model"]:
                value = model
            elif command == ["shell", "settings", "get", "secure", "enabled_accessibility_services"]:
                if read_fails:
                    raise subprocess.CalledProcessError(1, argv)
                value = current
            elif command == ["shell", "settings", "get", "secure", "accessibility_enabled"]:
                value = "1"
            elif command[:3] == ["shell", "am", "broadcast"]:
                value = "Broadcast completed: result=1"
            elif command[:5] == ["shell", "settings", "put", "secure", "enabled_accessibility_services"]:
                current = command[5]
                value = ""
            elif command == ["shell", "appops", "get", "local.mitv.launcher", "GET_USAGE_STATS"]:
                value = "GET_USAGE_STATS: allow"
            elif command == ["shell", "dumpsys", "accessibility"]:
                value = "Service[label=" + service_label + "]"
            else:
                value = ""
            return SimpleNamespace(stdout=(value + "\n").encode("utf-8"))

        error = None
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with patch.object(enable_home, "__file__", str(root / "tools/enable_home.py")), \
                    patch.object(sys, "argv", ["enable_home.py", "--serial", "test-device", "--expected-model", "TestTV"]), \
                    patch.object(enable_home.subprocess, "run", side_effect=run):
                try:
                    enable_home.main()
                except (RuntimeError, subprocess.CalledProcessError) as caught:
                    error = caught
            backups = list(root.rglob("*.json"))
            saved = json.loads(backups[0].read_text("utf-8")) if backups else None
        return error, calls, current, saved

    def test_empty_service_read_stops_before_activation(self):
        error, calls, _, saved = self.run_activation("")
        self.assertIsNotNone(error)
        self.assertIsNone(saved)
        self.assertFalse(any("put" in call or "broadcast" in call or "set" in call for call in calls))

    def test_failed_read_stops_before_activation(self):
        error, calls, _, _ = self.run_activation("null", read_fails=True)
        self.assertIsNotNone(error)
        self.assertFalse(any("put" in call or "broadcast" in call or "set" in call for call in calls))

    def test_model_mismatch_stops_before_activation(self):
        error, calls, _, _ = self.run_activation("null", model="OtherTV")
        self.assertIsNotNone(error)
        self.assertEqual(len(calls), 1)

    def test_activation_keeps_existing_services_and_backups(self):
        original = "voice/.Service:microphone/.Service"
        error, calls, current, saved = self.run_activation(original)
        self.assertIsNone(error)
        self.assertEqual(current, original + ":local.mitv.launcher/local.mitv.launcher.HomeBridgeService")
        self.assertEqual(saved["services"], original)
        self.assertIn(["shell", "appops", "set", "local.mitv.launcher", "GET_USAGE_STATS", "allow"], calls)

    def test_english_service_label_does_not_break_activation(self):
        error, _, current, _ = self.run_activation("voice/.Service", service_label="MiTV Quiet Launcher Home bridge")
        self.assertIsNone(error)
        self.assertIn("voice/.Service", current)


class ScreensaverTests(unittest.TestCase):
    def test_wrong_device_backup_is_not_applied(self):
        calls = []
        with tempfile.TemporaryDirectory() as folder:
            backup = Path(folder) / "timer.json"
            backup.write_text(json.dumps({"device": "different-device", "model": "TestTV", "system:screen_saver_timeout": "720000"}))

            def adb(argv, **kwargs):
                calls.append(argv)
                return b"TestTV\n" if "getprop" in argv else b"-2\n"

            with patch.object(sys, "argv", ["screensaver_tv.py", "restore", "--serial", "test-device",
                    "--expected-model", "TestTV", "--backup", str(backup)]), \
                    patch.object(screensaver_tv.subprocess, "check_output", side_effect=adb):
                with self.assertRaisesRegex(RuntimeError, "another device"):
                    screensaver_tv.main()
        self.assertFalse(any("put" in call or "delete" in call for call in calls))

    def test_missing_firmware_timer_is_not_created(self):
        calls = []
        with tempfile.TemporaryDirectory() as folder:
            backup = Path(folder) / "timer.json"

            def adb(argv, **kwargs):
                calls.append(argv)
                return b"TestTV\n" if "getprop" in argv else b"null\n"

            with patch.object(sys, "argv", ["screensaver_tv.py", "disable", "--serial", "test-device",
                    "--expected-model", "TestTV", "--backup", str(backup)]), \
                    patch.object(screensaver_tv.subprocess, "check_output", side_effect=adb):
                with self.assertRaisesRegex(RuntimeError, "no such timer"):
                    screensaver_tv.main()
            self.assertFalse(backup.exists())
        self.assertFalse(any("put" in call for call in calls))


class PrivacyTests(unittest.TestCase):
    def test_machine_addresses_and_paths_are_detected(self):
        address = ".".join(map(str, [192, 168, 9, 8]))
        self.assertIn("private_ipv4", audit_public.findings(address.encode()))
        local_path = "Q" + ":" + chr(92) + "workspace"
        self.assertIn("windows_absolute_path", audit_public.findings(local_path.encode()))
        self.assertFalse(audit_public.findings(b"https://example.test/ TV_SERIAL SDK_HOME"))

    def test_private_keys_and_credentials_are_detected_but_public_certs_are_allowed(self):
        self.assertIn("private_key", audit_public.findings(("-----BEGIN" + " PRIVATE KEY-----").encode()))
        self.assertIn("credential_pattern", audit_public.findings(("ghp_" + "a" * 40).encode()))
        self.assertFalse(audit_public.findings(b"-----BEGIN CERTIFICATE-----"))

    def test_private_artifacts_are_rejected(self):
        self.assertTrue(audit_public.private_file("nor/backup.json"))
        self.assertTrue(audit_public.private_file("release.keystore"))
        self.assertTrue(audit_public.private_file(".env.local"))
        self.assertFalse(audit_public.private_file("app/src/main/res/raw/isrg_root_x1.pem"))
        self.assertFalse(audit_public.private_file("third_party/tabler-icons/LICENSE"))


class ResearchNewsTests(unittest.TestCase):
    def inspect_game(self, status):
        import datetime
        import io
        now = datetime.datetime.now(research_news.SHANGHAI).strftime("%Y-%m-%d %H:%M")
        data = ("(" + json.dumps({"status": status, "body": '<div class="time">' + now + '</div>'}) + ");").encode()

        class Response(io.BytesIO):
            status = 200

        opener = SimpleNamespace(open=lambda *args, **kwargs: Response(data))
        with tempfile.TemporaryDirectory() as folder, \
                patch.object(research_news, "DESTINATION", Path(folder)), \
                patch.object(research_news.urllib.request, "build_opener", return_value=opener):
            return research_news.inspect(("game", "https://publisher.test/list"))

    def test_game_jsonp_is_inspected_as_html_list_not_rss(self):
        result = self.inspect_game("ok")
        self.assertEqual(result["items"], 1)
        self.assertEqual(result["recent"], 1)
        self.assertEqual(result["max_age_hours"], 48)
        self.assertEqual(result["file"], "game.json")

    def test_publisher_failure_does_not_look_like_success(self):
        with self.assertRaisesRegex(RuntimeError, "unavailable"):
            self.inspect_game("error")


if __name__ == "__main__":
    unittest.main()
