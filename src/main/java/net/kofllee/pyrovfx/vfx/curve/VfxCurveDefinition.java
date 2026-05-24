package net.kofllee.pyrovfx.vfx.curve;

import net.kofllee.pyrovfx.vfx.type.VfxCurveType;

import java.util.Comparator;
import java.util.List;

public record VfxCurveDefinition(VfxCurveType type, List<VfxCurvePoint> points) {
    public VfxCurveDefinition {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Curve must have at least one point");
        }

        points = points.stream().sorted(Comparator.comparingDouble(VfxCurvePoint::x)).toList();
    }

    public double sample(double x){
        if(points.size() == 1) {
            return points.getFirst().x();
        }

        if(x <= points.getFirst().x()){
            return points.getFirst().x();
        }

        if(x >= points.getLast().x()){
            return points.getLast().x();
        }

        int rightIndex = findRightIndex(x);
        int leftIndex = rightIndex - 1;

        VfxCurvePoint right = points.get(rightIndex);
        VfxCurvePoint left = points.get(leftIndex);

        double width = right.x() - left.x();
        double t = width == 0.0 ? 0.0 : (x - left.x()) / width;

        return switch (type){
            case LINEAR -> lerp(left.y(), right.y(), t);
            case STEP -> left.y();
            case CATMULL -> sampleCatmull(leftIndex, rightIndex, t);
            case BEZIER -> sampleBezier(left, right, t);
        };
    }

    private double sampleCatmull(int leftIndex, int rightIndex, double t) {
        double p0 = points.get(Math.max(0, leftIndex - 1)).y();
        double p1 = points.get(leftIndex).y();
        double p2 = points.get(rightIndex).y();
        double p3 = points.get(Math.min(points.size() - 1, rightIndex + 1)).y();

        double t2 = t * t;
        double t3 = t2 * t;

        return 0.5 * (2.0 * p1 + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private double sampleBezier(VfxCurvePoint left, VfxCurvePoint right, double t) {
        double smooth = t * t * (3.0 - 2.0 * t);
        return lerp(left.y(), right.y(), smooth);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }


    private int findRightIndex(double x) {
        for(int i = 0; i < points.size(); i++) {
            if(x <= points.get(i).x()) {
                return i;
            }
        }

        return points.size() - 1;
    }
}
