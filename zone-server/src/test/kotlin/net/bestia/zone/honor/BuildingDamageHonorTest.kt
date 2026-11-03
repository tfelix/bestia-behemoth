package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildingDamageHonorTest {

  private val fixture = HonorFixture { f -> listOf(BuildingDamageHonorSystem(f.service, f.masters)) }
  private val world = fixture.world

  private val vandal = fixture.master(HERE)

  init {
    // Somebody to see it.
    fixture.master(HERE + Vec3L(10, 0, 0))
  }

  @Test
  fun `a scratch on a house costs nothing`() {
    val house = building(StaticEntityKind.BUILDING_RESIDENCE)

    damage(house, 10)

    assertEquals(20, globalOf(vandal))
  }

  @Test
  fun `more than a tenth of a house costs honor once however long it goes on`() {
    val house = building(StaticEntityKind.BUILDING_RESIDENCE)

    damage(house, 11)
    damage(house, 40)

    assertEquals(18, globalOf(vandal))
  }

  @Test
  fun `a tree is not a building`() {
    val tree = building(StaticEntityKind.entries.first { !it.isBuilding })

    damage(tree, 50)

    assertEquals(20, globalOf(vandal))
  }

  @Test
  fun `a station somebody built counts as a building`() {
    val station = world.createEntity { id ->
      add(id, Health(100, 100))
      add(id, PlayerStructureIdentity(structureId = 1L, ownerAccountId = 999L))
    }

    damage(station, 20)

    assertEquals(18, globalOf(vandal))
  }

  private fun building(kind: StaticEntityKind): EntityId {
    return world.createEntity { id ->
      add(id, Health(100, 100))
      add(id, StaticVisual(kind, variant = 0, heightDm = 50))
    }
  }

  private fun damage(target: EntityId, amount: Int) {
    val taken = world.get(target, TakenDamage::class) ?: world.add(target, TakenDamage())
    taken.addDamage(vandal, amount)
    world.tick(0.1f)
  }

  private fun globalOf(entity: EntityId): Int {
    return world.get(entity, Honor::class)!!.shownValue
  }

  private companion object {
    val HERE = Vec3L(10_000, 10_000, 0)
  }
}
