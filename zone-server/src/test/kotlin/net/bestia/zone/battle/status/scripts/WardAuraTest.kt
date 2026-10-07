package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

/** The ward against the shipped catalogue, driven by the real duration system. */
class WardAuraTest {

  private val catalogue = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(listOf(WardAura(), Warded()))
  private val statusEffects = StatusEffectService(catalogue, scripts)
  private val world = testWorld(systems = listOf(StatusEffectDurationSystem(statusEffects, catalogue, scripts)))

  private val stone = world.createEntity { id -> add(id, Position.fromVec3(STONE)) }
    .also { statusEffects.applyEffect(world, it, StatusEffectId.WARD_AURA, level = 1) }

  @Test
  fun `players inside the field cannot hurt each other`() {
    val newcomer = playerAt(STONE.plus(1_000))
    val griefer = playerAt(STONE.plus(1_200))

    pulse()

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, griefer, newcomer))
  }

  @Test
  fun `the field ends at its radius`() {
    val inside = playerAt(STONE.plus(3_400))
    val outside = playerAt(STONE.plus(3_600))

    pulse()

    val passerBy = playerAt(STONE.plus(3_700))
    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, passerBy, inside))
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, passerBy, outside))
  }

  @Test
  fun `a station in the field is warded too`() {
    val workbench = world.createEntity { id ->
      add(id, PropPose(STONE.plus(500), yaw = 0f))
      add(id, PlayerStructureIdentity(structureId = 1L, ownerAccountId = 10L))
    }

    pulse()

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, playerAt(STONE.plus(600)), workbench))
  }

  @Test
  fun `a station whose column loads again is warded by the next pulse`() {
    pulse()

    // Residency spawns a fresh static without the effects the unloaded one carried.
    val reloaded = world.createEntity { id ->
      add(id, PropPose(STONE.plus(500), yaw = 0f))
      add(id, PlayerStructureIdentity(structureId = 1L, ownerAccountId = 10L))
    }
    val outsider = playerAt(STONE.plus(9_000))
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, outsider, reloaded), "unwarded until the pulse")

    pulse()

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, outsider, reloaded))
  }

  @Test
  fun `the ward runs out soon after its bearer leaves`() {
    val wanderer = playerAt(STONE.plus(1_000))
    pulse()

    world.getOrThrow(wanderer, Position::class).x = STONE.x + 10_000
    repeat(6) { world.tick(1f) }

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, playerAt(STONE.plus(10_100)), wanderer))
  }

  @Test
  fun `the stone takes its ward with it`() {
    val newcomer = playerAt(STONE.plus(1_000))
    pulse()

    world.destroy(stone)
    repeat(6) { world.tick(1f) }

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, playerAt(STONE.plus(1_100)), newcomer))
  }

  /** One whole aura interval on the duration system's one-second beat. */
  private fun pulse() {
    repeat(2) { world.tick(1f) }
  }

  private fun playerAt(at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Account(accountId = id))
    }
  }

  private fun Vec3L.plus(metresEast: Long): Vec3L {
    return Vec3L(x + metresEast, y, z)
  }

  private companion object {
    val STONE = Vec3L(50_000, 50_000, 64)
  }
}
