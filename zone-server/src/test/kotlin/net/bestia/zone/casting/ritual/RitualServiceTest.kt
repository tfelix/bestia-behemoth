package net.bestia.zone.casting.ritual

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.skill.RitualParticipant
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RitualServiceTest {

  private val world = testWorld()
  private val sessions = ConnectionInfoService()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val mana = mockk<ManaConcentrationService> { every { factorAt(any()) } returns 1.0 }
  private val worldService = mockk<WorldService>(relaxed = true) {
    every { config.chunkSize } returns 32
  }
  private val service = RitualService(
    sessions, statusEffects, mana, mockk(relaxed = true), mockk(relaxed = true), worldService, mockk(relaxed = true)
  )

  private val leader = player(accountId = 1L, level = 100)
  private val circle = world.createEntity { id ->
    add(id, Position.fromVec3(Vec3L(0, 0, 0)))
    add(id, PlayerStructureIdentity(structureId = 9L, ownerAccountId = 1L))
  }

  init {
    sessions.activateSession(1L, masterId = 1L, masterEntityId = leader)
  }

  @Test
  fun `the owner opens, helpers join once each, and the owner starts`() {
    val helper = player(accountId = 2L, level = 60)

    assertNull(service.interact(world, leader, circle))
    assertNull(service.interact(world, helper, circle))
    assertEquals(OpError.RITUAL_ALREADY_JOINED, service.interact(world, helper, circle))
    assertNull(service.interact(world, leader, circle))

    assertTrue(world.get(circle, Ritual::class)!!.isRunning)
    assertTrue(world.has(helper, RitualParticipant::class))
    assertEquals(75, world.get(helper, Mana::class)!!.current, "a quarter of the maximum mana is paid")
  }

  @Test
  fun `a stranger cannot open someone else's circle`() {
    val stranger = player(accountId = 3L, level = 10)

    assertEquals(OpError.RITUAL_NOT_OPEN, service.interact(world, stranger, circle))
  }

  @Test
  fun `the leader is charged with the helpers' shares and everyone rests`() {
    val helper = player(accountId = 2L, level = 60)
    service.interact(world, leader, circle)
    service.interact(world, helper, circle)
    service.interact(world, leader, circle)

    service.complete(world, circle, world.get(circle, Ritual::class)!!)

    verify { statusEffects.applyEffect(world, leader, StatusEffectId.RITUAL_CHARGE, 1, amount = 10) }
    verify { statusEffects.applyEffect(world, leader, StatusEffectId.RITUAL_FATIGUE, 1) }
    verify { statusEffects.applyEffect(world, helper, StatusEffectId.RITUAL_FATIGUE, 1) }
    assertFalse(world.has(helper, RitualParticipant::class))
  }

  private fun player(accountId: Long, level: Int): EntityId = world.createEntity { id ->
    add(id, Account(accountId))
    add(id, Level(level))
    add(id, Mana(100, 100))
    add(id, Position.fromVec3(Vec3L(1, 0, 0)))
  }
}
