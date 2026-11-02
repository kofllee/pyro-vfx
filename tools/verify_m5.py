#!/usr/bin/env python3
"""Check recorded M5 *real-client* reports, including cross-backend overload safety.

Run after collecting at least three independent global_budget_spike client
launches and the full compatibility smoke suite. This script never fabricates
benchmark samples or treats headless GameTests as GPU measurements.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from statistics import median

REQUIRED = {
    "global_budget_spike": 30_000,
    "analytic_dynamic_10k": 10_000,
    "collision_1k": 1_000,
    "reload_active": 1,
    "budget_spike": 20_000,
    "model_1k": 1_000,
    "sprite_lit_fallback": 1_000,
    "events": None,
    "uv_animation": None,
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, default=Path("run/pyrovfx-benchmarks"))
    parser.add_argument("--after", default="", help="UTC filename suffix threshold YYYYMMDD-HHMMSS")
    parser.add_argument("--min-global-runs", type=int, default=3)
    args = parser.parse_args()
    grouped: dict[str, list[dict]] = {name: [] for name in REQUIRED}
    problems: list[str] = []

    for path in args.folder.glob("*.json"):
        for name in grouped:
            prefix = name + "-"
            if not path.name.startswith(prefix):
                continue
            stamp = path.stem[len(prefix):]
            if stamp < args.after:
                break
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
            except (ValueError, OSError) as exc:
                problems.append(f"{path.name}: cannot read report: {exc}")
                break
            data["_file"] = path.name
            grouped[name].append(data)
            break

    for name, expected_peak in REQUIRED.items():
        records = grouped[name]
        if not records:
            problems.append(f"{name}: no reports after {args.after or 'start'}")
            continue
        if name == "global_budget_spike" and len(records) < args.min_global_runs:
            problems.append(f"{name}: {len(records)} launches; require {args.min_global_runs}")
        for record in records:
            file = record["_file"]
            if record.get("status") != "completed" or record.get("error"):
                problems.append(f"{file}: status={record.get('status')}: {record.get('error')}")
            if not record.get("measurementTickSamples") or not record.get("measurementFrameSamples"):
                problems.append(f"{file}: missing real-client simulation/frame samples")
            if expected_peak is not None and record.get("peakActiveParticles") != expected_peak:
                problems.append(f"{file}: peak {record.get('peakActiveParticles')} != {expected_peak}")
            if name == "global_budget_spike":
                if (record.get("globalAdmittedSpawns"), record.get("globalRejectedSpawns")) != (30_000, 30_000):
                    problems.append(f"{file}: wrong shared spawn admission/rejections")
                if record.get("globalRejectedEffects", 0) != 0:
                    problems.append(f"{file}: unexpected effect rejection")
            if name == "collision_1k" and not (
                record.get("collisionContacts", 0) > 0 and record.get("collisionEventObserved")
            ):
                problems.append(f"{file}: missing collision and emitted event")
            if name == "reload_active" and not record.get("reloadCompleted"):
                problems.append(f"{file}: active-effect reload did not complete")
            if name in ("model_1k", "sprite_lit_fallback") and record.get("selectedParticleBackend") != "cpu_legacy":
                problems.append(f"{file}: complex-feature CPU fallback not selected")

    globals_ = grouped["global_budget_spike"]
    if globals_:
        backends = {record.get("selectedParticleBackend") for record in globals_}
        if "gpu_analytic" not in backends or "cpu_dense" not in backends:
            problems.append(f"global_budget_spike: test both analytic and forced CPU fallback; observed {backends}")

    print("M5 recorded real-client validation (no synthetic data)")
    for name in REQUIRED:
        records = grouped[name]
        if not records:
            continue
        frames = [r["p95FrameMs"] for r in records if isinstance(r.get("p95FrameMs"), (int, float))]
        backend = ",".join(sorted({str(r.get("selectedParticleBackend", "?")) for r in records}))
        print(f"  {name:24} runs={len(records)} backend={backend:25} "
              f"median(frame P95)={median(frames):.3f} ms" if frames
              else f"  {name:24} runs={len(records)} backend={backend} (no frame P95)")
    if problems:
        print("FAILED:")
        for problem in problems:
            print("  - " + problem)
        return 1
    print("PASS: recorded global admission, CPU fallback and compatibility checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
