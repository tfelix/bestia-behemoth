package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

class DamageGateTest {

  private val world = testWorld()

  @Test
  fun `an effect shielding from all harm turns every blow away`() {
    val shielded = entity(shield = HarmShield.ALL)

    assertEquals(DamageGate.Verdict.IMMUNE, DamageGate.verdict(world, entity(), shielded))
  }

  @Test
  fun `an effect without a shield lets it land`() {
    val ordinary = entity()

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, entity(), ordinary))
  }

  @Test
  fun `a warded player cannot be hurt by another player`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, player(), warded))
  }

  @Test
  fun `and a warded player cannot hurt one either`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, warded, player()))
  }

  @Test
  fun `two players without the ward fight as usual`() {
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, player(), player()))
  }

  @Test
  fun `the ward does not stop a mob`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, entity(), warded))
  }

  @Test
  fun `and does not stop a warded player hitting a mob`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, warded, entity()))
  }

  @Test
  fun `a warded station cannot be knocked down by a player`() {
    val workbench = entity(shield = HarmShield.PLAYERS).also { world.add(it, PlayerStructureIdentity(structureId = 7L, ownerAccountId = 70L)) }

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, player(), workbench))
  }

  @Test
  fun `nothing harms itself`() {
    val mob = entity()

    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, mob, mob))
  }

  @Test
  fun `nothing harms what its own account owns`() {
    val master = player()
    val bestia = entity().also { world.add(it, Account(accountId = master)) }
    val workbench = entity().also { world.add(it, PlayerStructureIdentity(structureId = 7L, ownerAccountId = master)) }

    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, master, bestia))
    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, bestia, master))
    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, master, workbench))
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, player(), workbench))
  }

  @Test
  fun `a source that is gone counts as a player against a warded target`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, GONE, warded))
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, GONE, player()))
  }

  private fun player(shield: HarmShield? = null): EntityId {
    return entity(shield).also { world.add(it, Account(accountId = it)) }
  }

  private fun entity(shield: HarmShield? = null): EntityId {
    val effect = ActiveStatusEffect(definitionId = 1L, level = 1, remainingSeconds = 10f, shield = shield)

    return world.createEntity { id -> world.add(id, StatusEffects(mutableListOf(effect))) }
  }

  private companion object {
    /** No entity has this id: the caster of a fire that outlived it. */
    const val GONE = 424_242L
  }
}
