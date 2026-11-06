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
            clone["offset"] = [(-1.0 if index==0 else 1.0 if index==1 else 3.0),0,0]
            data.setdefault("curves", {})["museum_"+kind] = {
                "type": kind, "points": [[0,0.12],[0.25,0.45],[0.6,0.20],[1,0.55]]}
            clone["render"]["scale"] = [
                "curve.museum_"+kind+"(particle.normalized_age) * 4",
                "curve.museum_"+kind+"(particle.normalized_age) * 4",1]
            clone["render"]["color"] = {"linear":"#FFB65AEE", "step":"#55DFFFEE",
                                        "catmull":"#B98EFFEE"}[kind]
            clone["spawn_shape"] = {"type":"point"}
            emitters.append(clone)
        primary["offset"]=[-3.0,0,0]
        primary["spawn_shape"] = {"type":"point"}
        primary["render"]["color"] = "#D72D82FF"
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



def improve_presentation(exhibit_id, data):
    """Museum-only framing: keep the tested mechanic prominent and visually isolated."""
    emitters = data["emitters"]
    primary = emitters[0]
    if exhibit_id == "sprite_additive":
        # No motion: blend mode is the only changing variable, compared side by side.
        primary["id"] = "alpha"
        primary["offset"] = [-1.6, 0, 0]
        primary["spawn_shape"] = {"type": "sphere", "radius": 0.6}
        primary["motion"] = {"mode": "static"}
        primary["particle_lifetime"] = {"max_age_ticks": 140}
        primary["render"]["scale"] = [0.44, 0.44, 1]
        primary["render"]["color"] = [1, 0.62, 0.2, 0.45]
        primary["render"]["sprite"]["blend_mode"] = "alpha"
        additive = copy.deepcopy(primary)
        additive["id"] = "additive"
        additive["offset"] = [1.6, 0, 0]
        additive["render"]["sprite"]["blend_mode"] = "additive"
        emitters.append(additive)

    if exhibit_id == "source_entity_local":
        # A visible double marker: the amber source-following orbit versus a cyan world trail.
        for emitter in emitters:
            emitter["render"]["scale"] = [0.34, 0.34, 1]
            emitter["render"]["color"] = (
                [1.0, 0.75, 0.12, "0.9 - particle.normalized_age * 0.7"]
                if emitter["id"] == "local_aura" else
                [0.12, 0.85, 1.0, "0.9 - particle.normalized_age * 0.7"])
            emitter["render"]["sprite"]["blend_mode"] = "additive"
            emitter["spawn_shape"]["radius"] = 0.18
            emitter["offset"] = ([0.48, 1.25, 0] if emitter["id"] == "local_aura"
                                 else [-0.48, 1.25, 0])
            emitter["spawn_amount"]["rate"] = 28
            emitter["spawn_amount"]["max_particles"] = 90
            emitter["particle_lifetime"]["max_age_ticks"] = 48
            emitter["motion"] = {"mode": "static"}

    if exhibit_id == "source_model_part":
        # The model-part provider already points at the head; don't offset one block higher.
        primary["offset"] = [0, 0.16, 0]
        primary["render"]["color"] = [1, 0.76, 0.2, "1.0 - particle.normalized_age"]
        primary["render"]["scale"] = [0.34, 0.34, 1]
        primary["spawn_amount"]["rate"] = 32
        primary["particle_lifetime"]["max_age_ticks"] = 55

    if exhibit_id in {"source_block", "source_block_entity"}:
        # The previous 1.12-block additive tiles hid their barrel and the rest
        # of the scene despite low alpha. Keep a small halo around the anchor.
        primary["offset"] = [0, 1.1, 0]
        primary["render"]["scale"] = [0.22, 0.22, 1]
        primary["spawn_amount"]["rate"] = 20
        primary["spawn_amount"]["max_particles"] = 72
        primary["render"]["color"] = [0.95, 0.3, 0.88,
                                      "0.65 * (1.0 - particle.normalized_age)"]
        primary["spawn_shape"]["radius"] = 0.3

    if exhibit_id == "model_custom":
        # A geometry comparison needs stable, separated specimens, not flying cubes.
        for emitter, x, amount in zip(emitters, [-2.4, 0, 2.4], [1, 1, 1]):
            emitter["offset"] = [x, 0, 0]
            emitter["spawn_shape"] = {"type": "sphere", "radius": 0.1}
            emitter["motion"] = {"mode": "static"}
            emitter["spawn_amount"] = {"mode":"instant", "amount":amount}
            emitter["render"]["scale"] = [1.15, 1.15, 1.15]

    if exhibit_id == "model_item":
        # Five fixed, non-overlapping item model specimens.
        emitters.clear()
        samples = [
            ("ingot", "minecraft:iron_ingot", -3.6),
            ("diamond", "minecraft:diamond", -1.8),
            ("stick", "minecraft:stick", 0),
            ("sword", "minecraft:iron_sword", 1.8),
            ("apple", "minecraft:apple", 3.6),
        ]
        for name, item, x in samples:
            emitter = copy.deepcopy(primary)
            emitter["id"] = name
            emitter["render"]["model"]["model"] = item
            emitter["offset"] = [x, 0, 0]
            emitter["spawn_amount"] = {"mode": "instant", "amount": 1}
            emitter["spawn_shape"] = {"type": "point"}
            emitter["motion"] = {"mode": "static"}
            emitter["render"]["scale"] = [0.85, 0.85, 0.85]
            emitters.append(emitter)

    if exhibit_id == "model_translucent":
        # Translucent cubes are self-overdrawing six faces already; do not
        # superimpose five different cubes at the same position.
        primary["spawn_amount"] = {"mode": "instant", "amount": 1}
        primary["spawn_shape"] = {"type": "point"}
        primary["motion"] = {"mode": "static"}
        primary["render"]["scale"] = [1.75, 1.75, 1.75]

    if exhibit_id in {"model_rotating", "gpu_model"}:
        # Preserve model source/render backend, isolate three specimens.
        emitters.clear()
        for i,x in enumerate((-2.5, 0, 2.5)):
            emitter = copy.deepcopy(primary)
            emitter["id"] = f"model_{i+1}"
            emitter["offset"] = [x, 0, 0]
            emitter["spawn_amount"] = {"mode":"instant", "amount": 1}
            emitter["spawn_shape"] = {"type": "point"}
            emitter["render"]["scale"] = [0.75, 0.75, 0.75]
            emitters.append(emitter)

    if exhibit_id == "source_model_local":
        # A small ring of rotating cubes at chest height, not a random pile
        # of 24 large cubes occupying the Husk's body.
        primary["offset"] = [0, 1.25, 0]
        primary["spawn_amount"] = {"mode": "instant", "amount": 6}
        primary["spawn_shape"] = {
            "type": "ring", "radius": 1.15, "inner_radius": 1.1,
            "axis": [0, 1, 0]
        }
        primary["render"]["scale"] = [0.34, 0.34, 0.34]
        # A lightweight visual probe confirms that this same moving source
        # can also drive sprites if the resident model renderer misbehaves.
        guide = copy.deepcopy(emitters[0])
        guide["id"] = "entity_position_marker"
        guide["render"] = {
            "type": "sprite", "facing": "camera",
            "environment_lighting": False,
            "sprite": {
                "texture": "minecraft:textures/particle/generic_0.png",
                "blend_mode": "alpha"
            },
            "color": "#55EECCFF", "scale": [0.24, 0.24, 1]
        }
        guide["offset"] = [0, 1.9, 0]
        guide["spawn_shape"] = {"type": "point"}
        guide["spawn_amount"] = {"mode": "steady", "rate": 20,
                                 "max_particles": 40}
        guide["emitter_lifetime"] = {"mode": "once", "active_ticks": 160}
        guide["particle_lifetime"] = {"max_age_ticks": 35}
        guide["rotation"] = {"mode": "none"}
        emitters.append(guide)

    if exhibit_id == "collision_events":
        # Three independent drop lanes directly above matching physical
        # landing pads. Same gravity and spawn height: only collision behavior
        # changes. The bench stress fixture remains untouched.
        original = copy.deepcopy(primary)
        emitters.clear()
        for name, x, color, bounce, expire in (
            ("fall_stop", -3.0, "#77D5FFFF", 0.0, False),
            ("bounce", 0.0, "#FFCA51FF", 0.78, False),
            ("expire_contact", 3.0, "#FF7777FF", 0.0, True),
        ):
            emitter = copy.deepcopy(original)
            emitter["id"] = name
            emitter["offset"] = [x, 2.35, 0]
            emitter["spawn_shape"] = {"type": "sphere", "radius": 0.09}
            emitter["spawn_amount"] = {"mode": "instant", "amount": 3}
            emitter["particle_lifetime"] = {"max_age_ticks": 108}
            emitter["motion"]["dynamic"]["direction"] = "down"
            emitter["motion"]["dynamic"]["speed"] = 0
            emitter["motion"]["dynamic"]["acceleration"] = [0, -10.0, 0]
            emitter["motion"]["dynamic"]["linear_drag"] = 0
            emitter["motion"]["collision"] = {
                "collide": True, "collision_type": "sphere",
                "collision_size": [0.12, 0.12, 0.12],
                "collision_drag": 0.05, "bounciness": bounce,
                "expire_on_contact": expire
            }
            emitter["render"]["color"] = color
            emitter["render"]["scale"] = [0.42, 0.42, 1]
            # Keep on_collision only on one lane to still exercise ordered events.
            emitter["particle_triggers"] = (original.get("particle_triggers", [])
                                            if name == "fall_stop" else [])
            emitters.append(emitter)

    if exhibit_id == "sprite_horizontal":
        # Four distinct fixed samples; zero spread means billboard tilt can be
        # judged by walking around each plane instead of deciphering a cloud.
        primary["id"] = "camera"
        templates = [("camera", "camera", -3.3, "#FFC661CC"),
                     ("horizontal", "camera_horizontal", -1.1, "#56C8ECCC"),
                     ("world_x", "world_x", 1.1, "#EC74D6CC"),
                     ("world_y", "world_y", 3.3, "#9AD668CC")]
        emitters.clear()
        for name, facing, x, color in templates:
            emitter = copy.deepcopy(primary)
            emitter["id"] = name
            emitter["offset"] = [x, -0.12 if name == "world_y" else 0, 0]
            emitter["render"]["facing"] = facing
            emitter["render"]["sprite"]["blend_mode"] = "alpha"
            emitter["render"]["color"] = color
            emitter["render"]["scale"] = [1.25, 1.25, 1]
            emitter["particle_lifetime"] = {"max_age_ticks": 112}
            emitter["spawn_amount"] = {"mode": "instant", "amount": 1}
            emitter["spawn_shape"] = {"type": "point"}
            emitter["motion"] = {"mode": "static"}
            emitters.append(emitter)

    if exhibit_id == "curve_bezier":
        # Only scale varies with age: no drifting spark cloud to confuse the comparison.
        for emitter in emitters:
            emitter["motion"] = {"mode": "static"}
            emitter["spawn_amount"] = {"mode": "instant", "amount": 1}
            emitter["spawn_shape"] = {"type": "point"}
            emitter["render"]["sprite"]["blend_mode"] = "additive"

    if exhibit_id == "gpu_sprite":
        # Keep a real CPU Dense path as shared dynamic rotations and
        # birth-time shapes become GPU eligible. A mutable appearance param
        # requires CPU evaluation, while its default matches the same color.
        primary["id"] = "gpu_resident"
        primary["offset"] = [-2.2, 0, 0]
        primary["spawn_shape"] = {"type": "sphere", "radius": 0.65}
        primary["motion"]["dynamic"]["speed"] = 0.2
        primary["motion"]["dynamic"]["acceleration"] = [0, 0, 0]
        primary["render"]["scale"] = [0.34, 0.34, 1]
        primary["render"]["color"] = "#FFBB66EE"
        cpu = copy.deepcopy(primary)
        cpu["id"] = "cpu_dense"
        cpu["offset"] = [2.2, 0, 0]
        data["parameters"]["cpuRadius"] = 0.65
        data["parameters"]["cpuAlpha"] = 238 / 255
        cpu["spawn_shape"]["radius"] = "param.cpuRadius"
        cpu["render"]["color"] = [85 / 255, 223 / 255, 1.0, "param.cpuAlpha"]
        emitters.append(cpu)

    if exhibit_id == "event_order":
        # Preserve the semantics of an ordered event graph, but make each
        # step observable: 1 -> 2 -> 3 with a flash at 0/30/60 ticks.
        data["parameters"]["stage"] = 0
        data["lifetime"] = {"mode": "once", "active_ticks": 135}
        data["events"] = {
            "initialize": {"type": "set_param", "param": "stage", "value": 1},
            "increment": {"type": "set_param", "param": "stage",
                          "value": "param.stage + 1"},
            "show_one": {"type": "emit", "emitter": "step_one"},
            "show_two": {"type": "emit", "emitter": "step_two"},
            "show_three": {"type": "emit", "emitter": "step_three"},
            "start": {"type": "sequence", "events": ["initialize", "show_one"]},
            "advance_two": {"type": "sequence", "events": ["increment", "show_two"]},
            "advance_three": {"type": "sequence", "events": ["increment", "show_three"]},
        }
        data["triggers"] = [
            {"type": "on_creation", "event": "start"},
            {"type": "timeline", "time_ticks": 30, "event": "advance_two"},
            {"type": "timeline", "time_ticks": 60, "event": "advance_three"},
        ]
        emitters.clear()
        for name, x, color in [("step_one", -2.7, "#FF6666FF"),
                               ("step_two", 0, "#FFD45AFF"),
                               ("step_three", 2.7, "#5DEBFFFF")]:
            emitter = copy.deepcopy(primary)
            emitter["id"] = name
            emitter["offset"] = [x, 0, 0]
            emitter["emitter_lifetime"] = {"mode": "manual"}
            emitter["spawn_amount"] = {"mode": "manual", "amount": 10}
            emitter["spawn_shape"] = {"type": "sphere", "radius": 0.3}
            emitter["particle_lifetime"] = {"max_age_ticks": 100}
            emitter["motion"] = {"mode": "static"}
            emitter["render"]["scale"] = [0.5, 0.5, 1]
            emitter["render"]["color"] = color
            emitters.append(emitter)

    # Free-running effect+emitter clocks give seamless steady demonstrations.
    # Timeline/event-order specimens need real once-only lifecycle events and are
    # immediately restarted by the museum controller upon completion instead.
    looping_showcases = {
        "spawn_point", "spawn_sphere", "spawn_box", "spawn_line", "spawn_disc",
        "spawn_ring", "spawn_cone", "spawn_model", "dynamic_motion",
        "motion_parametric", "rotation_parametric", "sprite_horizontal",
        "sprite_velocity", "sprite_additive", "model_custom", "model_translucent",
    }
    # GPU-analytic / attachment / event-timeline examples retain their original
    # backend eligibility and exact lifetime semantics. The museum controller
    # replays those on the first tick after completion.
    if exhibit_id in looping_showcases:
        data["lifetime"] = {
            "mode": "looping", "active_ticks": 120, "sleep_ticks": 0, "loops": 0}
        for emitter in emitters:
            lifetime = emitter.get("emitter_lifetime", {})
            if lifetime.get("mode") == "manual":
                continue
            if emitter.get("spawn_amount", {}).get("mode") == "instant":
                cadence = 46 if exhibit_id in {"collision_events", "self_emit", "dynamic_motion"} else 112
                emitter["emitter_lifetime"] = {
                    "mode": "looping", "active_ticks": 1,
                    "sleep_ticks": cadence - 1, "loops": 0}
            else:
                emitter["emitter_lifetime"] = {
                    "mode": "looping", "active_ticks": 120,
                    "sleep_ticks": 0, "loops": 0}

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
            "Use the stand's INFO button for technical details and comparison labels."
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
        if exhibit_id in {"self_emit", "runtime_self_emit"}:
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
        if exhibit_id == "runtime_self_emit":
            data["lifetime"]["active_ticks"] = 75
        if exhibit_id == "runtime_coalesced":
            data["lifetime"]["active_ticks"] = 72
            data["emitters"][0]["particle_lifetime"]["max_age_ticks"] = 60
        customize_variant(exhibit_id, data)
        combine_technical_variants(exhibit_id, data)
        improve_presentation(exhibit_id, data)
        output = OUTPUT / f"{exhibit_id}.json"
        output.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for stale in OUTPUT.glob("*.json"):
        if stale.stem not in seen:
            stale.unlink()
    print(f"Generated {len(seen)} museum fixtures")
    return len(seen)


if __name__ == "__main__":
    generate()
