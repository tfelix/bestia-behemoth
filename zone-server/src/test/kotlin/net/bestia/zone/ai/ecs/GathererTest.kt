package net.bestia.zone.ai.ecs

import io.mockk.every
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.battle.attack.AttackOutcome
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.LootProtection
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.care.TestBagCare.BREAD
import net.bestia.zone.item.care.TestBagCare.bagWith
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia under GATHER orders working the area around its home with nobody driving it. */
class GathererTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  private val chestAt = Vec3L(-10, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun chest(): EntityId {
    return ai.spawnChest(chestAt, structureId = CHEST)
  }

  private fun bestia(job: BestiaJob = BestiaJob.GATHER): EntityId {
    val id = ai.spawnMob("passive_wanderer", home, orders = AiConfig(job))
    ai.world.add(id, OwnedBestia(masterId = 1L, playerBestiaId = id))
    return id
  }

  private fun isTaken(propId: Long): Boolean = ai.divergence.of(propId) != null

  @Test
  fun `a gatherer collects what lies in its area and earns experience for it`() {
    val gatherer = bestia()
    chest()
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)
    ai.spawnProp(StaticEntityKind.LOOSE_STONE, Vec3L(-6, 4, 0), propId = 2L)
    // The wanderer's area is 32 around home.
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(50, 0, 0), propId = 3L)

    ai.tickUntil(describe = { "it took ${listOf(1L, 2L).filter(::isTaken)}" }) { isTaken(1L) && isTaken(2L) }
    ai.tick(times = 20 * 10)

    assertNull(ai.divergence.of(3L), "it gathered outside its area")
    // A stick and a stone at item level 1: 2 * 1 + 5 each.
    assertEquals(14, ai.world.getOrThrow(gatherer, GainExp::class).value)
  }

  @Test
  fun `a gatherer whose bag is nearly full of its own food gathers no more`() {
    val gatherer = bestia()
    chest()
    ai.world.add(gatherer, Inventory(mutableListOf(Inventory.Item(itemId = BREAD, amount = 8, weight = 1))))
    ai.world.add(gatherer, CarryCapacity(8, 10))
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)

    ai.tick(times = 20 * 20)

    assertNull(ai.divergence.of(1L))
  }

  @Test
  fun `a bestia told to rest leaves the sticks alone`() {
    bestia(BestiaJob.REST)
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)

    ai.tick(times = 20 * 20)

    assertNull(ai.divergence.of(1L))
  }

  @Test
  fun `a gatherer fells a tree and picks up what it drops`() {
    val gatherer = bestia()
    chest()
    ai.world.add(gatherer, Account(ACCOUNT))
    val treeAt = Vec3L(4, 0, 0)
    val tree = ai.spawnProp(StaticEntityKind.TREE, treeAt, propId = 1L)
    var swings = 0
    every { ai.attackExecution.attack(any(), gatherer, tree, any()) } answers {
      swings++
      AttackOutcome.SWUNG
    }

    ai.tickUntil(describe = { "it swung $swings times" }) { swings >= 3 }
    // The tree falls, as the combat and death systems would have it, and drops its logs for the feller's master.
    ai.world.destroy(tree)
    ai.aoi.removeEntityPosition(tree)
    val logs = ai.world.createEntity { id ->
      ai.world.add(id, Position.fromVec3(treeAt))
      ai.world.add(id, GroundItemStack(itemId = 43, amount = 4))
      ai.world.add(id, LootProtection(ACCOUNT, remainingSeconds = 6f))
    }
    ai.aoi.setEntityPosition(logs, treeAt)

    ai.tickUntil(describe = { "it never reached for the logs" }) {
      ai.world.get(gatherer, ObtainItemIntent.LootItemIntent::class)?.sourceEntityItemStackId == logs
    }
  }

  @Test
  fun `a gatherer gives up on a tree it may not fell and takes the stick instead`() {
    val gatherer = bestia()
    chest()
    val tree = ai.spawnProp(StaticEntityKind.TREE, Vec3L(3, 0, 0), propId = 1L)
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(14, 0, 0), propId = 2L)
    every { ai.attackExecution.attack(any(), gatherer, tree, any()) } returns AttackOutcome.REFUSED

    ai.tickUntil(describe = { "it never got past the tree" }) { isTaken(2L) }
  }

  @Test
  fun `a gatherer with a nearly full bag carries it to its chest and keeps its food`() {
    val gatherer = bestia()
    val chest = chest()
    ai.world.add(gatherer, Inventory(mutableListOf(Inventory.Item(LOG, 8, weight = 1), Inventory.Item(BREAD, 2))))
    ai.world.add(gatherer, CarryCapacity(8, 10))
    // Something left to gather, so it is the load that sends it.
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)

    ai.tickUntil(describe = { "the chest holds ${itemsIn(chest)}" }) { itemsIn(chest)[LOG] == 8 }

    assertEquals(mapOf(BREAD to 2), itemsIn(gatherer))
  }

  @Test
  fun `a gatherer hauls what it has once nothing is left to gather`() {
    val gatherer = bestia()
    val chest = chest()
    ai.world.add(gatherer, bagWith(LOG to 1))

    ai.tickUntil(describe = { "the chest holds ${itemsIn(chest)}" }) { itemsIn(chest)[LOG] == 1 }
  }

  @Test
  fun `a gatherer whose chest is full stops gathering`() {
    bestia()
    val chest = chest()
    repeat(ChestStorage.SLOTS) { ai.world.getOrThrow(chest, Inventory::class).addItem(Inventory.Item(1000L + it, 1)) }
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)

    ai.tick(times = 20 * 20)

    assertNull(ai.divergence.of(1L))
  }

  @Test
  fun `a gatherer without a chest gathers nothing`() {
    bestia()
    ai.spawnProp(StaticEntityKind.STICK, Vec3L(5, 0, 0), propId = 1L)

    ai.tick(times = 20 * 20)

    assertNull(ai.divergence.of(1L))
  }

  private fun itemsIn(id: EntityId): Map<Long, Int> {
    return ai.world.getOrThrow(id, Inventory::class).getItems().associate { it.itemId to it.amount }
  }

  private companion object {
    const val ACCOUNT = 77L
    const val CHEST = 500L
    const val LOG = 43L
  }
}
