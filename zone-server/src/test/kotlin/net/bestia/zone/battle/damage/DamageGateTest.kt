package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.skill.SkillId
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
    val workbench = entity(shield = HarmShield.PLAYERS).also { world.add(it, PlayerStructureIdentity(structureId = 7L, ownerAccountId = 70L, ownerMasterId = 1L)) }

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
    val workbench = entity().also { world.add(it, PlayerStructureIdentity(structureId = 7L, ownerAccountId = master, ownerMasterId = 1L)) }

    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, master, bestia))
    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, bestia, master))
    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, master, workbench))
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, player(), workbench))
  }

  @Test
  fun `a source that is gone still answers to the account that made it`() {
    val warded = player(shield = HarmShield.PLAYERS)
    val ownBestia = entity().also { world.add(it, Account(accountId = MAKER)) }

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, GONE, warded, sourceOwner = MAKER))
    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, GONE, ownBestia, sourceOwner = MAKER))
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, GONE, player(), sourceOwner = MAKER))
  }

  @Test
  fun `a gone source nobody owned is a mob's, which the ward does not stop`() {
    val warded = player(shield = HarmShield.PLAYERS)

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, GONE, warded, sourceOwner = null))
  }

  @Test
  fun `a guild's banner is safe from its own members and their bestias, even with Approval`() {
    val banner = world.createEntity { id -> add(id, GuildOwned(GUILD)) }
    val member = player().also { world.add(it, GuildMembership(GUILD, taxPercent = 5, skillLevels = APPROVED)) }
    val membersBestia = world.createEntity { id -> add(id, Account(accountId = member)) }

    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, member, banner))
    assertEquals(DamageGate.Verdict.OWN, DamageGate.verdict(world, membersBestia, banner))
  }

  @Test
  fun `another guild with Approval and its bestias may harm a guild structure`() {
    val banner = world.createEntity { id -> add(id, GuildOwned(GUILD)) }
    val enemy = player().also { world.add(it, GuildMembership(OTHER_GUILD, taxPercent = 5, skillLevels = APPROVED)) }
    val enemysBestia = world.createEntity { id -> add(id, Account(accountId = enemy)) }

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, enemy, banner))
    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, enemysBestia, banner))
  }

  @Test
  fun `a guild without Approval, a guildless master and a mob cannot harm a guild structure`() {
    val banner = world.createEntity { id -> add(id, GuildOwned(GUILD)) }
    val unapproved = player().also { world.add(it, GuildMembership(OTHER_GUILD, taxPercent = 5)) }
    val guildless = player()
    val mob = entity()

    assertEquals(DamageGate.Verdict.UNAPPROVED, DamageGate.verdict(world, unapproved, banner))
    assertEquals(DamageGate.Verdict.UNAPPROVED, DamageGate.verdict(world, guildless, banner))
    assertEquals(DamageGate.Verdict.UNAPPROVED, DamageGate.verdict(world, mob, banner))
  }

  @Test
  fun `a ward protects a guild's banner like anything else a player owns`() {
    val banner = world.createEntity { id ->
      add(id, GuildOwned(GUILD))
      add(id, StatusEffects(mutableListOf(ActiveStatusEffect(1L, 1, 10f, shield = HarmShield.PLAYERS))))
    }

    assertEquals(DamageGate.Verdict.WARDED, DamageGate.verdict(world, player(), banner))
  }

  private fun player(shield: HarmShield? = null): EntityId {
    return entity(shield).also { world.add(it, Account(accountId = it)) }
  }

  private fun entity(shield: HarmShield? = null): EntityId {
    val effect = ActiveStatusEffect(definitionId = 1L, level = 1, remainingSeconds = 10f, shield = shield)

    return world.createEntity { id -> world.add(id, StatusEffects(mutableListOf(effect))) }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    val APPROVED = mapOf(SkillId.OFFICIAL_GUILD_APPROVAL.name to 1)

    /** No entity has this id: the caster of a fire that outlived it. */
    const val GONE = 424_242L

    /** The account that [GONE] belonged to. */
    const val MAKER = 515_151L
  }
}
