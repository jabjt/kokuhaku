package com.kokuhaku.game;

import com.kokuhaku.Difficulty;
import com.kokuhaku.Kokuhaku;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.Mystery.Clue;
import com.kokuhaku.game.Roster.Student;
import com.kokuhaku.world.town.Town;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The School Festival, on the second Saturday. The whole school's on the sports
 * field, the stalls are out, and at seven there are fireworks. Who you watch
 * them with is the most important thing you'll do all month, because someone
 * is watching who you choose.
 */
public final class Festival {
    public static final int DAY = 6;
    static final int INVITE_TEXT = Schedule.EVENING + 60, LAST_TRAIN = 12 * 60;
    public static final int OPENS = 9 * 60, CLOSES = 20 * 60 + 30;
    private static final int FIREWORKS = 19 * 60, ROCKETS = 45, ROCKET_EVERY = 11, REACH = 90, SAFE_NIGHTS = 3;
    private static final int STALL_SPACING = 8, SEARCH = 30, MOST_CHOICES = 3, ROW_NORTH = 14, QUIET_TICKS = 110;
    private static final int TAKOYAKI = 300, FORTUNE = 200, PHOTO = 100;
    private static final String STRUGGLE = "Torn off in the struggle: ";
    private static final String[] NAMES = {"Takoyaki", "Fortunes", "Haunted House", "Photo Booth"};
    private static final int[] COLOURS = {0xE0283C, 0xF4C430, 0x4FA8E8, 0xFFFFFF, 0xF080B0, 0x70E070};
    private static final Random RANDOM = new Random();

    private static final Map<BlockPos, Integer> stalls = new HashMap<>();
    private static final List<BlockPos> placed = new ArrayList<>();
    private static final Set<Integer> done = new HashSet<>();
    private static int safeUntil = 0;
    private static boolean chose = false;

    private Festival() {
    }

    static void clear() {
        stalls.clear();
        placed.clear();
        done.clear();
        safeUntil = 0;
        chose = false;
    }

    public static boolean on(RunState s) {
        return s.day == DAY;
    }

    /** She's happy, for now. */
    static boolean safeTonight(RunState s) {
        return s.day <= safeUntil;
    }

    private static void put(ServerWorld world, BlockPos at, BlockState state) {
        world.setBlockState(at, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        placed.add(at.toImmutable());
    }

    private static boolean clear(ServerWorld world, Town town, Set<BlockPos> walked, BlockPos corner) {
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 3; dz++) {
                BlockPos at = corner.add(dx, 0, dz);
                if (walked.contains(at) || !world.getBlockState(at.down()).isSolidBlock(world, at.down())) return false;
                for (int dy = 0; dy < 4; dy++) if (!world.isAir(at.up(dy))) return false;
            }
        }
        return true;
    }

    /** Puts the stalls out on the field, on open ground away from where people walk. */
    static void setUp(ServerWorld world, Town town) {
        BlockPos middle = town.graph.pos(town.place("field"));
        Set<BlockPos> walked = new HashSet<>();
        for (int n = 0; n < town.graph.size(); n++) walked.add(town.graph.pos(n));
        List<BlockPos> sites = new ArrayList<>();
        for (int dx = -SEARCH; dx <= SEARCH; dx += 2) {
            for (int dz = -SEARCH; dz <= SEARCH; dz += 2) {
                BlockPos at = middle.add(dx, 0, dz);
                if (clear(world, town, walked, at)) sites.add(at);
            }
        }
        // In a row across the north end of the field, facing the middle.
        BlockPos row = middle.north(ROW_NORTH);
        sites.sort(Comparator.comparingDouble(b -> Math.abs(b.getZ() - row.getZ()) * 4.0 + Math.abs(b.getX() - row.getX())));
        List<BlockPos> chosen = new ArrayList<>();
        for (BlockPos site : sites) {
            if (chosen.size() == NAMES.length) break;
            if (chosen.stream().allMatch(c -> c.getManhattanDistance(site) >= STALL_SPACING)) chosen.add(site);
        }
        for (int i = 0; i < chosen.size(); i++) stall(world, chosen.get(i), i);
        Kokuhaku.LOG.info("Festival: {} stalls at {} from {} open sites", chosen.size(), chosen.stream().map(BlockPos::toShortString).toList(), sites.size());
    }

    private static void stall(ServerWorld world, BlockPos c, int kind) {
        BlockState post = Blocks.DARK_OAK_FENCE.getDefaultState();
        for (int dy = 0; dy <= 2; dy++) {
            put(world, c.add(0, dy, 0), post);
            put(world, c.add(2, dy, 0), post);
        }
        for (int dx = 0; dx <= 2; dx++) {
            BlockPos counter = c.add(dx, 0, 2);
            put(world, counter, Blocks.SPRUCE_PLANKS.getDefaultState());
            stalls.put(counter.toImmutable(), kind);
            for (int dz = 0; dz <= 2; dz++) put(world, c.add(dx, 3, dz), (dx + dz) % 2 == 0 ? Blocks.RED_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState());
        }
        put(world, c.add(0, 1, 2), post);
        put(world, c.add(2, 1, 2), post);
        put(world, c.add(0, 2, 2), post);
        put(world, c.add(2, 2, 2), post);
        put(world, c.add(1, 2, 1), Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        BlockPos sign = c.add(1, 0, 3);
        put(world, sign, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.SOUTH));
        stalls.put(sign.toImmutable(), kind);
        if (world.getBlockEntity(sign) instanceof SignBlockEntity board) {
            board.setText(new SignText().withMessage(1, Text.literal(NAMES[kind])), true);
            board.setWaxed(true);
            board.markDirty();
        }
    }

    /** Packed away by morning, as if none of it happened. */
    static void packUp(ServerWorld world) {
        for (BlockPos at : placed) world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        placed.clear();
        stalls.clear();
    }

    static boolean use(ServerPlayerEntity p, RunState s, BlockPos pos) {
        Integer kind = stalls.get(pos);
        if (kind == null) return false;
        if (s.minute < OPENS) {
            RunManager.tell(p, "", "They're still setting up.");
            return true;
        }
        switch (kind) {
            case 0 -> Talk.say(p, NAMES[0], "\"Hot takoyaki! ¥" + TAKOYAKI + " a box. Good for sharing, if you've got someone to share with.\"",
                    Talk.option("Buy a box, ¥" + TAKOYAKI + ".", q -> {
                        if (!pay(q, s, TAKOYAKI)) return;
                        s.gifts++;
                        s.markDirty();
                        RunManager.tell(q, "", "You've got something to give away. Talk to a girl.");
                    }),
                    Talk.leave("Not right now."));
            case 1 -> fortune(p, s);
            case 2 -> haunted(p, s);
            default -> photo(p, s);
        }
        return true;
    }

    /** Developer testing only: walks up to a stall, or stands with someone for the fireworks. */
    static void devUse(ServerPlayerEntity p, RunState s, int kind) {
        stalls.entrySet().stream().filter(e -> e.getValue() == kind).findFirst().ifPresent(e -> use(p, s, e.getKey()));
    }

    static BlockPos stallAt(int kind) {
        return stalls.entrySet().stream().filter(e -> e.getValue() == kind).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    static void devWatch(ServerPlayerEntity p, RunState s, int girl) {
        watch(p, s, girl);
    }

    private static boolean pay(ServerPlayerEntity p, RunState s, int price) {
        if (s.yen >= price) {
            s.yen -= price;
            s.markDirty();
            Devotion.spent(p, s);
            return true;
        }
        RunManager.tell(p, "", "You can't afford that.");
        return false;
    }

    private static void fortune(ServerPlayerEntity p, RunState s) {
        if (!done.add(1)) {
            Talk.say(p, NAMES[1], "\"One to a customer. The spirits don't repeat themselves.\"", Talk.leave("Okay."));
            return;
        }
        Talk.say(p, NAMES[1], "A first-year in a paper hat holds out a box of folded slips. \"¥" + FORTUNE + ". They're scarily good this year. I don't write them.\"",
                Talk.option("Take one, ¥" + FORTUNE + ".", q -> {
                    if (!pay(q, s, FORTUNE)) {
                        done.remove(1);
                        return;
                    }
                    List<Integer> innocent = new ArrayList<>();
                    for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (!s.mystery().isYandere(g) && !s.dead.contains(g) && !s.accused.contains(g)) innocent.add(g);
                    String name = Roster.get(innocent.get(RANDOM.nextInt(innocent.size()))).firstName();
                    s.notes.add("Festival fortune: \"It isn't " + name + ".\"");
                    s.markDirty();
                    RunManager.tell(q, "\"It isn't " + name + ".\"", "It's in your bag. You didn't ask it anything.");
                }),
                Talk.option("No, thanks.", q -> done.remove(1)));
    }

    private static void haunted(ServerPlayerEntity p, RunState s) {
        if (!done.add(2)) {
            RunManager.tell(p, "", "You're not going back in there.");
            return;
        }
        Talk.say(p, NAMES[2], "Class 2-B built it out of desks and black curtains. Sheets, red paint, someone with a torch. It's for little kids, really.",
                Talk.option("Go in.", q -> {
                    Shadow.saw(s, "She took the place of the girl in the haunted house, and held your wrist in the dark.");
                    RunManager.sendBlack(q, RunManager.homeFor(q, s), 90, "A cold hand takes your wrist in the dark.\nIt isn't part of the show.");
                    s.notes.add(Grab.torn(s).replace(STRUGGLE, "Left in your hand: "));
                    s.markDirty();
                    Whispers.foundClue();
                    RunManager.later(100, () -> RunManager.tell(q, "", "\"Was anyone working the second room?\" \"What second room?\" You check your bag."));
                }),
                Talk.option("Not today.", q -> done.remove(2)));
    }

    private static void photo(ServerPlayerEntity p, RunState s) {
        if (!done.add(3)) {
            RunManager.tell(p, "", "You don't want another one.");
            return;
        }
        Talk.say(p, NAMES[3], "\"¥" + PHOTO + " for a strip of four. Stand on the cross. Don't blink.\"",
                Talk.option("Pay ¥" + PHOTO + ".", q -> {
                    if (!pay(q, s, PHOTO)) {
                        done.remove(3);
                        return;
                    }
                    s.notes.add("Photo strip: you, alone, three times. In the fourth, someone's beside you, cropped at the shoulder. " + Roster.get(s.freeYandere()).hair().phrase(Math.max(1, s.difficulty.ordinal())));
                    s.markDirty();
                    Shadow.saw(s, "She stepped into the last frame of your photo strip.");
                    RunManager.tell(q, "", "There were four shots. You only remember being alone for three.");
                }),
                Talk.option("Maybe later.", q -> done.remove(3)));
    }

    private static ItemStack rocket() {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        NbtCompound fireworks = stack.getOrCreateSubNbt("Fireworks");
        fireworks.putByte("Flight", (byte) 2);
        NbtCompound burst = new NbtCompound();
        burst.putByte("Type", (byte) RANDOM.nextInt(3));
        burst.putIntArray("Colors", new int[]{COLOURS[RANDOM.nextInt(COLOURS.length)], COLOURS[RANDOM.nextInt(COLOURS.length)]});
        burst.putByte("Trail", (byte) 1);
        NbtList bursts = new NbtList();
        bursts.add(burst);
        fireworks.put("Explosions", bursts);
        return stack;
    }

    private static void launch(ServerWorld world, Town town) {
        BlockPos middle = town.graph.pos(town.place("field"));
        double x = middle.getX() + 22 + RANDOM.nextInt(16), z = middle.getZ() - 20 + RANDOM.nextInt(40);
        world.spawnEntity(new FireworkRocketEntity(world, x, middle.getY() + 1, z, rocket()));
    }

    /** Checked each game minute. */
    static void minute(MinecraftServer server, RunState s, List<ServerPlayerEntity> living, Town town) {
        if (!on(s) || s.minute != FIREWORKS || chose) return;
        chose = true;
        for (int i = 0; i < ROCKETS; i++) RunManager.later(i * ROCKET_EVERY, () -> launch(server.getOverworld(), town));
        Kokuhaku.LOG.info("Fireworks");
        BlockPos middle = town.graph.pos(town.place("field"));
        for (ServerPlayerEntity p : living) {
            if (p.getBlockPos().getManhattanDistance(middle) > REACH) {
                RunManager.tell(p, "", "Far off over the school, the sky starts to flash.");
                Phone.fromHer(server, s, "You didn't come. I saved you a place.");
                continue;
            }
            RunManager.tell(p, "Fireworks", "Everyone's looking at the sky. Almost everyone.");
            pickPartner(p, s);
        }
    }

    private static void pickPartner(ServerPlayerEntity p, RunState s) {
        if (Living.active(s)) {
            watch(p, s, s.livingWith);
            return;
        }
        List<Integer> girls = new ArrayList<>();
        if (s.dating >= 0 && !s.dead.contains(s.dating)) girls.add(s.dating);
        List<Integer> others = new ArrayList<>();
        for (int g = 0; g < Roster.girlsFor(s.party.size()); g++) if (g != s.dating && !s.dead.contains(g) && !s.caught.contains(g) && s.fondness[g] >= 0) others.add(g);
        others.sort(Comparator.comparingInt((Integer g) -> -s.fondness[g]).thenComparingLong(g -> s.seed * 31 + g));
        for (int g : others) if (girls.size() < MOST_CHOICES) girls.add(g);
        List<Talk.Option> options = new ArrayList<>();
        for (int g : girls) options.add(Talk.option("Watch with " + Roster.get(g).firstName() + ".", q -> watch(q, s, g)));
        options.add(Talk.option("Watch alone.", q -> watch(q, s, -1)));
        Talk.say(p, "The field", "The first rocket goes up. People are pairing off, and three or four faces turn your way. Who do you stand next to?", options.toArray(new Talk.Option[0]));
    }

    /** Beside her on the grass, or nobody, with the sky going off. */
    private static void watch(ServerPlayerEntity p, RunState s, int girl) {
        Town town = RunManager.town();
        RunManager.setBusy(p, true);
        Vec3d spot = Vec3d.ofBottomCenter(town.graph.pos(town.place("field")));
        StudentEntity her = girl < 0 ? null : RunManager.studentsOf(p.server).stream().filter(e -> e.getSkin() == girl).findFirst().orElse(null);
        if (her != null) {
            her.getNavigation().stop();
            her.setAiDisabled(true);
            her.placeAt("", spot.add(0, 0, 1.2));
            her.setBodyYaw(-90f);
            her.setHeadYaw(-90f);
        }
        p.teleport(p.getServerWorld(), spot.x, spot.y, spot.z, -90f, -42f);
        int speaker = her == null ? -1 : her.getId();
        // Nobody says anything for a while. There's the sky to look at.
        RunManager.later(QUIET_TICKS, () -> {
            if (girl < 0) {
                alone(p, s);
            } else if (s.mystery().isYandere(girl) && !s.caught.contains(girl)) {
                withHer(p, s, girl, speaker);
            } else {
                withSomeoneElse(p, s, girl, speaker);
            }
            if (her != null) her.setAiDisabled(false);
            RunManager.setBusy(p, false);
        });
    }

    /** She can't believe it. It's the safest you'll be all month. */
    private static void withHer(ServerPlayerEntity p, RunState s, int girl, int speaker) {
        Student who = Roster.get(girl);
        s.fondness[girl] = Math.min(5, s.fondness[girl] + 3);
        safeUntil = s.day + SAFE_NIGHTS - 1;
        s.markDirty();
        Kokuhaku.LOG.info("Fireworks with her: safe until day {}", safeUntil);
        Shadow.saw(s, "You chose her for the fireworks. She kept the ticket stub, and left you alone for three nights.");
        Talk.say(p, who.firstName(), "You picked me. Out of everyone. ...I'm going to remember this for the rest of your life.", speaker, Talk.leave("..."));
    }

    /** She's scared, and she tells you what she saw. Somebody else saw you ask. */
    private static void withSomeoneElse(ServerPlayerEntity p, RunState s, int girl, int speaker) {
        Student who = Roster.get(girl);
        int her = s.freeYandere();
        s.fondness[girl] = Math.min(5, s.fondness[girl] + 2);
        s.fondness[her] = Math.max(-5, s.fondness[her] - 2);
        if (s.difficulty.ordinal() >= Difficulty.HARD.ordinal()) s.livingVictim = girl;
        Clue held = s.mystery().clues.stream().filter(c -> c.witness() == who.id() && !s.found.contains(c.id())).findFirst().orElse(null);
        String line = "Thanks for asking me. Honestly. I didn't want to be on my own tonight.";
        if (held != null) {
            s.found.add(held.id());
            Whispers.foundClue();
            line += " There's something I've been wanting to tell someone. " + Dialogue.reveal(held);
            RunManager.tell(p, "", "Clue added to your bag");
        }
        s.markDirty();
        Shadow.saw(s, "You stood next to " + who.firstName() + " for the fireworks. She watched you the whole time, from the top of the school.");
        Talk.say(p, who.firstName(), line, speaker, Talk.leave("I'm glad I did."));
        RunManager.later(600, () -> Phone.fromHer(p.server, s, "I saw you. She had her hand so close to yours."));
    }

    /** Nobody takes the space beside you. Then somebody does. */
    private static void alone(ServerPlayerEntity p, RunState s) {
        int her = s.freeYandere();
        s.fondness[her] = Math.min(5, s.fondness[her] + 1);
        s.notes.add(Grab.torn(s).replace(STRUGGLE, "Slipped into your pocket: "));
        s.markDirty();
        Whispers.foundClue();
        Shadow.saw(s, "You stood alone for the fireworks. She stood behind you until the last one went out.");
        Talk.say(p, "On your own", "You watch by yourself. Halfway through you feel someone stop just behind your shoulder, close enough to smell. When it's over, there's nobody there, and something's been slipped into your pocket.", Talk.leave("Check your bag."));
    }
}
