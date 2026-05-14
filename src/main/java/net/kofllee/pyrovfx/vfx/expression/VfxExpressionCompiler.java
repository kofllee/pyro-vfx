package net.kofllee.pyrovfx.vfx.expression;

public final class VfxExpressionCompiler {
    private VfxExpressionCompiler() {}

    public static VfxExpressionNode compileNumber(String source){
        return new VfxExpressionParser(source).parse();
    }
}
