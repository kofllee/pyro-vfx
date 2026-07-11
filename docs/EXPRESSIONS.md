# Expressions and Curves

Pyro VFX numeric fields may use expression strings.

Expressions are parsed into executable expression nodes when the definition is created. The source string is not reparsed every time the expression is evaluated.

## Numeric expression

```json
"speed": "0.5 + particle.random * 1.5"
```

## Vec3 expression

```json
"acceleration": [
  "math.sin(particle.age * 0.1) * 0.05",
  -0.2,
  0
]
```

## Color expression

```json
"color": [
  1.0,
  "0.5 + particle.random * 0.25",
  0.1,
  "1.0 - particle.normalized_age"
]
```

## Operators

### Arithmetic

```text
+
-
*
/
%
^
```

### Comparison

```text
<
<=
>
>=
==
!=
```

Comparison results are numeric:

```text
true  -> 1.0
false -> 0.0
```

### Boolean

```text
&&
||
!
```

A value is considered true when it is non-zero and not NaN.

### Conditional

```text
condition ? when_true : when_false
```

Example:

```json
"scale": [
  "particle.normalized_age < 0.5 ? 0.2 : 0.5",
  "particle.normalized_age < 0.5 ? 0.2 : 0.5",
  1
]
```

## Math functions

### Trigonometry

```text
math.sin(value)
math.cos(value)
math.tan(value)
```

Angles use radians.

### Basic numeric functions

```text
math.abs(value)
math.floor(value)
math.ceil(value)
math.round(value)
math.frac(value)
math.sqrt(value)
math.pow(base, exponent)
math.min(a, b)
math.max(a, b)
math.clamp(value, min, max)
```

### Interpolation

```text
math.lerp(a, b, t)
math.inv_lerp(a, b, value)
math.smoothstep(edge0, edge1, value)
```

### Rotation helpers

```text
math.wrap_degrees(angle)
math.angle_delta(from, to)
math.lerprotate(from, to, t)
```

`math.wrap_degrees` returns a value in the `[-180, 180)` range.

## Random functions

```text
random.value()
random.range(min, max)
random.int(min, max)
random.die(min, max, count)
random.die_int(min, max, count)
```

### `random.value()`

Returns a random value in the `0.0..1.0` range.

### `random.range(min, max)`

Returns a random floating-point value between the supplied limits.

### `random.int(min, max)`

Rounds the limits and returns an inclusive random integer.

### `random.die(min, max, count)`

Returns the sum of `count` floating-point random rolls.

### `random.die_int(min, max, count)`

Returns the sum of `count` inclusive integer random rolls.

Random functions advance the runtime random source whenever they are evaluated. Do not assume that evaluating the same expression twice in the same tick returns the same value.

For stable per-object randomness, prefer:

```text
effect.random
emitter.random
particle.random
```

## Curves

Curves are named root-level definitions.

```json
"curves": {
  "smoke_size": {
    "type": "catmull",
    "points": [
      [0.0, 0.1],
      [0.25, 0.4],
      [0.75, 0.9],
      [1.0, 1.2]
    ]
  }
}
```

Use a curve through a function call:

```text
curve.smoke_size(particle.normalized_age)
```

Example:

```json
"scale": [
  "curve.smoke_size(particle.normalized_age)",
  "curve.smoke_size(particle.normalized_age)",
  1
]
```

Current curve types:

### Linear

```text
linear
```

Linear interpolation between points.

### Step

```text
step
```

Uses the left point value until the next point is reached.

### Catmull

```text
catmull
```

Catmull-Rom interpolation using neighboring values.

### Bezier

```text
bezier
```

The current implementation applies smooth interpolation between each pair of values. It does not store explicit user-controlled Bezier handles.

## Context scopes

Available variables depend on when a field is evaluated.

Using a variable outside its intended context may produce a missing-value error or unintended behavior.

## Parameters

```text
param.<id>
```

Example:

```text
param.power
param.smoke_amount
```

## Effect context

```text
effect.age
effect.local_age
effect.active_age
effect.normalized_age
effect.random
effect.pos.x
effect.pos.y
effect.pos.z
```

### Meaning

```text
effect.age
    Total age of the effect instance in ticks.

effect.local_age
    Age inside the current lifetime cycle after the initial delay.

effect.active_age
    Age inside the active section of the current cycle.

effect.normalized_age
    active_age / active_ticks, clamped to 0.0..1.0.

effect.random
    Stable random value generated once for the effect instance.

effect.pos
    Effect origin position.
```

## Emitter context

```text
emitter.age
emitter.local_age
emitter.active_age
emitter.normalized_age
emitter.random
emitter.spawned_particles
emitter.pos.x
emitter.pos.y
emitter.pos.z
```

### Meaning

```text
emitter.age
    Emitter age in ticks.

emitter.local_age
    Age inside the current emitter lifetime cycle.

emitter.active_age
    Age inside the active part of the current cycle.

emitter.normalized_age
    active_age / active_ticks, clamped to 0.0..1.0.

emitter.random
    Stable random value generated once for the runtime emitter.

emitter.spawned_particles
    Total number of particles emitted by that emitter.

emitter.pos
    Current emitter position.
```

## Particle spawn context

```text
particle.random
spawn.pos.x
spawn.pos.y
spawn.pos.z
```

`particle.random` is generated once and remains stable for that particle.

`spawn.pos` is the sampled particle spawn position.

## Particle tick context

```text
particle.age
particle.lifetime
particle.normalized_age
particle.random

particle.scale.x
particle.scale.y
particle.scale.z

particle.rotation.x
particle.rotation.y
particle.rotation.z

particle.angular_velocity.x
particle.angular_velocity.y
particle.angular_velocity.z

particle.pos.x
particle.pos.y
particle.pos.z

particle.prev_pos.x
particle.prev_pos.y
particle.prev_pos.z

particle.vel.x
particle.vel.y
particle.vel.z

particle.speed

spawn.pos.x
spawn.pos.y
spawn.pos.z
```

### Meaning

```text
particle.age
    Current particle age in ticks.

particle.lifetime
    Fixed particle lifetime in ticks.

particle.normalized_age
    particle.age / particle.lifetime, clamped to 0.0..1.0.

particle.random
    Stable per-particle random value.

particle.scale
    Current scale state.

particle.rotation
    Current Euler rotation state.

particle.angular_velocity
    Current angular velocity state.

particle.pos
    Current world position.

particle.prev_pos
    Previous world position.

particle.vel
    Current velocity.

particle.speed
    Current velocity length.

spawn.pos
    Original spawn position.
```

## Evaluation timing

### Effect start

Typical fields:

- parameter defaults;
- effect lifetime;
- command overrides after defaults.

### Emitter start

Typical fields:

- emitter lifetime;
- instant amount;
- max particle count.

### Effect tick

Typical fields:

- emitter offset.

### Emitter tick

Typical fields:

- steady spawn rate.

### Particle spawn

Typical fields:

- spawn shape;
- particle lifetime;
- initial speed;
- initial direction;
- initial rotation;
- angular velocity;
- UV start state.

### Particle tick

Typical fields:

- acceleration;
- drag;
- collision values;
- color;
- scale;
- parametric position;
- parametric rotation;
- angular acceleration;
- angular drag.

## Recommended expression style

Prefer stable randomness:

```json
"speed": "1.0 + particle.random * 2.0"
```

over repeated calls:

```json
"speed": "random.range(1.0, 3.0)"
```

when the value should remain tied to the particle.

Prefer curves for repeated lifetime shaping:

```json
"scale": [
  "curve.size(particle.normalized_age)",
  "curve.size(particle.normalized_age)",
  1
]
```

Keep expensive or deeply nested expressions out of fields evaluated for every particle every tick unless they are necessary.

## Current limitations

- No static analysis of variable dependencies.
- No expression cost reporting.
- No editor autocomplete.
- No visual curve editor.
- No constant folding beyond literal node construction.
- Random function evaluation order matters.
- Unknown functions fail parsing.
- Missing context variables fail during evaluation.
