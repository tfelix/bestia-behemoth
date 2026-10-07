package net.bestia.zone.engine

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.aoi.ActivePlayerAOIService
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.aoi.EntityAudience
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.effects.StatusEffectsComponentSMSG
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticSync
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.TickOutbox
import net.bestia.zone.metrics.TickMetrics
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.stream.ChunkEntityVisibility
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import kotlin.test.Test

/**
 * A finished station is a static prop, and the ward gives it a [StatusEffects] component like anything else a
 * player owns. It reaches clients on its chunk's static batch, so the per-entity sync must stay silent about it,
 * against the real visibility index.
 */
class StaticStatusEffectSyncTest {

  private val chunkService = mockk<ChunkService> {
    every { config } returns WorldConfig(seed = 1L, widthCells = 128, heightCells = 128)
    every { normalise(any()) } answers { firstArg() }
  }
  private val subscriptions = ChunkSubscriptionService()
  private val visibility = ChunkEntityVisibility(chunkService, subscriptions)
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)
  private val world = testWorld()

  private val engine = ZoneEngine(
    world = world,
    config = WorldRulesConfig(tickRate = 20),
    entityAOIService = EntityAOIService(),
    playerAOIService = ActivePlayerAOIService(),
    outMessageProcessor = outMessageProcessor,
    outbox = TickOutbox(mockk(relaxed = true)),
    entityVisibility = visibility,
    entityAudience = EntityAudience(visibility),
    snapshotBuilder = EntitySnapshotBuilder(),
    tickMetrics = TickMetrics(SimpleMeterRegistry(), WorldRulesConfig(tickRate = 20)),
  )

  /** Holds the chunk both entities below stand in. */
  private val watcher = 90_002L.also { subscriptions.markSent(it, ChunkPos(0, 0, 0)) }

  @Test
  fun `a warded station tells no client about its effects, nor that it is gone`() {
    val workbench = world.createEntity { id ->
      add(id, PropPose(Vec3L(5, 5, 0), yaw = 0f))
      add(id, StaticSync)
      add(id, PlayerStructureIdentity(structureId = 1L))
      add(id, StatusEffects(mutableListOf(warded())))
    }

    engine.tickOnce(0.05f)
    world.destroy(workbench)

    verify(exactly = 0) { outMessageProcessor.sendToPlayer(watcher, any<SMSG>()) }
    verify(exactly = 0) { outMessageProcessor.sendToPlayer(watcher, any<Collection<SMSG>>()) }
  }

  @Test
  fun `an ordinary entity in the same chunk does reach the watcher`() {
    world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(5, 5, 0)))
      add(id, StatusEffects(mutableListOf(warded())))
    }

    engine.tickOnce(0.05f)

    verify {
      outMessageProcessor.sendToPlayer(watcher, match<Collection<SMSG>> { sent -> sent.any { it is StatusEffectsComponentSMSG } })
    }
  }

  private fun warded(): ActiveStatusEffect {
    return ActiveStatusEffect(StatusEffectId.WARDED.id, level = 1, remainingSeconds = 5f, shield = HarmShield.PLAYERS)
  }
}
