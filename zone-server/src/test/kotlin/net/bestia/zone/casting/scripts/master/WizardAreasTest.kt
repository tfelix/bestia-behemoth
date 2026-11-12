package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ecs.effects.AreaStatus
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WizardAreasTest {

  private val world = RecordingSkillWorld()

  @Test
  fun `Fire Wall lays three burning tiles across the cast that throw foes back a tile per level`() {
    // The caster stands at x = 1 and aims at x = 3, so the wall runs along y.
    FireWall(LineOfSightService()).execute(groundCtx(level = 2, element = Element.FIRE))

    assertEquals(listOf(Vec3L(3, -1, 0), Vec3L(3, 0, 0), Vec3L(3, 1, 0)), world.spawnedAreaEffects.map { it.centre })
    val tile = world.spawnedAreaEffects.first()
    assertEquals(7L, tile.visualId)
    val flames = tile.effect
    assertEquals(0L, flames.radiusTiles)
    // 15 MATK at 130%.
    assertEquals(19, flames.damagePerTick)
    assertEquals(40, flames.remainingTicks)
    assertEquals(Element.FIRE, flames.element)
    assertEquals(2, flames.mechanics.knockbackTiles)
    assertTrue(flames.mechanics.knockbackAgainstFacing)
    assertTrue(flames.mechanics.landsAsElementalSpell)
  }

  @Test
  fun `Meteor Storm drops two more meteors per level over ten seconds, each splashing a seven by seven`() {
    MeteorStorm(LineOfSightService()).execute(groundCtx(level = 3, element = Element.FIRE, aoeRadius = 3.0))

    val spawned = world.spawnedAreaEffects.single()
    assertEquals(8L, spawned.visualId)
    val storm = spawned.effect
    assertEquals(7, storm.remainingTicks)
    assertEquals(10f / 7, storm.tickIntervalSeconds)
    // 15 MATK at 150%.
    assertEquals(22, storm.damagePerTick)
    assertEquals(3L, storm.radiusTiles)
    assertEquals(3, storm.mechanics.strikeRadiusTiles)
    assertTrue(storm.mechanics.landsAsElementalSpell)
  }

  @Test
  fun `Thunder Storm calls three strikes at Lv1 over eight seconds, each on a three by three`() {
    ThunderStorm(LineOfSightService()).execute(groundCtx(level = 1, element = Element.WIND, aoeRadius = 2.0))

    val spawned = world.spawnedAreaEffects.single()
    assertEquals(10L, spawned.visualId)
    val storm = spawned.effect
    assertEquals(3, storm.remainingTicks)
    assertEquals(8f / 3, storm.tickIntervalSeconds)
    // 15 MATK at 110%.
    assertEquals(16, storm.damagePerTick)
    assertEquals(Element.WIND, storm.element)
    assertEquals(1, storm.mechanics.strikeRadiusTiles)
  }

  @Test
  fun `Storm Gust sends a wave a second for ten seconds that may freeze each foe`() {
    StormGust(LineOfSightService()).execute(groundCtx(level = 3, element = Element.WATER, aoeRadius = 4.0))

    val spawned = world.spawnedAreaEffects.single()
    assertEquals(9L, spawned.visualId)
    val gale = spawned.effect
    assertEquals(10, gale.remainingTicks)
    assertEquals(4L, gale.radiusTiles)
    // 15 MATK at 140%.
    assertEquals(21, gale.damagePerTick)
    assertEquals(listOf(AreaStatus(StatusEffectId.FROZEN, 25, null, 3)), gale.mechanics.statuses)
    assertTrue(gale.mechanics.landsAsElementalSpell)
  }

  @Test
  fun `Frost Nova bursts once around where the caster stands and only freezes`() {
    world.putPosition(BattleContextFixture.ATTACKER_ID, CASTER_NOW)
    FrostNova().execute(selfCtx(level = 2))

    val spawned = world.spawnedAreaEffects.single()
    assertEquals(CASTER_NOW, spawned.centre)
    assertEquals(13L, spawned.visualId)
    val burst = spawned.effect
    assertEquals(4L, burst.radiusTiles)
    assertEquals(1, burst.remainingTicks)
    assertEquals(0, burst.damagePerTick)
    assertEquals(listOf(AreaStatus(StatusEffectId.FROZEN, 35, null, 2)), burst.mechanics.statuses)
  }

  @Test
  fun `Earthquake shakes two metres more per level around the caster for eight seconds`() {
    world.putPosition(BattleContextFixture.ATTACKER_ID, CASTER_NOW)
    Earthquake().execute(selfCtx(level = 3))

    val spawned = world.spawnedAreaEffects.single()
    assertEquals(CASTER_NOW, spawned.centre)
    assertEquals(17L, spawned.visualId)
    val quake = spawned.effect
    assertEquals(6L, quake.radiusTiles)
    assertEquals(8, quake.remainingTicks)
    assertEquals(22, quake.damagePerTick)
    assertEquals(Element.EARTH, quake.element)
    assertTrue(quake.mechanics.landsAsElementalSpell)
  }

  private fun groundCtx(level: Int, element: Element, aoeRadius: Double? = null) = SkillContextFixture.skillCtx(
    BattleContextFixture.groundCtx(BattleContextFixture.attack(level = level, aoeRadius = aoeRadius, element = element)),
    world,
    skillLevel = level,
  )

  private fun selfCtx(level: Int) = SkillContextFixture.skillCtx(
    BattleContextFixture.entityCtx(attack = BattleContextFixture.attack(level = level, element = Element.EARTH)),
    world,
    skillLevel = level,
  )

  private companion object {
    val CASTER_NOW = Vec3L(7, 7, 0)
  }
}
