"""Check Android resource coverage, UI references and localized upload-page behavior."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
JAVA = ROOT / 'app/src/main/java/local/mitv/launcher'


def resources(folder):
    result = {}
    for path in (RES / folder).glob('*.xml'):
        for entry in ET.parse(path).getroot():
            if entry.tag != 'string':
                continue
            name = entry.attrib['name']
            if name in result:
                raise AssertionError('Duplicate resource: ' + name)
            value = entry.text or ''
            if value.startswith('"') and value.endswith('"'):
                value = value[1:-1]
                escapes = {'n': '\n', 'r': '\r', 't': '\t', '"': '"', "'": "'", '\\': '\\'}
                value = re.sub(r'\\([nrt\"\x27\\])', lambda match: escapes[match[1]], value)
            result[name] = value
    return result


class LocaleTests(unittest.TestCase):
    def test_english_resources_cover_defaults_and_format_arguments(self):
        chinese, english = resources('values'), resources('values-en')
        self.assertEqual(chinese.keys(), english.keys())
        self.assertTrue(all(english.values()))
        self.assertFalse(any(re.search(r'[\u4e00-\u9fff]', value) for value in english.values()))
        for name in chinese:
            self.assertEqual(re.findall(r'%\d+\$[sd]', chinese[name]), re.findall(r'%\d+\$[sd]', english[name]), name)

    def test_ui_uses_declared_resource_ids(self):
        declared = resources('values')
        for path in JAVA.glob('*.java'):
            for name in re.findall(r'(?<!android\.)R\.string\.([a-z0-9_]+)', path.read_text('utf-8')):
                self.assertIn(name, declared, path.name)
        for filename in ('MainActivity', 'AppearanceActivity', 'SettingsDialog', 'HomePreviewView',
                         'InputSettings', 'SaverLibraryActivity', 'SaverSettings', 'NewsReaderActivity',
                         'ScreensaverActivity', 'DeviceActions', 'PreviewController', 'PhotoUploadServer'):
            source = (JAVA / (filename + '.java')).read_text('utf-8')
            self.assertNotRegex(source, r'"[^"\n]*[\u4e00-\u9fff]', filename)

    def test_chinese_defaults_remain_chinese(self):
        values = resources('values')
        self.assertEqual(values['app_name'], '简净桌面')
        self.assertEqual(values['appearance_content'], '外观与内容')
        self.assertEqual(values['switch_off'], '关闭')

    def test_upload_pages_keep_code_autofill_and_request_validation(self):
        for folder, language in (('raw', 'zh-CN'), ('raw-en', 'en')):
            page = (RES / folder / 'upload_page.html').read_text('utf-8')
            self.assertIn("lang='" + language + "'", page)
            self.assertIn("window.addEventListener('hashchange',connectFromScan)", page)
            self.assertIn('connectFromScan();', page)
            self.assertIn(r'if(!/^\d{6}$/.test(c)||!f.length)', page)
            self.assertIn("'X-QuietTV-Code':c", page)
            self.assertIn("method:'POST'", page)
        self.assertNotRegex((RES / 'raw-en/upload_page.html').read_text('utf-8'), r'[\u4e00-\u9fff]')


if __name__ == '__main__':
    unittest.main()
