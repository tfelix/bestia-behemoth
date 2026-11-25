package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.EssenceAura
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test

class EssenceAuraSystemTest {

  private val world = testWorld()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val aoi = mockk<EntityAOIService>()
  private val system = EssenceAuraSystem(statusEffects, aoi)

  @Test
  fun `captain's orders reach the wearer's own Bestias in range and no others`() {
    val captain = master(MASTER, at = 0, auras = setOf(EssenceAura.CAPTAINS_ORDERS))
    val near = bestia(owner = MASTER, at = 10)
    val far = bestia(owner = MASTER, at = 20)
    val strangers = bestia(owner = OTHER_MASTER, at = 5)

    system.update(world, 1f)

    verify { statusEffects.applyEffect(world, near, StatusEffectId.CAPTAINS_ORDERS, 1, captain) }
    verify(exactly = 0) { statusEffects.applyEffect(world, far, any<StatusEffectId>(), any(), any()) }
    verify(exactly = 0) { statusEffects.applyEffect(world, strangers, any<StatusEffectId>(), any(), any()) }
  }

  @Test
  fun `royal swarm also reaches the masters of the wearer's party`() {
    val queen = master(MASTER, at = 0, auras = setOf(EssenceAura.ROYAL_SWARM))
    world.add(queen, PartyMembership(1L, setOf(PartyMembership.Member(1L, MASTER), PartyMembership.Member(2L, OTHER_MASTER)), PartyExpShare.EACH_TAKE))
    val partner = master(OTHER_MASTER, at = 8)

    system.update(world, 1f)

    verify { statusEffects.applyEffect(world, queen, StatusEffectId.ROYAL_SWARM, 1, queen) }
    verify { statusEffects.applyEffect(world, partner, StatusEffectId.ROYAL_SWARM, 1, queen) }
  }

  @Test
  fun `a stench may poison a foe close by`() {
    val wearer = master(MASTER, at = 0, auras = setOf(EssenceAura.STENCH))
    val foe = world.createEntity { id ->
      add(id, Position(2, 0, 0))
      add(id, Health(100, 100))
    }
    every { aoi.queryEntitiesInCube(any(), any(), any()) } returns setOf(wearer, foe)

    system.update(world, 1f)

    verify { statusEffects.tryInflict(world, foe, StatusEffectId.POISON, 1, wearer, 10) }
  }

  private fun master(masterId: Long, at: Long, auras: Set<EssenceAura> = emptySet()): EntityId = world.createEntity { id ->
    add(id, Master(masterId))
    add(id, Account(masterId))
    add(id, Position(at, 0, 0))
    add(id, CombatStats(CombatValues.OBJECT_BASELINE.copy(essenceAuras = auras)))
  }

  private fun bestia(owner: Long, at: Long): EntityId = world.createEntity { id ->
    add(id, OwnedBestia(masterId = owner, playerBestiaId = at))
    add(id, Account(owner))
    add(id, Position(at, 0, 0))
  }

  private companion object {
    const val MASTER = 42L
    const val OTHER_MASTER = 43L
  }
}
