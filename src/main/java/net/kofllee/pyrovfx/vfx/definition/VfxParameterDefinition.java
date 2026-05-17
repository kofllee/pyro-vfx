package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;

public record VfxParameterDefinition(String id, VfxNumberExpression value) {
}
