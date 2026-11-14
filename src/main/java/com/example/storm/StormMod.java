
package com.example.storm;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class StormMod implements ModInitializer {

    private static final Map<UUID, Storm> STORMS =
            new HashMap<>();

    private static final class Storm {
        final UUID id;
        int age = 0;
        int stage = 1;

        Storm(UUID id) {
            this.id = id;
        }
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                register(dispatcher)
        );

        ServerTickEvents.END_LEVEL_TICK.register(
            StormMod::tick
        );
    }

    private static void register(
            CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
            Commands.literal("stormboss")
                .then(Commands.literal("activate")
                    .executes(context -> {
                        ServerLevel level =
                            context.getSource().getLevel();

                        Vec3 origin =
                            context.getSource().getPosition();

                        WitherBoss nearest = null;
                        double closest = 64 * 64;

                        AABB area = new AABB(
                            origin.x - 64,
                            origin.y - 64,
                            origin.z - 64,
                            origin.x + 64,
                            origin.y + 64,
                            origin.z + 64
                        );

                        for (WitherBoss boss :
                            level.getEntitiesOfClass(
                                WitherBoss.class, area)) {

                            double distance =
                                boss.distanceToSqr(origin);

                            if (distance < closest) {
                                closest = distance;
                                nearest = boss;
                            }
                        }

                        if (nearest == null) {
                            context.getSource().sendFailure(
                                Component.literal(
                                    "Summon a Wither first."
                                )
                            );
                            return 0;
                        }

                        STORMS.put(
                            nearest.getUUID(),
                            new Storm(nearest.getUUID())
                        );

                        nearest.setCustomName(
                            Component.literal(
                                "The Gathering Storm - Stage 1"
                            )
                        );

                        nearest.setCustomNameVisible(true);

                        context.getSource().sendSuccess(
                            () -> Component.literal(
                                "The storm is gathering!"
                            ),
                            true
                        );

                        return 1;
                    })
                )
        );
    }

    private static void tick(ServerLevel level) {

        if (level.getGameTime() % 5 != 0) {
            return;
        }

        Iterator<Storm> iterator =
            STORMS.values().iterator();

        while (iterator.hasNext()) {

            Storm storm = iterator.next();

            Entity entity = level.getEntity(storm.id);

            if (!(entity instanceof WitherBoss boss)) {
                continue;
            }

            if (!boss.isAlive()) {
                iterator.remove();
                continue;
            }

            storm.age += 5;

            int newStage =
                storm.age < 1200 ? 1 :
                storm.age < 3000 ? 2 :
                storm.age < 6000 ? 3 : 4;

            if (newStage != storm.stage) {
                storm.stage = newStage;

                boss.setCustomName(
                    Component.literal(
                        "The Gathering Storm - Stage "
                        + newStage
                    )
                );
            }

            Vec3 center = boss.position();

            double radius = 12 + 6 * storm.stage;

            level.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                center.x,
                center.y + 3,
                center.z,
                15 * storm.stage,
                radius / 4,
                4,
                radius / 4,
                0.05
            );

            level.sendParticles(
                ParticleTypes.PORTAL,
                center.x,
                center.y + 2,
                center.z,
                12,
                4,
                3,
                4,
                0.1
            );

            AABB area =
                boss.getBoundingBox().inflate(radius);

            for (LivingEntity target :
                level.getEntitiesOfClass(
                    LivingEntity.class,
                    area,
                    e -> e != boss && e.isAlive()
                )) {

                Vec3 delta = center.add(0, 2, 0)
                    .subtract(target.position());

                if (delta.lengthSqr() < 0.01) {
                    continue;
                }

                double strength =
                    0.11 + storm.stage * 0.035;

                target.setDeltaMovement(
                    target.getDeltaMovement().add(
                        delta.normalize().scale(strength)
                    )
                );

                if (storm.age % 20 == 0) {

                    Vec3 direction =
                        target.position().subtract(center);

                    for (int i = 1; i <= 8; i++) {

                        Vec3 point = center.add(
                            direction.scale(i / 9.0)
                        );

                        level.sendParticles(
                            ParticleTypes.SQUID_INK,
                            point.x,
                            point.y + 1,
                            point.z,
                            2,
                            0.2,
                            0.2,
                            0.2,
                            0
                        );
                    }
                }
            }
        }
    }
}
