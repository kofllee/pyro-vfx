#!/usr/bin/env python3
"""Regression tests for selecting coherent M5 benchmark sessions."""
from __future__ import annotations

from datetime import datetime, timedelta
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).with_name("verify_m5.py")
SPEC = importlib.util.spec_from_file_location("verify_m5", SCRIPT)
assert SPEC and SPEC.loader
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)


def report(name: str, minute: int, commit: str) -> dict:
    return {"_file": f"{name}-20261102-{minute:06d}.json",
            "_scenario": name,
            "_stamp": datetime(2026, 11, 2, 1, 0) + timedelta(minutes=minute),
            "_commit": commit,
            "status": "completed",
            "measurementTickSamples": 100,
            "measurementFrameSamples": 4000,
            "peakActiveParticles": VERIFY.REQUIRED[name],
            "selectedParticleBackend": "gpu_analytic",
            "p95FrameMs": 1.0}


class VerifyM5Test(unittest.TestCase):
    def test_latest_complete_session_does_not_mix_revisions(self):
        suite = [report(name, i, "old") for i, name in enumerate(VERIFY.REQUIRED)]
        suite += [report("global_budget_spike", 9, "old"),
                  report("global_budget_spike", 10, "old")]
        newer_partial = [report("model_1k", 100, "new"),
                         report("sprite_lit_fallback", 101, "new")]
        chosen = VERIFY.select_session(suite + newer_partial, timedelta(minutes=15), 3)
        self.assertIsNotNone(chosen)
        self.assertEqual({r["_commit"] for r in chosen}, {"old"})
        self.assertEqual(len(chosen), len(VERIFY.REQUIRED) + 2)

    def test_incomplete_recent_session_never_borrows_historical_records(self):
        previous = [report(name, i, "old") for i, name in enumerate(VERIFY.REQUIRED)]
        recent = [report("model_1k", 100, "new")]
        chosen = VERIFY.select_session(recent, timedelta(minutes=15), 3)
        self.assertIsNone(chosen)
        self.assertIsNone(VERIFY.stamp_from_filename(Path("garbage.json"), "model_1k"))
        self.assertEqual(VERIFY.stamp_from_filename(
            Path("model_1k-20261105-173000.json"), "model_1k"),
            datetime(2026, 11, 5, 17, 30))

    def test_lit_sprite_and_model_backend_are_version_aware(self):
        rows = [report(name, i, "current") for i, name in enumerate(VERIFY.REQUIRED)]
        rows[0]["globalAdmittedSpawns"] = 30_000
        rows[0]["globalRejectedSpawns"] = 30_000
        rows[0]["globalRejectedEffects"] = 0
        rows[2]["collisionContacts"] = 1
        rows[2]["collisionEventObserved"] = True
        rows[3]["reloadCompleted"] = True
        rows += [dict(rows[0], _file="global-budget-second.json"),
                 dict(rows[0], _file="global-budget-dense.json",
                      selectedParticleBackend="cpu_dense")]
        rows[5]["selectedParticleBackend"] = "gpu_analytic"
        rows[6]["selectedParticleBackend"] = "cpu_dense"
        self.assertEqual(VERIFY.validate(rows, 3), [])


if __name__ == "__main__":
    unittest.main()
