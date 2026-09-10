import unittest
from pilot import render


class PilotTests(unittest.TestCase):
    def test_only_version_changes_and_attempts_are_distinct(self):
        source = "artifact:\n  version: 1.0.0 # x-release-please-version\n  path: target/Sigil.jar\nrelease:\n  mode: test-only\n"
        version, actual = render(source, "1.0.0", "123", "1")
        self.assertEqual(version, "1.0.0-provenance.123.1")
        self.assertEqual(actual, source.replace("1.0.0 # x-release-please-version", version))
        self.assertNotEqual(version, render(source, "1.0.0", "123", "2")[0])

    def test_invalid_inputs_fail_closed(self):
        source = "  version: 1.0.0 # x-release-please-version\n  mode: test-only\n"
        for args in [(source, "1.0.0", "../x", "1"), (source, "1.0.0", "1", "0"), (source, "2.0.0", "1", "1"), (source.replace("test-only", "automatic"), "1.0.0", "1", "1"), (source + source, "1.0.0", "1", "1")]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                render(*args)


if __name__ == "__main__":
    unittest.main()
