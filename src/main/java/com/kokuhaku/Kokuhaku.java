package com.kokuhaku;

import com.kokuhaku.block.ModBlocks;
import com.kokuhaku.item.ModItems;
import com.kokuhaku.entity.StudentEntity;
import com.kokuhaku.game.RunManager;
import com.kokuhaku.net.Net;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Common (client + integrated server) setup. */
public class Kokuhaku implements ModInitializer {
    public static final String MOD_ID = "kokuhaku";
    public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Kokuhaku");

    public static final EntityType<StudentEntity> STUDENT = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "student"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, StudentEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .build());

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModItems.init();
        ModSounds.init();
        FabricDefaultAttributeRegistry.register(STUDENT, StudentEntity.createStudentAttributes());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (RunManager.isRun(server)) {
                RunManager.onStarted(server);
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Nobody at school dies by accident: a wall or a train would take their clues with them, and nobody would ever know.
            if (entity instanceof com.kokuhaku.entity.StudentEntity) return false;
            if (!(entity instanceof ServerPlayerEntity player) || !RunManager.isRun(player.server)) return true;
            // There's no respawning in this game, so the sea can't be what ends a run.
            if (source.isOf(net.minecraft.entity.damage.DamageTypes.DROWN) || source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL)) return false;
            if (!source.getName().contains("run_over")) return true;
            RunManager.hitByTrain(player);
            return false;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (RunManager.isRun(server)) {
                RunManager.tick(server);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (RunManager.isRun(server)) {
                RunManager.onJoin(handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (RunManager.isRun(server)) {
                RunManager.onLeave(handler.player);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity sp)
                    || !RunManager.isRun(sp.server)) {
                return ActionResult.PASS;
            }
            return RunManager.useBlock(sp, hit.getBlockPos());
        });

        ServerPlayNetworking.registerGlobalReceiver(Net.START, (server, player, handler, buf, sender) -> {
            int difficulty = buf.readVarInt();
            boolean devoted = buf.readBoolean(), accomplice = buf.readBoolean();
            server.execute(() -> RunManager.begin(player, difficulty, devoted, accomplice));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.OPEN_PHONE, (server, player, handler, buf, sender) ->
                server.execute(() -> com.kokuhaku.game.Phone.open(player)));
        ServerPlayNetworking.registerGlobalReceiver(Net.PHONE_REPLY, (server, player, handler, buf, sender) -> {
            int option = buf.readVarInt();
            server.execute(() -> com.kokuhaku.game.Phone.reply(player, option));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.PLANT, (server, player, handler, buf, sender) ->
                server.execute(() -> com.kokuhaku.game.Accomplice.plant(player)));

        ServerPlayNetworking.registerGlobalReceiver(Net.VOICE, (server, player, handler, buf, sender) -> {
            byte[] data = buf.readByteArray(1024);
            server.execute(() -> RunManager.voice(player, data));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.RADIO_PICK, (server, player, handler, buf, sender) -> {
            int pick = buf.readVarInt();
            server.execute(() -> com.kokuhaku.game.RunManager.radioPick(player, pick));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.STAY, (server, player, handler, buf, sender) ->
                server.execute(() -> RunManager.stay(player)));
        ServerPlayNetworking.registerGlobalReceiver(Net.SKIP_WALK, (server, player, handler, buf, sender) ->
                server.execute(() -> com.kokuhaku.game.WalkScene.skip(player)));
        ServerPlayNetworking.registerGlobalReceiver(Net.GRAB_DONE, (server, player, handler, buf, sender) -> {
            boolean free = buf.readBoolean();
            server.execute(() -> com.kokuhaku.game.Grab.done(player, free));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.LET_OUT, (server, player, handler, buf, sender) ->
                server.execute(() -> RunManager.letOut(player)));
        ServerPlayNetworking.registerGlobalReceiver(Net.CHOOSE, (server, player, handler, buf, sender) -> {
            int student = buf.readVarInt();
            int option = buf.readVarInt();
            server.execute(() -> RunManager.choose(player, student, option));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.BUY, (server, player, handler, buf, sender) -> {
            int drink = buf.readVarInt();
            server.execute(() -> RunManager.buy(player, drink));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.SHIFT_DONE, (server, player, handler, buf, sender) -> {
            int job = buf.readVarInt();
            int mistakes = buf.readVarInt();
            server.execute(() -> RunManager.shiftDone(player, job, mistakes));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.LESSON_DONE, (server, player, handler, buf, sender) -> {
            int wrong = buf.readVarInt();
            int prizes = buf.readVarInt();
            List<int[]> notes = new ArrayList<>();
            int count = Math.min(6, buf.readVarInt());
            for (int i = 0; i < count; i++) notes.add(new int[]{buf.readVarInt(), buf.readVarInt()});
            server.execute(() -> RunManager.lessonDone(player, wrong, prizes, notes));
        });

        ServerPlayNetworking.registerGlobalReceiver(Net.PROFILE, (server, player, handler, buf, sender) -> {
            int wins = buf.readVarInt(), losses = buf.readVarInt(), badges = buf.readVarInt(), lastDay = buf.readVarInt();
            server.execute(() -> {
                RunManager.profile(player, wins, losses, badges);
                com.kokuhaku.game.Story.record(wins + losses, lastDay);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.NOTE, (server, player, handler, buf, sender) -> {
            String text = buf.readString(200), signed = buf.readString(64);
            server.execute(() -> com.kokuhaku.game.Noticeboard.post(player, text, signed));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.SPECTATE, (server, player, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            server.execute(() -> RunManager.spectate(player, on));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.PE_DONE, (server, player, handler, buf, sender) -> {
            int made = buf.readVarInt();
            int shots = buf.readVarInt();
            server.execute(() -> {
                if (!com.kokuhaku.game.Practical.finished(player, made, shots)) com.kokuhaku.game.Pe.shotsIn(player, made, shots);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.CALL_GHOST, (server, player, handler, buf, sender) -> {
            String name = buf.readString();
            server.execute(() -> RunManager.callBack(player, name));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.OPEN_BAG, (server, player, handler, buf, sender) ->
                server.execute(() -> RunManager.openBag(player)));
        ServerPlayNetworking.registerGlobalReceiver(Net.ACCUSE, (server, player, handler, buf, sender) -> {
            int girl = buf.readVarInt();
            server.execute(() -> RunManager.accuse(player, girl));
        });
    }
}
