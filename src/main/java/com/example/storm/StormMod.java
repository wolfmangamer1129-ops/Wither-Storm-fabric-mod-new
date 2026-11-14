package com.example.storm;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Original mechanics prototype. Not a port of any third-party mod. */
public final class StormMod implements ModInitializer {
    private static final Map<UUID, Storm> STORMS = new HashMap<>();
    private static final class Storm {
        final UUID id;
        int age;
        int stage = 1;
        Storm(UUID id) { this.id = id; }
    }

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        ServerTickEvents.END_WORLD_TICK.register(StormMod::tick);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stormboss")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("summon").executes(context -> {
                ServerLevel level = context.getSource().getLevel();
                Vec3 pos = context.getSource().getPosition();
                WitherBoss boss = EntityType.WITHER.create(level);
                if (boss == null) return 0;
                boss.moveTo(pos.x, pos.y + 3, pos.z, 0, 0);
                boss.setCustomName(Component.literal("The Gathering Storm"));
                boss.setCustomNameVisible(true);
                level.addFreshEntity(boss);
                STORMS.put(boss.getUUID(), new Storm(boss.getUUID()));
                context.getSource().sendSuccess(() -> Component.literal("The storm is gathering!"), true);
                return 1;
            }))
            .then(Commands.literal("disrupt").executes(context -> {
                ServerLevel level = context.getSource().getLevel();
                Vec3 origin = context.getSource().getPosition();
                for (Storm storm : STORMS.values()) {
                    Entity entity = level.getEntity(storm.id);
                    if (entity instanceof WitherBoss boss && boss.distanceToSqr(origin) < 64 * 64) {
                        boss.hurt(level.damageSources().magic(), 75f);
                        level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2, 0.7f);
                        context.getSource().sendSuccess(() -> Component.literal("Storm core disrupted!"), true);
                        return 1;
                    }
                }
                context.getSource().sendFailure(Component.literal("No storm within 64 blocks."));
                return 0;
            }))
        );
    }

    private static void tick(ServerLevel level) {
        if (level.getGameTime() % 5 != 0) return;
        Iterator<Storm> iterator = STORMS.values().iterator();
        while (iterator.hasNext()) {
            Storm storm = iterator.next();
            Entity entity = level.getEntity(storm.id);
            if (!(entity instanceof WitherBoss boss)) continue; // unloaded bosses remain tracked until loaded
            if (!boss.isAlive()) { iterator.remove(); continue; }
            storm.age += 5;
            int newStage = storm.age < 1200 ? 1 : storm.age < 3000 ? 2 : storm.age < 6000 ? 3 : 4;
            if (newStage != storm.stage) {
                storm.stage = newStage;
                boss.setCustomName(Component.literal("The Gathering Storm — Stage " + newStage));
                level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3, 0.5f);
            }
            Vec3 center = boss.position();
            double radius = 12 + 6 * storm.stage;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 3, center.z, 15 * storm.stage, radius / 4, 4, radius / 4, 0.05);
            level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 2, center.z, 12, 4, 3, 4, 0.1);
            // Tractor beam: pulls nearby entities toward the boss and damages close targets.
            AABB area = boss.getBoundingBox().inflate(radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != boss && e.isAlive())) {
                Vec3 delta = center.add(0, 2, 0).subtract(target.position());
                if (delta.lengthSqr() < 0.01) continue;
                double strength = 0.11 + storm.stage * 0.035;
                target.setDeltaMovement(target.getDeltaMovement().add(delta.normalize().scale(strength)));
                target.hurtMarked = true;
                if (target.distanceToSqr(boss) < 16 && storm.age % 20 == 0) {
                    target.hurt(level.damageSources().mobAttack(boss), 2 + storm.stage * 2);
                }
                // Particle tendrils represent tentacles in this prototype.
                if (storm.age % 20 == 0) {
                    Vec3 d = target.position().subtract(center);
                    for (int i = 1; i <= 8; i++) {
                        Vec3 pt = center.add(d.scale(i / 9.0));
                        level.sendParticles(ParticleTypes.SQUID_INK, pt.x, pt.y + 1, pt.z, 2, 0.2, 0.2, 0.2, 0);
                    }
                }
            }
            // Optional limited terrain destruction; respects mobGriefing gamerule.
            if (storm.stage >= 3 && storm.age % 40 == 0 && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
                BlockPos centerBlock = boss.blockPosition();
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    BlockPos block = centerBlock.offset(dx, -2, dz);
                    if (!level.getBlockState(block).isAir() && level.getBlockState(block).getDestroySpeed(level, block) >= 0 && !level.getBlockState(block).is(Blocks.BEDROCK)) {
                        level.destroyBlock(block, false, boss);
                    }
                }
            }
        }
    }
}
