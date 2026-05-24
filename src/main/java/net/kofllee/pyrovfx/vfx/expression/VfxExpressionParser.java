package net.kofllee.pyrovfx.vfx.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VfxExpressionParser {
    private final String source;
    private int index;

    public VfxExpressionParser(String source) {
        this.source = source;
    }

    public VfxExpressionNode parse() {
        VfxExpressionNode node = parseConditional();
        skipWhitespace();

        if (!isAtEnd()) {
            throw error("Unexpected token: " + peek());
        }

        return node;
    }

    private VfxExpressionNode parseConditional() {
        VfxExpressionNode condition = parseOr();
        skipWhitespace();

        if (!match('?')) {
            return condition;
        }

        VfxExpressionNode whenTrue = parseConditional();
        skipWhitespace();
        expect(':');
        VfxExpressionNode whenFalse = parseConditional();

        return context -> truthy(condition.evaluate(context)) ? whenTrue.evaluate(context) : whenFalse.evaluate(context);
    }

    private VfxExpressionNode parseOr() {
        VfxExpressionNode node = parseAnd();

        while (true) {
            skipWhitespace();

            if (!match("||")) {
                return node;
            }

            VfxExpressionNode left = node;
            VfxExpressionNode right = parseAnd();

            node = context -> truthy(left.evaluate(context)) || truthy(right.evaluate(context)) ? 1.0 : 0.0;
        }
    }

    private VfxExpressionNode parseAnd() {
        VfxExpressionNode node = parseEquality();

        while (true) {
            skipWhitespace();

            if (!match("&&")) {
                return node;
            }

            VfxExpressionNode left = node;
            VfxExpressionNode right = parseEquality();

            node = context -> truthy(left.evaluate(context)) && truthy(right.evaluate(context)) ? 1.0 : 0.0;
        }
    }

    private static boolean truthy(double value) {
        return value != 0.0 && !Double.isNaN(value);
    }

    private VfxExpressionNode parseEquality() {
        VfxExpressionNode node = parseComparison();

        while (true) {
            skipWhitespace();

            if (match("==")) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseComparison();
                node = context -> left.evaluate(context) == right.evaluate(context) ? 1.0 : 0.0;
                continue;
            }

            if (match("!=")) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseComparison();
                node = context -> left.evaluate(context) != right.evaluate(context) ? 1.0 : 0.0;
                continue;
            }

            return node;
        }
    }

    private VfxExpressionNode parseComparison() {
        VfxExpressionNode node = parseTerm();

        while (true){
            skipWhitespace();

            if(match("<=")){
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseTerm();
                node = context -> left.evaluate(context) <= right.evaluate(context) ? 1.0 : 0.0;
                continue;
            }

            if(match(">=")){
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseTerm();
                node = context -> left.evaluate(context) >= right.evaluate(context) ? 1.0 : 0.0;
                continue;
            }

            if (match('<')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseTerm();
                node = context -> left.evaluate(context) < right.evaluate(context) ? 1.0 : 0.0;
                continue;
            }

            if (match('>')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseTerm();
                node = context -> left.evaluate(context) > right.evaluate(context) ? 1.0 : 0.0;
                continue;
            }

            return node;
        }
    }

    private VfxExpressionNode parseExpression() {
        VfxExpressionNode node = parseTerm();

        while (true) {
            skipWhitespace();

            if (match('+')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseTerm();
                node = context -> left.evaluate(context) + right.evaluate(context);
                continue;
            }

            if (match('-')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseTerm();
                node = context -> left.evaluate(context) - right.evaluate(context);
                continue;
            }

            return node;
        }
    }

    private VfxExpressionNode parseTerm() {
        VfxExpressionNode node = parseFactor();

        while (true) {
            skipWhitespace();

            if (match('+')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseFactor();
                node = context -> left.evaluate(context) + right.evaluate(context);
                continue;
            }

            if (match('-')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseFactor();
                node = context -> left.evaluate(context) - right.evaluate(context);
                continue;
            }

            return node;
        }
    }

    private VfxExpressionNode parseFactor() {
        VfxExpressionNode node = parsePower();

        while (true) {
            skipWhitespace();

            if (match('*')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parsePower();
                node = context -> left.evaluate(context) * right.evaluate(context);
                continue;
            }

            if (match('/')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parsePower();
                node = context -> left.evaluate(context) / right.evaluate(context);
                continue;
            }

            if (match('%')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parsePower();
                node = context -> left.evaluate(context) % right.evaluate(context);
                continue;
            }

            return node;
        }
    }

    private VfxExpressionNode parsePower() {
        VfxExpressionNode node = parseUnary();

        skipWhitespace();

        if (match('^')) {
            VfxExpressionNode left = node;
            VfxExpressionNode right = parsePower();
            return context -> Math.pow(left.evaluate(context), right.evaluate(context));
        }

        return node;
    }

    private VfxExpressionNode parseUnary() {
        skipWhitespace();

        if (match('+')) {
            return parseUnary();
        }

        if (match('-')) {
            VfxExpressionNode node = parseUnary();
            return context -> -node.evaluate(context);
        }

        if (match('!')) {
            VfxExpressionNode node = parseUnary();
            return context -> truthy(node.evaluate(context)) ? 0.0 : 1.0;
        }

        return parsePrimary();
    }

    private VfxExpressionNode parsePrimary() {
        skipWhitespace();

        if (match('(')) {
            VfxExpressionNode node = parseConditional();
            skipWhitespace();
            expect(')');
            return node;
        }

        if (isNumberStart(peek())) {
            return parseNumber();
        }

        if (isIdentifierStart(peek())) {
            return parseIdentifierOrFunction();
        }

        throw new IllegalArgumentException("Expected expression value at position " + index + " in: " + source);
    }

    private VfxExpressionNode parseNumber() {
        int start = index;
        boolean hasDot = false;

        while (!isAtEnd()) {
            char c = peek();

            if (Character.isDigit(c)) {
                index++;
                continue;
            }

            if (c == '.' && !hasDot) {
                hasDot = true;
                index++;
                continue;
            }

            break;
        }

        double value = Double.parseDouble(source.substring(start, index));
        return context -> value;
    }

    private VfxExpressionNode parseIdentifierOrFunction() {
        String name = parseIdentifierName();
        skipWhitespace();

        if (!match('(')) {
            return context -> context.getNumber(name);
        }

        List<VfxExpressionNode> args = new ArrayList<>();
        skipWhitespace();

        if (!match(')')) {
            while (true) {
                args.add(parseConditional());
                skipWhitespace();

                if (match(')')) {
                    break;
                }

                expect(',');
            }
        }

        return function(name, args);
    }

    private VfxExpressionNode function(String rawName, List<VfxExpressionNode> args) {
        String name = rawName.toLowerCase(Locale.ROOT);

        if(name.startsWith("curve.")) {
            String curveId = rawName.substring("curve.".length());
            requireArgCount(rawName, args, 1);

            return context -> context.curves().sample(curveId, args.getFirst().evaluate(context));
        }

        return switch (name){
            case "math.sin" -> unary(rawName, args, Math::sin);
            case "math.cos" -> unary(rawName, args, Math::cos);
            case "math.tan" -> unary(rawName, args, Math::tan);
            case "math.abs" -> unary(rawName, args, Math::abs);
            case "math.floor" -> unary(rawName, args, Math::floor);
            case "math.ceil" -> unary(rawName, args, Math::ceil);
            case "math.round" -> unary(rawName, args, value -> (double) Math.round(value));
            case "math.frac" -> unary(rawName, args, value -> value - Math.floor(value));
            case "math.sqrt" -> unary(rawName, args, Math::sqrt);
            case "math.pow" -> binary(rawName, args, Math::pow);
            case "math.min" -> binary(rawName, args, Math::min);
            case "math.max" -> binary(rawName, args, Math::max);
            case "math.clamp" -> ternary(rawName, args, Math::clamp);
            case "math.lerp" -> ternary(rawName, args, (a, b, t) -> a + (b - a) * t);
            case "math.inv_lerp" -> ternary(rawName, args, (a, b, x) -> a == b ? 0.0 : (x - a) / (b - a));
            case "math.smoothstep" -> ternary(rawName, args, this::smoothstep);
            case "math.wrap_degrees" -> unary(rawName, args, this::wrapDegrees);
            case "math.angle_delta" -> binary(rawName, args, this::angleDelta);
            case "math.lerprotate" -> ternary(rawName, args, this::lerpRotate);

            case "random.value" -> randomValue(rawName, args);
            case "random.range" -> randomRange(rawName, args);
            case "random.int" -> randomInt(rawName, args);
            case "random.die" -> randomDie(rawName, args, false);
            case "random.die_int" -> randomDie(rawName, args, true);

            default -> throw error("Unknown expression function: " + rawName);
        };
    }

    private VfxExpressionNode randomValue(String name, List<VfxExpressionNode> args) {
        requireArgCount(name, args, 0);
        return context -> context.random().nextDouble();
    }

    private VfxExpressionNode randomRange(String name, List<VfxExpressionNode> args) {
        requireArgCount(name, args, 2);

        return context -> {
            double low = args.get(0).evaluate(context);
            double high = args.get(1).evaluate(context);
            return low + context.random().nextDouble() * (high - low);
        };
    }

    private VfxExpressionNode randomInt(String name, List<VfxExpressionNode> args) {
        requireArgCount(name, args, 2);

        return context -> {
            int low = (int) Math.round(args.get(0).evaluate(context));
            int high = (int) Math.round(args.get(1).evaluate(context));

            if (high < low) {
                int tmp = low;
                low = high;
                high = tmp;
            }

            return low + context.random().nextInt(high - low + 1);
        };
    }

    private VfxExpressionNode randomDie(String name, List<VfxExpressionNode> args, boolean integer) {
        requireArgCount(name, args, 3);

        return context -> {
            double low = args.get(0).evaluate(context);
            double high = args.get(1).evaluate(context);
            int count = Math.max(0, (int) Math.round(args.get(2).evaluate(context)));

            double result = 0;

            for(int i = 0; i < count; i++) {
                if(integer) {
                    int intLow = (int) Math.round(low);
                    int intHigh = (int) Math.round(high);

                    if(intHigh < intLow) {
                        int tmp = intLow;
                        intLow = intHigh;
                        intHigh = tmp;
                    }

                    result += intLow + context.random().nextInt(intHigh - intLow + 1);
                }
                else {
                    result += low + context.random().nextDouble() * (high - low);
                }
            }

            return result;
        };
    }


    private double smoothstep(double edge0, double edge1, double x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0 : 1.0;
        }

        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private double lerpRotate(double from, double to, double t) {
        return from + angleDelta(from, to) * t;
    }

    private double angleDelta(double from, double to) {
        return wrapDegrees(to - from);
    }

    private double wrapDegrees(double angle) {
        double wrapped = angle % 360.0;

        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        }

        if (wrapped < -180.0) {
            wrapped += 360.0;
        }

        return wrapped;
    }

    private VfxExpressionNode ternary(String name, List<VfxExpressionNode> args, TernaryFunction function) {
        requireArgCount(name, args, 3);

        return context -> function.apply(
                args.get(0).evaluate(context),
                args.get(1).evaluate(context),
                args.get(2).evaluate(context)
        );
    }

    private VfxExpressionNode binary(String name, List<VfxExpressionNode> args, BinaryFunction function) {
        requireArgCount(name, args, 2);
        return context -> function.apply(args.getFirst().evaluate(context), args.getLast().evaluate(context));
    }

    private VfxExpressionNode unary(String name, List<VfxExpressionNode> args, UnaryFunction function) {
        requireArgCount(name, args, 1);
        return context -> function.apply(args.getFirst().evaluate(context));
    }

    private void requireArgCount(String name, List<VfxExpressionNode> args, int expected) {
        if (args.size() != expected) {
            throw error(name + " expects " + expected + " args, got " + args.size());
        }
    }

    private String parseIdentifierName() {
        int start = index;

        while (!isAtEnd() && isIdentifierPart(peek())) {
            index++;
        }

        return source.substring(start, index);

    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(index) != expected) {
            return false;
        }

        index++;
        return true;
    }

    private boolean match(String expected) {
        skipWhitespace();

        if (!source.startsWith(expected, index)) {
            return false;
        }

        index += expected.length();
        return true;
    }


    private void expect(char expected) {
        if (!match(expected)) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + index + " in: " + source);
        }
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(index);
    }

    private boolean isAtEnd() {
        return index >= source.length();
    }

    private static boolean isNumberStart(char character) {
        return Character.isDigit(character) || character == '.';
    }

    private static boolean isIdentifierStart(char character) {
        return Character.isLetter(character) || character == '_';
    }

    private static boolean isIdentifierPart(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '.';
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + index + " in: " + source);
    }

    @FunctionalInterface
    private interface UnaryFunction {
        double apply(double value);
    }

    @FunctionalInterface
    private interface BinaryFunction {
        double apply(double a, double b);
    }

    @FunctionalInterface
    private interface TernaryFunction {
        double apply(double a, double b, double c);
    }

}