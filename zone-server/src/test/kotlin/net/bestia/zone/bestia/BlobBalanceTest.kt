package net.bestia.zone.bestia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.bestia.zone.account.master.status.EffortValueCostCalculator
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import net.bestia.zone.boot.MobImporterBootRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * Reads the real `mob/blob.yml` and holds it against a real fresh master, the way `ItemCatalogWeightTest`
 * reads the real `items.yml`.
 *
 * A blob is the first thing a new player meets, and how hard it hits is decided by four files that do not
 * reference one another: its own attributes here, the damage formula, the soft-defence formula, and the HP
 * pool a level-1 master is created with. They drifted once already - a blob killed a fresh master in two
 * swings, and nothing in any single file looked wrong. This is the test that would have said so.
 *
 * The numbers asserted are deliberately loose bounds rather than exact damage: the point is that a blob stays
 * a lesson rather than an ambush, not that it deals precisely N.
 */
class BlobBalanceTest {

  private val blob = readBlob()

  /** Every attribute at the creation cap, which is what `MasterFactory.evenlySpreadEffortValues` produces. */
  private val freshMasterAttribute = EffortValueCostCalculator.BALANCED_EFFORT_VALUE
  private val freshMasterHp = ConditionValueCalculator().computeMaxHp(MASTER_LEVEL, freshMasterAttribute)

  /** Worst case for the master: no variance shaved off the blob's swing, so every hit is its ceiling. */
  private val melee = MeleePhysicalDamageCalculator(FixedRandom(0f))

  @Test
  fun `a blob needs several swings to kill a master in the starter kit`() {
    val swings = swingsToKill(hardDefense = STARTER_KIT_DEF)

    assertTrue(
      swings >= MIN_SWINGS_GEARED,
      "a blob kills a geared fresh master in $swings swings, wanted at least $MIN_SWINGS_GEARED"
    )
  }

  /** Gear helps, but the blob nerf has to carry this on its own - 5% off a small number is half a point. */
  @Test
  fun `a blob is survivable even with nothing on`() {
    val swings = swingsToKill(hardDefense = 0)

    assertTrue(
      swings >= MIN_SWINGS_BARE,
      "a blob kills an ungeared fresh master in $swings swings, wanted at least $MIN_SWINGS_BARE"
    )
  }

  /** The other half of "weakest thing in the world": the knife has to settle it in one. */
  @Test
  fun `a master in the starter kit kills a blob in one swing`() {
    val ctx = BattleContextFixture.entityCtx(
      attackerEntity = master(hardDefense = STARTER_KIT_DEF),
      defenderEntity = blobEntity()
    ) as EntityBattleContext

    val damage = melee.calculateDamage(
      ctx.copy(weapon = ctx.weapon.copy(atk = NOVICE_KNIFE_ATK)),
      isCritical = false
    )

    assertTrue(damage >= blob.health, "a geared master deals $damage to a blob with ${blob.health} HP")
  }

  /**
   * A known residual, pinned so it cannot quietly get worse.
   *
   * `PhysicalAttackStrategy.MIN_CRIT_CHANCE` is an unconditional 1% floor - no attribute spread takes a
   * creature below it - and a critical bypasses both kinds of defence. So roughly one blob swing in a hundred
   * still lands for more than a fresh master's whole pool, and no mob tuning can reach that: the lever is the
   * level-1 HP pool (`ConditionValueCalculator`, where `BASE_VALUE_HP` contributes 0.8 at level 1).
   *
   * What this asserts is the bound, not the absence: a critical must stay within touching distance of the
   * pool rather than becoming a rout.
   */
  @Test
  fun `a blob's critical stays close to a fresh master's pool`() {
    val ctx = BattleContextFixture.entityCtx(
      attackerEntity = blobEntity(),
      defenderEntity = master(hardDefense = STARTER_KIT_DEF)
    ) as EntityBattleContext

    val critical = melee.calculateDamage(ctx.copy(weapon = ctx.weapon.copy(atk = 0)), isCritical = true)

    assertTrue(
      critical <= freshMasterHp * 2,
      "a blob critical deals $critical against a pool of $freshMasterHp"
    )
  }

  private fun swingsToKill(hardDefense: Int): Int {
    val ctx = BattleContextFixture.entityCtx(
      attackerEntity = blobEntity(),
      defenderEntity = master(hardDefense = hardDefense)
    ) as EntityBattleContext

    // Bare-handed: a blob holds nothing, and nothing in the catalogue would fit it if it did.
    val perSwing = melee.calculateDamage(ctx.copy(weapon = ctx.weapon.copy(atk = 0)), isCritical = false)

    return (freshMasterHp + perSwing - 1) / perSwing
  }

  private fun blobEntity() = BattleContextFixture.battleEntity(
    // Mobs carry no Level component, so a fight reads them at 1 whatever the catalogue says.
    level = 1,
    strength = blob.attributes.strength,
    intelligence = blob.attributes.intelligence,
    vitality = blob.attributes.vitality,
    dexterity = blob.attributes.dexterity,
    willpower = blob.attributes.willpower,
    agility = blob.attributes.agility,
    maxHealth = blob.health,
    id = BattleContextFixture.DEFENDER_ID
  )

  private fun master(hardDefense: Int) = BattleContextFixture.battleEntity(
    level = MASTER_LEVEL,
    strength = freshMasterAttribute,
    intelligence = freshMasterAttribute,
    vitality = freshMasterAttribute,
    dexterity = freshMasterAttribute,
    willpower = freshMasterAttribute,
    agility = freshMasterAttribute,
    hardDefense = hardDefense,
    maxHealth = freshMasterHp
  )

  private fun readBlob(): MobImporterBootRunner.MobYmlDto {
    val mapper = ObjectMapper(YAMLFactory())
      .registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    return ClassPathResource("mob/blob.yml").inputStream.use {
      mapper.readValue(it, MobImporterBootRunner.MobYmlDto::class.java)
    }
  }

  private companion object {
    const val MASTER_LEVEL = 1

    /** `novice_shirt` 3 plus `novice_boots` 2, in percentage points - see `items.yml`. */
    const val STARTER_KIT_DEF = 5

    const val NOVICE_KNIFE_ATK = 10

    /**
     * Floors rather than the current values, which are 5 and 4 against the worst swing a blob can roll. Set a
     * notch below so that ordinary tuning has somewhere to move and only a regression toward the two-swing
     * death this test exists to rule out trips them.
     */
    const val MIN_SWINGS_GEARED = 4
    const val MIN_SWINGS_BARE = 3
  }
}
