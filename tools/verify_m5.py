#!/usr/bin/env python3
"""Validate one coherent M5 *real-client* benchmark campaign, not the entire archive.

The default selects the most recent complete session from one reported Git
commit within a bounded UTC time window. Use --after to demand new results;
a partial new campaign fails as incomplete instead of borrowing old reports.
No data is synthesized and all selected measurements are validated.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timedelta
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
TIMESTAMP = "%Y%m%d-%H%M%S"


def stamp_from_filename(path: Path, name: str) -> datetime | None:
    prefix = name + "-"
    if not path.name.startswith(prefix) or path.suffix != ".json":
        return None
    try:
        return datetime.strptime(path.stem[len(prefix):], TIMESTAMP)
    except ValueError:
        return None


def collect(folder: Path, after: datetime | None) -> list[dict]:
    reports: list[dict] = []
    for name in REQUIRED:
        for path in folder.glob(name + "-*.json"):
            stamp = stamp_from_filename(path, name)
            if stamp is None or (after is not None and stamp < after):
                continue
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
                if not isinstance(data, dict):
                    raise ValueError("expected JSON object")
            except (ValueError, OSError) as exc:
                # Corrupt files are not silently counted as successful samples.
                data = {"status": "invalid", "error": str(exc)}
            data["_file"] = path.name
            data["_scenario"] = name
            data["_stamp"] = stamp
            data["_commit"] = data.get("environment", {}).get("gitCommit", "unknown")
            reports.append(data)
    return reports


def select_session(reports: list[dict], window: timedelta,
                   min_global_runs: int) -> list[dict] | None:
    # Keep version changes and independent historical benchmark batches apart.
    grouped: dict[str, list[dict]] = {}
    for report in reports:
        grouped.setdefault(report["_commit"], []).append(report)
    candidates: list[tuple[datetime, list[dict]]] = []
    for cohort in grouped.values():
        cohort.sort(key=lambda r: r["_stamp"])
        for end in reversed(cohort):
            start = end["_stamp"] - window
            session = [r for r in cohort if start <= r["_stamp"] <= end["_stamp"]]
            counts = Counter(r["_scenario"] for r in session)
            if all(counts.get(name, 0) >=
                   (min_global_runs if name == "global_budget_spike" else 1)
                   for name in REQUIRED):
                candidates.append((end["_stamp"], session))
                break
    if not candidates:
        return None
    return max(candidates, key=lambda entry: entry[0])[1]


def validate(session: list[dict], min_global_runs: int) -> list[str]:
    problems = []
    by_name = {name: [] for name in REQUIRED}
    for record in session:
        by_name[record["_scenario"]].append(record)
    for name, expected_peak in REQUIRED.items():
        records = by_name[name]
        if len(records) < (min_global_runs if name == "global_budget_spike" else 1):
            problems.append(f"{name}: insufficient runs")
            continue
        for record in records:
            file = record["_file"]
            if record.get("status") != "completed" or record.get("error"):
                problems.append(f"{file}: status={record.get('status')}: {record.get('error')}")
            if not record.get("measurementTickSamples") or not record.get("measurementFrameSamples"):
                problems.append(f"{file}: missing client tick/frame samples")
            if expected_peak is not None and record.get("peakActiveParticles") != expected_peak:
                problems.append(f"{file}: peak {record.get('peakActiveParticles')} != {expected_peak}")
            if name == "global_budget_spike":
                if (record.get("globalAdmittedSpawns"), record.get("globalRejectedSpawns")) != (30_000, 30_000):
                    problems.append(f"{file}: wrong shared spawn admission/rejections")
                if record.get("globalRejectedEffects", 0) != 0:
                    problems.append(f"{file}: unexpected effect rejection")
            if name == "collision_1k" and not (
                    record.get("collisionContacts", 0) > 0 and record.get("collisionEventObserved")):
                problems.append(f"{file}: missing collision and emitted event")
            if name == "reload_active" and not record.get("reloadCompleted"):
                problems.append(f"{file}: active-effect reload did not complete")
            # Model and world-lit sprite routing evolved after M5. CPU Legacy,
            # CPU Dense, GPU Analytic models, and GPU-instanced lit sprites are
            # all valid when their reported renderer matches the active route.
            if name == "model_1k" and record.get("selectedParticleBackend") not in (
                    "cpu_legacy", "cpu_dense", "gpu_analytic"):
                problems.append(f"{file}: unknown model simulation backend")
            if name == "sprite_lit_fallback" and record.get("selectedParticleBackend") not in (
                    "cpu_legacy", "cpu_dense"):
                problems.append(f"{file}: invalid lit sprite backend")
    globals_ = by_name["global_budget_spike"]
    backends = {r.get("selectedParticleBackend") for r in globals_}
    if "gpu_analytic" not in backends or "cpu_dense" not in backends:
        problems.append(f"global_budget_spike: require both GPU and forced Dense; saw {backends}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, default=Path("run/pyrovfx-benchmarks"))
    parser.add_argument("--after", default="", help="Require UTC filename stamp >= YYYYMMDD-HHMMSS")
    parser.add_argument("--window-minutes", type=int, default=15,
                        help="Maximum span of one same-commit benchmark session (default: 15)")
    parser.add_argument("--min-global-runs", type=int, default=3)
    args = parser.parse_args()
    if args.window_minutes < 1 or args.min_global_runs < 1:
        parser.error("window-minutes and min-global-runs must be positive")
    try:
        after = datetime.strptime(args.after, TIMESTAMP) if args.after else None
    except ValueError:
        parser.error("--after must use YYYYMMDD-HHMMSS")
    reports = collect(args.folder, after)
    session = select_session(reports, timedelta(minutes=args.window_minutes),
                             args.min_global_runs)
    if session is None:
        counts = Counter(r["_scenario"] for r in reports)
        print("INCOMPLETE: no single same-commit M5 client benchmark campaign"
              f" within {args.window_minutes} minutes"
              + (f" after {args.after}" if args.after else " in the archive"))
        for name in REQUIRED:
            print(f"  {name:24} archived candidate reports={counts[name]}")
        print("Collect a complete paired M5 suite; do not mix old backends or Git revisions.")
        return 2

    session.sort(key=lambda r: (r["_scenario"], r["_stamp"]))
    print("M5 recorded real-client verification (one historical campaign)")
    print(f"  commit: {session[0]['_commit']}")
    print(f"  UTC reports: {min(r['_stamp'] for r in session):{TIMESTAMP}}"
          f" .. {max(r['_stamp'] for r in session):{TIMESTAMP}}")
    for name in REQUIRED:
        cohort = [r for r in session if r["_scenario"] == name]
        frames = [r["p95FrameMs"] for r in cohort
                  if isinstance(r.get("p95FrameMs"), (int, float))]
        backend = ",".join(sorted({str(r.get("selectedParticleBackend", "?")) for r in cohort}))
        summary = f"median(P95)={median(frames):.3f} ms" if frames else "no frame P95"
        print(f"  {name:24} runs={len(cohort)} backend={backend}: {summary}")
    problems = validate(session, args.min_global_runs)
    if problems:
        print(f"FAILED: {len(problems)} issues within selected campaign:")
        for problem in problems[:30]:
            print("  - " + problem)
        if len(problems) > 30:
            print(f"  ... {len(problems) - 30} more")
        return 1
    print("PASS: coherent real-client M5 session. Does NOT certify the current Git checkout.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
