#!/usr/bin/env python3
"""Generate visible, resource-pack-independent museum copies of benchmark effects.

Only visual demo JSON is changed. Gameplay scaling semantics and benchmark
fixtures are never modified. Run after changing pyro_vfx_museum_exhibits.tsv.
"""
from __future__ import annotations
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/main/resources/pyro_vfx_museum_exhibits.tsv"
SOURCE = ROOT / "src/main/resources/assets/pyro_vfx/vfx/bench"
OUTPUT = ROOT / "src/main/resources/assets/pyro_vfx/vfx/museum"


def scale_value(value, multiplier):
    if isinstance(value, (float, int)):
        return round(float(value) * multiplier, 5)
    if isinstance(value, list):
        return [scale_value(x, multiplier) if index < 2 else x
                for index, x in enumerate(value)]
    # Existing dynamic expressions keep the same language; scale at expression level.
    if isinstance(value, str):
        return f"({value}) * {multiplier:g}"
    raise ValueError(f"Unsupported scale: {value!r}")


def generate() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    seen = set()
    rows = [row for row in CATALOG.read_text(encoding="utf-8").splitlines()
            if row.strip() and not row.startswith("#")]
    for row in rows:
        exhibit_id, category, title, fixture, count, size, description = row.split("|")
        if exhibit_id in seen:
            raise ValueError(f"duplicate ID {exhibit_id}")
        seen.add(exhibit_id)
        data = copy.deepcopy(json.loads((SOURCE / f"{fixture}.json").read_text(encoding="utf-8")))
        data.setdefault("metadata", {})["description"] = (
            f"Museum / {category} / {title}: {description}. "
            "Read on-stand controls to replay and compare."
        )
        count = int(count)
        size = float(size)
        data.setdefault("parameters", {})["count"] = count
        for emitter in data["emitters"]:
            # Keep the original emitter's event graph/motion/lighting and lifetime.
            # Most benchmark sources already use param.count. Replace hardcoded
            # quantities only when this is an unparameterized source fixture.
            amount = emitter.get("spawn_amount", {})
            if amount.get("mode") == "instant":
                amount["amount"] = "param.count"
            render = emitter.get("render", {})
            if "scale" in render:
                render["scale"] = scale_value(render["scale"], size)
            if exhibit_id.startswith("smoke_alpha") and isinstance(render.get("color"), list):
                # A museum exhibit must still show smoke near the end of an
                # automated 100-tick capture. This changes ONLY the demo.
                render["color"][3] = "math.max(0.6, 0.85 * (1.0 - particle.normalized_age))"
                emitter["rotation"] = {
                    "mode": "dynamic",
                    "dynamic": {
                        "start_rotation": [0, 0, 0],
                        "angular_velocity": [0, 0, 15],
                        "angular_drag": 0
                    }
                }
                if "dynamic" in emitter.get("motion", {}):
                    emitter["motion"]["dynamic"]["speed"] = 0.015
                    emitter["motion"]["dynamic"]["acceleration"] = [0, 0, 0]
            if "spawn_shape" in emitter:
                shape = emitter["spawn_shape"]
                if isinstance(shape.get("radius"), (int, float)):
                    shape["radius"] = min(float(shape["radius"]),
                                          0.9 if exhibit_id.startswith("smoke_alpha") else 1.55)
                if isinstance(shape.get("height"), (int, float)):
                    shape["height"] = min(float(shape["height"]), 2.5)
            # Avoid sky-high presentation beams in the museum.
        output = OUTPUT / f"{exhibit_id}.json"
        output.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for stale in OUTPUT.glob("*.json"):
        if stale.stem not in seen:
            stale.unlink()
    print(f"Generated {len(seen)} museum fixtures")
    return len(seen)


if __name__ == "__main__":
    generate()
