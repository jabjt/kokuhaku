package com.kokuhaku.game;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** A walk along a list of points at a steady pace, with the corners rounded off. Server and client work it out the same. */
public final class PathWalker {
    private final List<Vec3d> points;
    private final double[] along;

    public PathWalker(List<Vec3d> route) {
        List<Vec3d> smooth = new ArrayList<>(route);
        for (int pass = 0; pass < 2; pass++) smooth = rounded(smooth);
        points = smooth;
        along = new double[points.size()];
        for (int i = 1; i < along.length; i++) along[i] = along[i - 1] + points.get(i).distanceTo(points.get(i - 1));
    }

    /** Cuts every corner, keeping the two ends. */
    private static List<Vec3d> rounded(List<Vec3d> in) {
        if (in.size() < 3) return in;
        List<Vec3d> out = new ArrayList<>();
        out.add(in.get(0));
        for (int i = 0; i + 1 < in.size(); i++) {
            out.add(in.get(i).lerp(in.get(i + 1), 0.25));
            out.add(in.get(i).lerp(in.get(i + 1), 0.75));
        }
        out.add(in.get(in.size() - 1));
        return out;
    }

    public double length() {
        return along[along.length - 1];
    }

    /** Where you are this far along, 0..1. */
    public Vec3d at(double progress) {
        double target = Math.max(0, Math.min(1, progress)) * length();
        int i = 0;
        while (i < along.length - 2 && along[i + 1] < target) i++;
        double span = along[i + 1] - along[i];
        return points.get(i).lerp(points.get(i + 1), span <= 0 ? 0 : (target - along[i]) / span);
    }

    /** Which way you're heading: flat, and a unit long. */
    /** How much the way ahead drops for each block along: positive going downhill. */
    public double drop(double progress) {
        double step = 5 / Math.max(5, length());
        Vec3d d = at(Math.min(1, progress + step)).subtract(at(progress));
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        return flat < 1e-3 ? 0 : -d.y / flat;
    }

    public Vec3d heading(double progress) {
        double step = 3 / Math.max(3, length());
        Vec3d d = at(Math.min(1, progress + step)).subtract(at(Math.max(0, progress - step * 0.3)));
        Vec3d flat = new Vec3d(d.x, 0, d.z);
        return flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
    }
}
