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
CATALOG = ROOT / "src/development/resources/pyro_vfx_museum_exhibits.tsv"
SOURCE = ROOT / "src/development/resources/assets/pyro_vfx/vfx/bench"
OUTPUT = ROOT / "src/development/resources/assets/pyro_vfx/vfx/museum"


def scale_value(value, multiplier, model=False):
    if isinstance(value, (float, int)):
        return round(float(value) * multiplier, 5)
    if isinstance(value, list):
        return [scale_value(x, multiplier) if (model or index < 2) else x
                for index, x in enumerate(value)]
    # Existing dynamic expressions keep the same language; scale at expression level.
    if isinstance(value, str):
        return f"({value}) * {multiplier:g}"
    raise ValueError(f"Unsupported scale: {value!r}")


def neutral_model_color(color):
    """Remove demo RGB tint, never change authored model opacity."""
    if isinstance(color, list) and len(color) == 4:
        return [1, 1, 1, color[3]]
    if isinstance(color, str) and color.startswith("#"):
        if len(color) == 9:  # #RRGGBBAA
            return "#FFFFFF" + color[-2:]
        if len(color) == 7:  # #RRGGBB (implicitly fully opaque)
            return "#FFFFFF"
    # Unsupported expression formats keep their authored appearance rather
    # than silently losing alpha. Do not create an opaque fallback.
    return color


# Make the visual coverage explicit instead of silently using the sphere
# fixture for displays named after different features.
SHAPES = {
    "spawn_point": {"type": "point"},
    "spawn_sphere": {"type": "sphere", "radius": 2.0},
    "spawn_sphere_shell": {"type": "sphere", "radius": 2.0, "edge_thickness": 0.06},
    "spawn_box": {"type": "box", "half_extents": [2.0, 1.4, 1.4]},
    "spawn_box_shell": {"type": "box", "half_extents": [2.0, 1.4, 1.4], "edge_thickness": 0.05},
    "spawn_line": {"type": "line", "axis": [0, 1, 0], "length": 4.0},
    # Face the visitor rather than showing an edge-on two-pixel line.
    "spawn_disc": {"type": "disc", "radius": 2.0, "axis": [0, 0, 1]},
    "spawn_ring": {"type": "ring", "radius": 2.0, "inner_radius": 1.45, "axis": [0, 0, 1]},
    "spawn_cone": {"type": "cone", "radius": 2.0, "height": 3.2, "axis": [0, 1, 0]},
    "spawn_model": {"type": "model", "model": "pyro_vfx:vfx/ember_cube",
                    "scale": [3.5, 3.5, 3.5]},
}




ATTACHMENTS = ROOT / "src/development/resources/assets/pyro_vfx/vfx/fixtures/attachments"


def combine_technical_variants(exhibit_id, data):
    """Each sub-emitter demonstrates a different technical capability, not a new exhibit."""
    import copy
    emitters = data["emitters"]
    primary = emitters[0]
    if exhibit_id in {"spawn_sphere", "spawn_box"}:
        shell = copy.deepcopy(primary)
        shell["id"] = "surface"
        shell["offset"] = [1.45, 0, 0]
        primary["offset"] = [-1.45, 0, 0]
        if exhibit_id == "spawn_sphere":
            primary["spawn_shape"] = {"type": "sphere", "radius": 0.95}
            shell["spawn_shape"] = {"type": "sphere", "radius": 0.95, "edge_thickness": 0.04}
        else:
            primary["spawn_shape"] = {"type": "box", "half_extents": [0.9, 0.9, 0.9]}
            shell["spawn_shape"] = {"type": "box", "half_extents": [0.9, 0.9, 0.9], "edge_thickness": 0.04}
        shell["render"]["color"] = "#55DFFFEE"
        emitters.append(shell)
    if exhibit_id == "spawn_point":
        primary["offset"] = [-1.25, 0, 0]
        steady = copy.deepcopy(primary)
        steady["id"] = "continuous"
        steady["offset"] = [1.25, 0, 0]
        steady["emitter_lifetime"] = {"mode":"looping","active_ticks":20,"sleep_ticks":0,"loops":0}
        steady["spawn_amount"] = {"mode":"steady","rate":8,"max_particles":40}
        steady["render"]["color"] = "#55DFFFEE"
        emitters.append(steady)
    if exhibit_id == "source_entity_local":
        world = json.loads((ATTACHMENTS/"source_entity_world.json").read_text(encoding="utf-8"))
        trail = world["emitters"][0]
        trail["id"] = "world_trail"
        trail["offset"] = [-0.45, 0.6, 0]
        trail["spawn_amount"]["rate"] = 8
        primary["id"] = "local_aura"
        primary["offset"] = [0.45, 0.6, 0]
        primary["spawn_amount"]["rate"] = 8
        emitters.append(trail)
    if exhibit_id == "model_custom":
        primary["offset"] = [-2, 0, 0]
        for name,source in (("block","model_block"),("entity","model_entity")):
            other = json.loads((SOURCE/(source+".json")).read_text(encoding="utf-8"))["emitters"][0]
            other = copy.deepcopy(other)
            other["id"] = name
            other["offset"] = [0 if name=="block" else 2, 0, 0]
            other["spawn_amount"] = {"mode":"instant","amount":2}
            other["render"]["scale"] = [1.1, 1.1, 1.1]
            other["render"]["color"] = "#FFFFFFFF"
            emitters.append(other)
    if exhibit_id == "curve_bezier":
        for kind,index in (("linear",0),("step",1),("catmull",2)):
            clone = copy.deepcopy(primary)
            clone["id"] = "curve_"+kind
            clone["offset"] = [(-1.5 if index==0 else 1.5 if index==1 else 0),0,0]
            data.setdefault("curves", {})["museum_"+kind] = {
                "type": kind, "points": [[0,0.12],[0.25,0.45],[0.6,0.20],[1,0.55]]}
            clone["render"]["scale"] = [
                "curve.museum_"+kind+"(particle.normalized_age) * 4",
                "curve.museum_"+kind+"(particle.normalized_age) * 4",1]
            clone["render"]["color"] = "#55DFFFEE" if index else "#FFB65AEE"
            emitters.append(clone)
        primary["offset"]=[0,0,0]
    if exhibit_id == "collision_events":
        bounce = copy.deepcopy(primary)
        bounce["id"] = "bounce_box"
        bounce["offset"] = [1.0,0,0]
        bounce["particle_triggers"] = []
        bounce["motion"]["collision"]["collision_type"] = "box"
        bounce["motion"]["collision"]["collision_size"] = [0.12,0.12,0.12]
        bounce["spawn_amount"] = {"mode":"instant","amount":8}
        bounce["render"]["color"] = "#55DFFFEE"
        gone = copy.deepcopy(bounce)
        gone["id"] = "expire_contact"
        gone["offset"] = [-1.0,0,0]
        gone["motion"]["collision"]["expire_on_contact"] = True
        emitters.extend([bounce,gone])
    if exhibit_id == "sprite_horizontal":
        for name,facing,x in (("world_x","world_x",-1.25),("world_y","world_y",1.25)):
            clone=copy.deepcopy(primary)
            clone["id"]=name
            clone["offset"]=[x,0,0]
            clone["render"]["facing"]=facing
            clone["render"]["color"]="#55DFFFCC"
            emitters.append(clone)


def customize_variant(exhibit_id, data):
    emitter = data["emitters"][0]
    if exhibit_id in SHAPES:
        emitter["spawn_shape"] = SHAPES[exhibit_id]
        emitter["motion"] = {"mode": "static"}
        emitter["offset"] = [0, 0, 0]
        # High contrast against the blue sky and concrete: readable from the
        # visitor's platform without additive overexposure.
        emitter["render"]["color"] = "#D72D82FF"
        emitter["render"]["scale"] = [0.40, 0.40, 1]
        emitter["render"]["sprite"]["blend_mode"] = "alpha"
    if exhibit_id in ("sprite_world_x", "sprite_world_y", "sprite_velocity"):
        emitter["render"]["facing"] = {
            "sprite_world_x": "world_x", "sprite_world_y": "world_y",
            "sprite_velocity": "velocity"
        }[exhibit_id]
    if exhibit_id == "rotation_parametric":
        emitter["rotation"] = {"mode": "parametric", "parametric":
            {"rotation": [0, 0, "particle.normalized_age * 720"]}}
    if exhibit_id == "cpu_curves":
        # Dynamic angular integration is deliberately not GPU analytic.
        # This stand shows exactly the same curve evaluation on CPU Dense.
        emitter["rotation"] = {"mode": "dynamic", "dynamic": {
            "start_rotation": [0, 0, 0],
            "angular_velocity": [0, 0, 45], "angular_drag": 0}}
    if exhibit_id == "legacy_event":
        # An actual per-particle callback (not an effect-level ON_CREATION)
        # is necessary for the selected path to be CPU Legacy.
        emitter["particle_triggers"] = [
            {"type": "on_expiration", "event": "increment"}]
    if exhibit_id == "motion_parametric":
        emitter["spawn_shape"] = {"type": "ring", "radius": 1.2, "inner_radius": 1.1}
        emitter["motion"] = {"mode": "parametric", "parametric": {
            "offset": ["math.cos(particle.age * 0.12) * 1.2",
                       "math.sin(particle.age * 0.16) * 0.7",
                       "math.sin(particle.age * 0.12) * 1.2"],
            "direction": [0, 1, 0]}}
    if exhibit_id.startswith("curve_"):
        kind = exhibit_id.removeprefix("curve_")
        data.setdefault("curves", {})["museum_bezier"] = {
            "type": kind,
            "points": [[0, 0.12], [0.25, 0.45], [0.6, 0.20], [1, 0.55]]}
        emitter["render"]["scale"] = [
            "curve.museum_bezier(particle.normalized_age) * 6",
            "curve.museum_bezier(particle.normalized_age) * 6", 1]
    if exhibit_id == "collision_box":
        emitter["motion"]["collision"]["collision_type"] = "box"
        emitter["motion"]["collision"]["collision_size"] = [0.3, 0.3, 0.3]
    if exhibit_id == "collision_expire":
        emitter["motion"]["collision"]["expire_on_contact"] = True
    if exhibit_id == "spawn_steady":
        emitter["spawn_amount"]["rate"] = 18
        emitter["spawn_amount"]["max_particles"] = 60
    if exhibit_id == "model_additive":
        emitter["render"]["model"]["render_layer"] = "additive"
    if exhibit_id == "model_cutout":
        emitter["render"]["model"]["render_layer"] = "cutout"


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
        source = (ATTACHMENTS / f"{exhibit_id}.json") if exhibit_id.startswith("source_") else (SOURCE / f"{fixture}.json")
        data = copy.deepcopy(json.loads(source.read_text(encoding="utf-8")))
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
            if render.get("type") == "model":
                # Neutralize only RGB. The original translucent cube has
                # alpha=0x80; overwriting it with #FFFFFFFF made it opaque.
                render["color"] = neutral_model_color(render.get("color", "#FFFFFFFF"))
                if exhibit_id == "model_translucent":
                    # Six cube faces layer alpha in the Minecraft translucent
                    # renderer. The original benchmark's 50% alpha becomes
                    # visually near-opaque after 2-3 overlapping faces.
                    # Keep the original benchmark intact and use a lower
                    # per-face opacity for the readability-focused museum.
                    render["color"] = "#FFFFFF33"
                if render.get("model", {}).get("source") == "item":
                    # Texture alpha is real geometry information for items.
                    render["model"]["render_layer"] = "cutout"
            if "scale" in render:
                # XYZ are physical dimensions of a model; a sprite's third
                # element is not a depth scale. Never squash 3D demos.
                render["scale"] = scale_value(render["scale"], size,
                                               model=render.get("type") == "model")
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
        if exhibit_id == "self_emit":
            # The historical M0 stress fixture deliberately emits back into
            # the same colliding emitter: 24 -> 576 -> 13,824 contacts.
            # Keep that fixture unchanged, but make the interactive demo a
            # bounded one-generation parent -> child event for reliable FPS.
            data["events"]["emit_again"]["emitter"] = "child"
            data["emitters"].append({
                "id": "child",
                "emitter_lifetime": {"mode": "manual"},
                "spawn_amount": {"mode": "instant", "amount": 2},
                "spawn_shape": {"type": "sphere", "radius": 0.18},
                "offset": [0, 0.3, 0],
                "particle_lifetime": {"max_age_ticks": 24},
                "motion": {"mode": "dynamic", "dynamic": {
                    "direction": "up", "speed": 0.16,
                    "acceleration": [0, -0.04, 0],
                    "linear_drag": 0.1
                }},
                "render": {"type": "sprite", "facing": "camera",
                    "environment_lighting": False,
                    "sprite": {"texture": "minecraft:textures/particle/generic_0.png",
                               "blend_mode": "additive"},
                    "color": "#66CCFFFF", "scale": [0.65, 0.65, 1]}
            })
        customize_variant(exhibit_id, data)
        combine_technical_variants(exhibit_id, data)
        output = OUTPUT / f"{exhibit_id}.json"
        output.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for stale in OUTPUT.glob("*.json"):
        if stale.stem not in seen:
            stale.unlink()
    print(f"Generated {len(seen)} museum fixtures")
    return len(seen)


if __name__ == "__main__":
    generate()
