#!/usr/bin/env python3
"""Regression tests for the JSON benchmark comparator."""
import unittest
from compare_bench import compare


def fixture():
    return {
        "status": "completed",
        "scenario": "sprite_1k",
        "effect": "pyro_vfx:bench/static",
        "effectSha256": "a" * 64,
        "seed": 123,
        "requestedParticles": 1000,
        "warmupTicks": 30,
        "measurementTicks": 100,
        "measurementFrameSamples": 120,
        "measurementTickSamples": 100,
        "medianFrameMs": 8.0,
        "p95FrameMs": 10.0,
        "medianSimulationMs": 1.0,
        "p95SimulationMs": 1.2,
        "medianRenderSubmissionMs": 0.3,
        "p95RenderSubmissionMs": 0.4,
        "environment": {
            "minecraft": "1.21.1",
            "gpuRenderer": "GPU",
            "openGlVersion": "4.6",
            "width": 1920,
            "height": 1080,
            "world": "Test VFX",
            "renderDistance": 12,
            "fov": 70,
            "vsync": False,
        },
    }


class BenchmarkComparisonTests(unittest.TestCase):
    def test_identical_results_pass(self):
        code, report = compare(fixture(), fixture(), 0.15)
        self.assertEqual(0, code)
        self.assertEqual("pass", report["status"])

    def test_significant_regression_fails(self):
        candidate = fixture()
        candidate["p95FrameMs"] = 12.5
        code, report = compare(fixture(), candidate, 0.15)
        self.assertEqual(2, code)
        self.assertIn("p95FrameMs", report["regressions"])

    def test_hardware_or_resolution_change_is_incomparable(self):
        candidate = fixture()
        candidate["environment"]["width"] = 1280
        code, report = compare(fixture(), candidate, 0.15)
        self.assertEqual(3, code)
        self.assertEqual("incomparable", report["status"])

    def test_incomplete_test_cannot_pass(self):
        candidate = fixture()
        candidate["measurementFrameSamples"] = 4
        code, _ = compare(fixture(), candidate, 0.15)
        self.assertEqual(3, code)

    def test_missing_metric_is_unavailable_not_fabricated(self):
        candidate = fixture()
        candidate["medianSimulationMs"] = None
        code, report = compare(fixture(), candidate, 0.15)
        self.assertEqual(0, code)
        self.assertEqual({"status": "unavailable"}, report["metrics"]["medianSimulationMs"])


if __name__ == "__main__":
    unittest.main()
