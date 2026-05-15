package net.kofllee.pyrovfx.vfx.expression;

public final class VfxExpressionParser {
    private final String source;
    private int index;

    public VfxExpressionParser(String source) {
        this.source = source;
    }

    public VfxExpressionNode parse() {
        VfxExpressionNode node = parseExpression();
        skipWhitespace();

        if (!isAtEnd()) {
            throw new IllegalArgumentException("Unexpected token in expression: " + peek());
        }

        return node;
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
        VfxExpressionNode node = parseUnary();

        while (true) {
            skipWhitespace();

            if (match('*')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseUnary();
                node = context -> left.evaluate(context) * right.evaluate(context);
                continue;
            }

            if (match('/')) {
                VfxExpressionNode left = node;
                VfxExpressionNode right = parseUnary();
                node = context -> left.evaluate(context) / right.evaluate(context);
                continue;
            }

            return node;
        }
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

        return parsePrimary();
    }

    private VfxExpressionNode parsePrimary() {
        skipWhitespace();

        if (match('(')) {
            VfxExpressionNode node = parseExpression();
            skipWhitespace();
            expect(')');
            return node;
        }

        if (isNumberStart(peek())) {
            return parseNumber();
        }

        if (isIdentifierStart(peek())) {
            return parseIdentifier();
        }

        throw new IllegalArgumentException("Expected expression value at position " + index + " in: " + source);
    }

    private VfxExpressionNode parseNumber() {
        int start = index;

        while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '.')) {
            index++;
        }

        double value = Double.parseDouble(source.substring(start, index));
        return context -> value;
    }

    private VfxExpressionNode parseIdentifier() {
        int start = index;

        while (!isAtEnd() && isIdentifierPart(peek())) {
            index++;
        }

        String name = source.substring(start, index);
        return context -> context.getNumber(name);
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(index) != expected) {
            return false;
        }

        index++;
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
}