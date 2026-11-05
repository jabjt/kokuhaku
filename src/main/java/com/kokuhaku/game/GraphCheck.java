package com.kokuhaku.game;

import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.world.town.Graph;
import com.kokuhaku.world.town.Town;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The waypoints came from the map, and some links between them run straight
 * through a wall or a window. Once the town's built, this walks every link on the
 * real blocks and cuts any nobody could actually walk. Anywhere that would leave
 * cut off from everywhere else keeps its link, and gets named in the log.
 */
final class GraphCheck {
    private static final int NEIGHBOURS = 14;
    private static final double REJOIN_REACH = 14;

    private GraphCheck() {
    }

    private static int root(int[] parent, int n) {
        while (parent[n] != n) {
            parent[n] = parent[parent[n]];
            n = parent[n];
        }
        return n;
    }

    static void run(ServerWorld world, Town town) {
        Graph graph = town.graph;
        StudentEntity who = Kokuhaku.STUDENT.create(world);
        if (who == null) return;
        long started = System.currentTimeMillis();
        List<int[]> cut = new ArrayList<>();
        int links = 0, retired = 0;
        // Nobody walks along the top of a hedge, a railing or a window.
        for (int n = 0; n < graph.size(); n++) {
            var under = world.getBlockState(graph.pos(n).down()).getBlock();
            boolean onTop = under instanceof net.minecraft.block.LeavesBlock || under instanceof net.minecraft.block.PaneBlock || under instanceof net.minecraft.block.FenceBlock
                    || com.kokuhaku.world.town.Furniture.at(world, graph.pos(n).down());
            if (!onTop) continue;
            for (int b : graph.neighbours(n)) graph.unlink(n, b);
            retired++;
        }
        Kokuhaku.LOG.info("Places moved off the furniture: {}", town.offTheFurniture());
        for (int a = 0; a < graph.size(); a++) {
            Vec3d from = Vec3d.ofBottomCenter(graph.pos(a));
            for (int b : graph.neighbours(a)) {
                if (b < a) continue;
                links++;
                if (WalkRoute.walkable(world, from, Vec3d.ofBottomCenter(graph.pos(b)), who)) continue;
                graph.unlink(a, b);
                cut.add(new int[]{a, b});
            }
        }
        int[] parent = new int[graph.size()];
        for (int n = 0; n < parent.length; n++) parent[n] = n;
        for (int a = 0; a < graph.size(); a++) for (int b : graph.neighbours(a)) parent[root(parent, a)] = root(parent, b);
        // A waypoint by a window was often joined to the street outside. Join it to the room it's really in.
        int rejoined = 0;
        for (int n = 0; n < graph.size(); n++) {
            for (int other : graph.nearestFew(Vec3d.ofBottomCenter(graph.pos(n)), NEIGHBOURS)) {
                if (root(parent, n) == root(parent, other) || graph.pos(n).getSquaredDistance(graph.pos(other)) > REJOIN_REACH * REJOIN_REACH) continue;
                if (!WalkRoute.walkable(world, Vec3d.ofBottomCenter(graph.pos(n)), Vec3d.ofBottomCenter(graph.pos(other)), who)) continue;
                graph.link(n, other);
                parent[root(parent, n)] = root(parent, other);
                rejoined++;
            }
        }
        who.discard();
        // Anything still on its own keeps its old link, because being stranded is worse. There should be none.
        int kept = 0;
        for (int[] link : cut) {
            if (root(parent, link[0]) == root(parent, link[1])) continue;
            parent[root(parent, link[0])] = root(parent, link[1]);
            graph.link(link[0], link[1]);
            if (kept++ < 12) Kokuhaku.LOG.info("Link kept, nothing else reaches it: {} to {}", graph.pos(link[0]).toShortString(), graph.pos(link[1]).toShortString());
        }
        Kokuhaku.LOG.info("Graph check: {} points on top of things retired, {} links, {} cut as unwalkable, {} rejoined properly, {} kept as they were, {} ms", retired, links, cut.size() - kept, rejoined, kept, System.currentTimeMillis() - started);
    }
}
