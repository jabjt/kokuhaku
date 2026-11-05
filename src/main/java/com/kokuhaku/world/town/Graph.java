package com.kokuhaku.world.town;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Walkable waypoints through the town (streets, doorways, hallways, stairs,
 * seats). Students route along it with Dijkstra and use Minecraft's own
 * pathfinding for each short hop between neighbouring nodes.
 */
public final class Graph {
    private final List<BlockPos> nodes = new ArrayList<>();
    private final List<List<Integer>> edges = new ArrayList<>();
    private final Map<BlockPos, Integer> byPos = new HashMap<>();
    private final java.util.Set<Integer> soft = new java.util.HashSet<>();
    /** People keep to the pavement: grass only ever gets walked on by someone who's going to it. */
    private static final double SOFT_COST = 12;

    /** Adds a node where an entity stands (y = feet), or returns the existing one there. */
    public int node(int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        Integer existing = byPos.get(p);
        if (existing != null) return existing;
        int id = nodes.size();
        nodes.add(p);
        edges.add(new ArrayList<>());
        byPos.put(p, id);
        return id;
    }

    /** Marks a waypoint as grass or dirt: walkable, but people prefer the paths. */
    public void setSoft(int id) {
        soft.add(id);
    }

    public void link(int a, int b) {
        if (a == b || edges.get(a).contains(b)) return;
        edges.get(a).add(b);
        edges.get(b).add(a);
    }

    public void unlink(int a, int b) {
        edges.get(a).remove((Integer) b);
        edges.get(b).remove((Integer) a);
    }

    /** The waypoint at exactly this spot, if there is one. */
    public Integer at(BlockPos p) {
        return byPos.get(p);
    }

    public List<Integer> neighbours(int n) {
        return List.copyOf(edges.get(n));
    }

    /** The closest few nodes, nearest first, weighted the way {@link #nearest} is. */
    public List<Integer> nearestFew(Vec3d p, int count) {
        PriorityQueue<double[]> best = new PriorityQueue<>((a, b) -> Double.compare(b[0], a[0]));
        for (int i = 0; i < nodes.size(); i++) {
            if (edges.get(i).isEmpty()) continue;
            BlockPos n = nodes.get(i);
            double dx = n.getX() + 0.5 - p.x, dy = (n.getY() - p.y) * 4, dz = n.getZ() + 0.5 - p.z;
            best.add(new double[]{dx * dx + dy * dy + dz * dz, i});
            if (best.size() > count) best.poll();
        }
        List<Integer> out = new ArrayList<>();
        while (!best.isEmpty()) out.add(0, (int) best.poll()[1]);
        return out;
    }

    /** Nodes every {@code step} blocks along a straight line, linked in order. Returns them. */
    public List<Integer> line(int x1, int y, int z1, int x2, int z2, int step) {
        List<Integer> out = new ArrayList<>();
        int len = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        int n = Math.max(1, (int) Math.ceil(len / (double) step));
        for (int i = 0; i <= n; i++) {
            int x = x1 + Math.round((x2 - x1) * i / (float) n);
            int z = z1 + Math.round((z2 - z1) * i / (float) n);
            out.add(node(x, y, z));
        }
        for (int i = 1; i < out.size(); i++) link(out.get(i - 1), out.get(i));
        return out;
    }

    public BlockPos pos(int id) {
        return nodes.get(id);
    }

    public int size() {
        return nodes.size();
    }

    /** Closest node, weighting height heavily so floors of a building don't get mixed up. */
    public int nearest(Vec3d p) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            if (edges.get(i).isEmpty()) continue;
            BlockPos n = nodes.get(i);
            double dx = n.getX() + 0.5 - p.x, dy = (n.getY() - p.y) * 4, dz = n.getZ() + 0.5 - p.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** A node a few random steps away from {@code start} along the network. */
    public int wander(int start, int steps, java.util.Random r) {
        int at = start;
        for (int i = 0; i < steps; i++) {
            List<Integer> next = edges.get(at);
            if (next.isEmpty()) break;
            at = next.get(r.nextInt(next.size()));
        }
        return at;
    }

    /** Number of nodes you can't get to from {@code start}. Should be 0. */
    public int unreachableFrom(int start) {
        boolean[] seen = new boolean[nodes.size()];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>(List.of(start));
        seen[start] = true;
        int count = 1;
        while (!queue.isEmpty()) {
            for (int v : edges.get(queue.poll())) {
                if (!seen[v]) {
                    seen[v] = true;
                    count++;
                    queue.add(v);
                }
            }
        }
        return nodes.size() - count;
    }

    /** Node ids from {@code from} to {@code to} inclusive, or just [to] if unreachable. */
    public List<Integer> path(int from, int to) {
        return path(from, to, SOFT_COST);
    }

    /** The same, with a say in how much people mind leaving the pavement. */
    public List<Integer> path(int from, int to, double softCost) {
        double[] dist = new double[nodes.size()];
        int[] prev = new int[nodes.size()];
        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(prev, -1);
        dist[from] = 0;
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{0, from});
        while (!queue.isEmpty()) {
            double[] cur = queue.poll();
            int u = (int) cur[1];
            if (cur[0] > dist[u]) continue;
            if (u == to) break;
            for (int v : edges.get(u)) {
                double step = Math.sqrt(nodes.get(u).getSquaredDistance(nodes.get(v)));
                double d = dist[u] + (soft.contains(v) ? step * softCost : step);
                if (d < dist[v]) {
                    dist[v] = d;
                    prev[v] = u;
                    queue.add(new double[]{d, v});
                }
            }
        }
        if (dist[to] == Double.MAX_VALUE) return List.of(to);
        List<Integer> out = new ArrayList<>();
        for (int v = to; v != -1; v = prev[v]) out.add(v);
        Collections.reverse(out);
        return out;
    }
}
