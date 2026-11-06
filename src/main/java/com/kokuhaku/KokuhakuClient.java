package com.kokuhaku;

import com.kokuhaku.client.ClientRun;
import com.kokuhaku.block.ModBlocks;
import com.kokuhaku.client.Cutscene;
import com.kokuhaku.client.DevShots;
import com.kokuhaku.game.Job;
import com.kokuhaku.screen.JobScreen;
import com.kokuhaku.screen.JumpscareScreen;
import com.kokuhaku.screen.LessonScreen;
import com.kokuhaku.screen.VendingScreen;
import com.kokuhaku.screen.WalkScreen;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.Vec3d;
import com.kokuhaku.client.KeyBindings;
import com.kokuhaku.client.MenuScene;
import com.kokuhaku.client.Party;
import com.kokuhaku.client.Voice;
import com.kokuhaku.client.WindowTitle;
import com.kokuhaku.client.RunHud;
import com.kokuhaku.client.StepRule;
import com.kokuhaku.client.StudentRenderer;
import com.kokuhaku.game.RunState;
import com.kokuhaku.net.Net;
import com.kokuhaku.screen.BagScreen;
import com.kokuhaku.screen.DialogueScreen;
import com.kokuhaku.screen.EndScreen;
import com.kokuhaku.screen.TimetableScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KokuhakuClient implements ClientModInitializer {
    public static final String MOD_ID = Kokuhaku.MOD_ID;
    public static final Identifier MENU_FONT = new Identifier(MOD_ID, "menu");

    /** Text in the menus' custom font. */
    private static final int NO_BLOOD = 0x303030;

    public static MutableText menuText(String s) {
        // The menu font stops at ordinary letters. Her marks need the plain one, or they come out as boxes.
        if (s.equals(com.kokuhaku.game.Pages.TRUE_NAME)) return Text.literal(s);
        return Text.literal(s).styled(style -> style.withFont(MENU_FONT));
    }

    /**
     * There's no sprint key. Space stays, because it's how you swim up, and
     * jumping on land is stopped by NoJumpMixin. Older installs saved it unbound.
     */
    /** Chat, the player list and every shader key: none of them have any business in the middle of this. */
    private static void unbindTheRest(MinecraftClient client) {
        if (client.options == null) return;
        boolean changed = false;
        for (net.minecraft.client.option.KeyBinding key : client.options.allKeys) {
            boolean gone = key == client.options.chatKey || key == client.options.commandKey || key == client.options.playerListKey || key.getCategory().toLowerCase(java.util.Locale.ROOT).contains("iris");
            if (gone && !key.isUnbound()) {
                key.setBoundKey(InputUtil.UNKNOWN_KEY);
                changed = true;
            }
        }
        if (client.options.tutorialStep != net.minecraft.client.tutorial.TutorialStep.NONE) {
            client.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
            changed = true;
        }
        if (changed) {
            net.minecraft.client.option.KeyBinding.updateKeysByCode();
            client.options.write();
        }
    }

    private static void unbindMovement(MinecraftClient client) {
        if (client.options == null || (client.options.sprintKey.isUnbound() && !client.options.jumpKey.isUnbound())) return;
        client.options.sprintKey.setBoundKey(InputUtil.UNKNOWN_KEY);
        if (client.options.jumpKey.isUnbound()) client.options.jumpKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_SPACE));
        client.options.getSprintToggled().setValue(false);
        client.options.getAutoJump().setValue(false);
        KeyBinding.updateKeysByCode();
        client.options.write();
    }

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Kokuhaku.STUDENT, StudentRenderer::new);
        // The map's look comes from its resource pack.
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
                new Identifier(MOD_ID, "shota"), mod, ResourcePackActivationType.ALWAYS_ENABLED));
        KeyBindings.init();
        HudRenderCallback.EVENT.register(RunHud::render);
        HudRenderCallback.EVENT.register(com.kokuhaku.client.PhoneBanner::render);
        ClientTickEvents.END_CLIENT_TICK.register(com.kokuhaku.client.PhotoAlbum::tick);
        WorldRenderEvents.END.register(context -> com.kokuhaku.client.PhotoAlbum.frameDrawn());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.kokuhaku.client.GameSettings.tick(client);
            MenuScene.tick(client);
            Voice.tick(client);
            WindowTitle.tick(client);
            unbindMovement(client);
            unbindTheRest(client);
            StepRule.tick(client);
            com.kokuhaku.client.Unease.tick(client);
            if (client.player != null && ClientRun.active) {
                // Only two slots to your name.
                if (client.player.getInventory().selectedSlot > 1) client.player.getInventory().selectedSlot = 0;
            }
            while (KeyBindings.MUTE.wasPressed()) Voice.muted = !Voice.muted;
            while (KeyBindings.PHONE.wasPressed()) {
                if (ClientRun.active && client.currentScreen == null) ClientPlayNetworking.send(Net.OPEN_PHONE, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
            }
            while (KeyBindings.TIMETABLE.wasPressed()) {
                if (ClientRun.active && client.currentScreen == null) {
                    client.setScreen(new TimetableScreen());
                }
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> MenuScene.joined(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientRun.reset();
            com.kokuhaku.client.RadioPlayer.stop(client);
            Cutscene.stop();
            Party.reset();
            MenuScene.disconnected();
            Voice.stopAll();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Cutscene.tick();
            DevShots.uiTest(client);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.PARTY, (client, handler, buf, sender) -> {
            List<Party.Member> members = new ArrayList<>();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) members.add(new Party.Member(buf.readUuid(), buf.readString(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            client.execute(() -> Party.members = members);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.RUN_START, (client, handler, buf, sender) -> client.execute(() -> MenuScene.runStarted(client)));
        ClientPlayNetworking.registerGlobalReceiver(Net.PEEK_PATH, (client, handler, buf, sender) -> {
            List<Vec3d> path = new ArrayList<>();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) path.add(new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
            client.execute(() -> Cutscene.walkPath = path);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.VOICE_OUT, (client, handler, buf, sender) -> {
            UUID who = buf.readUuid();
            float gain = buf.readFloat();
            byte[] data = buf.readByteArray(1024);
            Voice.play(client, who, gain, data); // right away: the game thread would only bunch them up
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.SHOT, (client, handler, buf, sender) -> client.execute(DevShots::take));
        ClientPlayNetworking.registerGlobalReceiver(Net.RADIO, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            net.minecraft.util.math.BlockPos at = buf.readBlockPos();
            int track = buf.readVarInt();
            client.execute(() -> com.kokuhaku.client.RadioPlayer.set(client, on, at, track));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.WALK_CLOCK, (client, handler, buf, sender) -> {
            int age = buf.readVarInt();
            client.execute(() -> com.kokuhaku.client.Cutscene.walkClock(age));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.RADIO_OPEN, (client, handler, buf, sender) -> {
            boolean under = buf.readBoolean();
            int[] tracks = buf.readIntArray();
            client.execute(() -> {
                com.kokuhaku.client.RadioPlayer.playlist = tracks;
                client.setScreen(new com.kokuhaku.screen.RadioScreen(under));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.TRAY, (client, handler, buf, sender) -> {
            String item = buf.readString();
            client.execute(() -> com.kokuhaku.client.ClientRun.tray = item.isEmpty() ? null : new net.minecraft.item.ItemStack(net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier(item))));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.WALK_HOLD, (client, handler, buf, sender) -> {
            boolean hold = buf.readBoolean();
            client.execute(() -> Cutscene.holdWalk(hold));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.CUTSCENE, (client, handler, buf, sender) -> {
            Cutscene.Type type = Cutscene.Type.values()[buf.readVarInt()];
            Vec3d cam = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d look = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d head = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d foot = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int ticks = buf.readVarInt();
            String text = buf.readString();
            client.execute(() -> Cutscene.start(type, cam, look, head, foot, ticks, text));
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(com.kokuhaku.client.ChalkBoard::render);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.EVIDENCE);
        // With blood off, the stains are only ever dark marks.
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> com.kokuhaku.client.GameSettings.noBlood ? NO_BLOOD : 0xFFFFFF, ModBlocks.EVIDENCE);
        ClientPlayNetworking.registerGlobalReceiver(Net.LESSON, (client, handler, buf, sender) -> {
            NbtCompound nbt = buf.readNbt();
            client.execute(() -> client.setScreen(new LessonScreen(nbt)));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.PRACTICAL, (client, handler, buf, sender) -> {
            boolean kitchen = buf.readBoolean();
            int level = buf.readVarInt();
            client.execute(() -> client.setScreen(new com.kokuhaku.screen.PracticalScreen(kitchen, level)));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.HOOPS, (client, handler, buf, sender) -> {
            int level = buf.readVarInt();
            client.execute(() -> client.setScreen(new com.kokuhaku.screen.HoopScreen(level)));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.GHOST, (client, handler, buf, sender) -> client.execute(() -> {
            client.options.hudHidden = false;
            ClientRun.ghost = true;
            client.setScreen(null);
        }));
        ClientPlayNetworking.registerGlobalReceiver(Net.OPEN_VENDING, (client, handler, buf, sender) -> {
            int yen = buf.readVarInt();
            client.execute(() -> client.setScreen(new VendingScreen(yen)));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.OPEN_JOB, (client, handler, buf, sender) -> {
            Job job = Job.values()[buf.readVarInt()];
            int yen = buf.readVarInt();
            client.execute(() -> client.setScreen(new JobScreen(job, yen)));
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.STATE, (client, handler, buf, sender) -> {
            int day = buf.readVarInt(), minute = buf.readVarInt(), guesses = buf.readVarInt();
            Difficulty difficulty = Difficulty.values()[buf.readVarInt()];
            RunState.Status status = RunState.Status.values()[buf.readVarInt()];
            int yen = buf.readVarInt(), locker = buf.readVarInt(), party = buf.readVarInt(), alive = buf.readVarInt();
            long week = buf.readLong();
            String objective = buf.readString();
            int dread = buf.readVarInt();
            int temper = buf.readVarInt();
            net.minecraft.util.math.BlockPos target = buf.readBoolean() ? buf.readBlockPos() : null;
            int unread = buf.readVarInt();
            int chase = buf.readVarInt();
            boolean slowing = buf.readBoolean();
            client.execute(() -> {
                ClientRun.chase = chase;
                ClientRun.slowing = slowing;
                ClientRun.unread = unread;
                ClientRun.target = target;
                ClientRun.dread = dread;
                ClientRun.temper = temper;
                ClientRun.week = week;
                ClientRun.objective = objective;
                ClientRun.party = party;
                ClientRun.alive = alive;
                ClientRun.active = status == RunState.Status.PLAYING;
                ClientRun.yen = yen;
                ClientRun.locker = locker;
                ClientRun.day = day;
                ClientRun.minute = minute;
                ClientRun.guessesLeft = guesses;
                ClientRun.difficulty = difficulty;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.ANNOUNCE, (client, handler, buf, sender) -> {
            ClientRun.Announcement a = new ClientRun.Announcement(buf.readString(), buf.readString(), buf.readVarInt());
            client.execute(() -> ClientRun.announce(a));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.DIALOGUE, (client, handler, buf, sender) -> {
            NbtCompound nbt = buf.readNbt();
            client.execute(() -> {
                if (client.currentScreen instanceof WalkScreen walk) walk.show(nbt);
                else client.setScreen(new DialogueScreen(nbt));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.BAG, (client, handler, buf, sender) -> {
            NbtCompound nbt = buf.readNbt();
            client.execute(() -> client.setScreen(new BagScreen(nbt)));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.REEL, (client, handler, buf, sender) -> {
            String reel = buf.readString();
            client.execute(() -> ClientRun.reel = reel);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.PHONE_PING, (client, handler, buf, sender) -> {
            String chat = buf.readString(), from = buf.readString(), body = buf.readString();
            boolean dead = buf.readBoolean(), photo = buf.readBoolean();
            client.execute(() -> com.kokuhaku.client.PhoneBanner.show(chat, from, body, dead, photo));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.PHOTO_TAKE, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            client.execute(() -> com.kokuhaku.client.PhotoAlbum.request(id));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.PHONE, (client, handler, buf, sender) -> {
            NbtCompound nbt = buf.readNbt();
            client.execute(() -> com.kokuhaku.screen.PhoneScreen.receive(client, nbt));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.END, (client, handler, buf, sender) -> {
            int kind = buf.readVarInt(), yandere = buf.readVarInt();
            String text = buf.readString();
            client.execute(() -> com.kokuhaku.client.Cutscene.stop());
            client.execute(() -> client.setScreen(kind >= 2 && com.kokuhaku.screen.EndingsScreen.ON ? new com.kokuhaku.screen.EndingsScreen() : kind >= 2 ? new JumpscareScreen(kind == 5 ? JumpscareScreen.UNMASK + text : text) : new EndScreen(kind == 1, text)));
        });
    }
}
