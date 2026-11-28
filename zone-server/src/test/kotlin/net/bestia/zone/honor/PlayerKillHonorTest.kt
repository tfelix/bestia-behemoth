package net.bestia.zone.honor

import net.bestia.worldgen.core.Faction
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.update
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who pays for a kill between players, through the aggressor flag and the kill system together. */
class PlayerKillHonorTest {

  private val warZones = FakeWarZones()

  private val fixture = HonorFixture { f ->
    listOf(
      AggressorSystem(f.masters, f.aggression, f.statusEffects),
      PlayerKillHonorSystem(f.service, f.masters, f.aggression, FactionRivalry(), warZones),
    )
  }
  private val world = fixture.world

  private val attacker = fixture.master(HERE)
  private val victim = fixture.master(HERE + Vec3L(5, 0, 0))

  @Test
  fun `the first to strike becomes the aggressor`() {
    hit(attacker, victim)

    assertTrue(fixture.aggression.isAggressor(world, attacker))
    assertFalse(fixture.aggression.isAggressor(world, victim))
  }

  @Test
  fun `striking back does not make the defender an aggressor`() {
    hit(attacker, victim)
    hit(victim, attacker)

    assertFalse(fixture.aggression.isAggressor(world, victim))
  }

  @Test
  fun `a bestia's attack flags its master`() {
    hit(fixture.bestiaOf(attacker), victim)

    assertTrue(fixture.aggression.isAggressor(world, attacker))
  }

  @Test
  fun `killing a player who did not start it costs honor`() {
    hit(attacker, victim)
    kill(attacker, victim)

    assertEquals(10, globalOf(attacker))
  }

  @Test
  fun `killing the aggressor costs nothing`() {
    hit(attacker, victim)
    kill(victim, attacker)

    assertEquals(20, globalOf(victim))
  }

  @Test
  fun `a third player who kills the aggressor is defending and earns honor`() {
    val bystander = fixture.master(HERE + Vec3L(8, 0, 0))
    hit(attacker, victim)
    hit(bystander, attacker)
    kill(bystander, attacker)

    assertFalse(fixture.aggression.isAggressor(world, bystander))
    assertEquals(23, globalOf(bystander))
  }

  @Test
  fun `killing an outlaw earns no more than they owe, once a day`() {
    val outlaw = fixture.master(HERE + Vec3L(3, 0, 0), HonorLedger(debt = 23, lastLossAt = fixture.now))
    kill(attacker, outlaw)
    world.remove(outlaw, Dead::class)
    kill(attacker, outlaw)

    assertEquals(23, globalOf(attacker))
  }

  @Test
  fun `killing a thief earns the thief's bounty`() {
    fixture.statusEffects.applyEffect(world, victim, StatusEffectId.THIEF, level = 1)
    kill(attacker, victim)

    assertEquals(25, globalOf(attacker))
  }

  @Test
  fun `killing a player's bestia costs no global honor`() {
    val bestia = fixture.bestiaOf(victim)
    hit(attacker, bestia)
    kill(attacker, bestia)

    assertEquals(20, globalOf(attacker))
  }

  @Test
  fun `a death is charged once however long the body lies there`() {
    hit(attacker, victim)
    kill(attacker, victim)
    repeat(3) { world.tick(1f) }

    assertEquals(10, globalOf(attacker))
  }

  @Test
  fun `killing a rival Order's master of about the same level costs nothing`() {
    swear(attacker, Faction.CHAOS)
    swear(victim, Faction.ETERNITY)
    hit(attacker, victim)
    kill(attacker, victim)

    assertEquals(20, globalOf(attacker))
  }

  @Test
  fun `killing a rival Order's master far below costs less than an ordinary kill`() {
    val weakRival = fixture.master(HERE + Vec3L(3, 0, 0), level = 1)
    val strongAttacker = fixture.master(HERE + Vec3L(6, 0, 0), level = 11)
    swear(strongAttacker, Faction.ETERNITY)
    swear(weakRival, Faction.CHAOS)
    hit(strongAttacker, weakRival)
    kill(strongAttacker, weakRival)

    assertEquals(15, globalOf(strongAttacker))
  }

  @Test
  fun `the Circle is nobody's rival yet`() {
    swear(attacker, Faction.CHAOS)
    swear(victim, Faction.CIRCLE)
    hit(attacker, victim)
    kill(attacker, victim)

    assertEquals(10, globalOf(attacker))
  }

  @Test
  fun `guild mates are never rivals, whatever their Orders`() {
    swear(attacker, Faction.CHAOS)
    swear(victim, Faction.ETERNITY)
    listOf(attacker, victim).forEach { world.add(it, GuildMembership(guildId = 4, taxPercent = 0)) }
    hit(attacker, victim)
    kill(attacker, victim)

    assertEquals(10, globalOf(attacker))
  }

  @Test
  fun `killing an unsworn master costs the same for a sworn one`() {
    swear(attacker, Faction.CHAOS)
    hit(attacker, victim)
    kill(attacker, victim)

    assertEquals(10, globalOf(attacker))
  }

  @Test
  fun `in a war zone even a rival far below is free to kill`() {
    val weakRival = fixture.master(HERE + Vec3L(3, 0, 0), level = 1)
    val strongAttacker = fixture.master(HERE + Vec3L(6, 0, 0), level = 11)
    swear(strongAttacker, Faction.ETERNITY)
    swear(weakRival, Faction.CHAOS)
    warZones.coversAll = true
    hit(strongAttacker, weakRival)
    kill(strongAttacker, weakRival)

    assertEquals(20, globalOf(strongAttacker))
  }

  @Test
  fun `a war zone changes nothing for an unsworn victim`() {
    swear(attacker, Faction.CHAOS)
    warZones.coversAll = true
    hit(attacker, victim)
    kill(attacker, victim)

    assertEquals(10, globalOf(attacker))
  }

  @Test
  fun `a kill between rivals is reported where the victim fell, other kills are not`() {
    val bystander = fixture.master(HERE + Vec3L(8, 0, 0))
    swear(attacker, Faction.CHAOS)
    swear(victim, Faction.ETERNITY)
    hit(attacker, victim)
    kill(attacker, victim)
    hit(attacker, bystander)
    kill(attacker, bystander)

    assertEquals(listOf(HERE + Vec3L(5, 0, 0)), warZones.kills)
  }

  @Test
  fun `a kill inside a ward opens no war zone`() {
    swear(attacker, Faction.CHAOS)
    swear(victim, Faction.ETERNITY)
    fixture.statusEffects.applyEffect(world, victim, StatusEffectId.WARDED, level = 1)
    hit(attacker, victim)
    kill(attacker, victim)

    assertTrue(warZones.kills.isEmpty())
  }

  private fun hit(from: EntityId, to: EntityId) {
    world.update(to, { IncomingDamage() }) { it.add(10, from) }
    world.tick(0.1f)
    world.remove(to, IncomingDamage::class)
  }

  private fun kill(by: EntityId, victim: EntityId) {
    world.update(victim, { TakenDamage() }) { it.addDamage(by, 100) }
    world.add(victim, Dead())
    world.tick(0.1f)
  }

  private fun swear(master: EntityId, faction: Faction) {
    world.add(master, FactionMembership(faction, swornAt = fixture.now))
  }

  private fun globalOf(entity: EntityId): Int {
    return world.get(entity, Honor::class)!!.shownValue
  }

  private class FakeWarZones : WarZoneMap {
    var coversAll = false
    val kills = mutableListOf<Vec3L>()

    override fun covers(at: Vec3L): Boolean {
      return coversAll
    }

    override fun rivalKill(at: Vec3L) {
      kills.add(at)
    }
  }

  private companion object {
    val HERE = Vec3L(10_000, 10_000, 0)
  }
}
