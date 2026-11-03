package net.bestia.zone.battle.attack

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.Weapon
import net.bestia.zone.battle.ammo.AmmoEffect
import net.bestia.zone.battle.ammo.AmmoEffectRegistry
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.attack.AttackDelay
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.battle.ammo.AmmoType
import net.bestia.zone.item.ammo.QuiverAmmoSupply
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatSheet
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PropPromotionService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RangedAttackTest {

  private val world = testWorld()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val counting = CountingArrow()

  private val itemStats = ItemStatCatalog().apply {
    load(mapOf(ARROW.id to ItemStatSheet(attack = 3), HEAVY_ARROW.id to ItemStatSheet(attack = 40)))
  }

  private val ammoSupply = QuiverAmmoSupply(
    templates = itemTemplatesOf(ARROW, HEAVY_ARROW, FIRE_ARROW, COUNTING_ARROW),
    itemStats = itemStats,
    effects = AmmoEffectRegistry(listOf(counting)),
    inventoryService = inventoryService,
    asyncJobExecutor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    },
  )
  private val resolver = BasicAttackResolver(ammoSupply)

  /** Every roll lands and crits, so damage is deterministic. */
  private val sut = AttackExecutionService(
    BattleContextFactory(PropPromotionService(mockk(relaxed = true))),
    AttackStrategyFactory(LineOfSightService(), FixedRandom(0f)),
    messages,
    resolver,
    ammoSupply
  )

  @Test
  fun `a bow shoots as far as it reaches and spends one arrow per shot`() {
    val shooter = world.spawnArcher(quiver = listOf(ARROW.id), ARROW.id to 3)
    val target = world.spawnTarget(at = Vec3L(5, 0, 0))

    assertEquals(AttackOutcome.SWUNG, sut.attackWithHeldWeapon(world, shooter, target))

    assertTrue(world.has(target, IncomingDamage::class))
    assertEquals(2, heldArrows(shooter, ARROW.id))
    verify { inventoryService.consumeAll(MASTER_ID, listOf(ARROW.id to 1), null) }
  }

  @Test
  fun `a target beyond the bow's range costs no arrow`() {
    val shooter = world.spawnArcher(quiver = listOf(ARROW.id), ARROW.id to 3)
    val target = world.spawnTarget(at = Vec3L(6, 0, 0))

    assertEquals(AttackOutcome.OUT_OF_RANGE, sut.attackWithHeldWeapon(world, shooter, target))

    assertEquals(3, heldArrows(shooter, ARROW.id))
  }

  @Test
  fun `an empty quiver refuses the shot, tells the player and costs no delay`() {
    val shooter = world.spawnArcher(quiver = listOf(ARROW.id))
    val target = world.spawnTarget(at = Vec3L(2, 0, 0))

    assertEquals(AttackOutcome.OUT_OF_AMMO, sut.attackWithHeldWeapon(world, shooter, target))

    assertFalse(world.has(shooter, AttackDelay::class))
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.ATTACK_OUT_OF_AMMO)) }
  }

  @Test
  fun `an entry the bag has run out of is skipped for the next one`() {
    val shooter = world.spawnArcher(quiver = listOf(FIRE_ARROW.id, ARROW.id), ARROW.id to 1)

    val shot = resolver.resolve(world, shooter, BattleAttack.getBasicMeleeAttack())

    assertEquals(ARROW.id, shot?.ammunition?.itemId)
  }

  @Test
  fun `the shot takes its element from the arrow and its reach from the bow`() {
    val shooter = world.spawnArcher(quiver = listOf(FIRE_ARROW.id), FIRE_ARROW.id to 1)

    val shot = resolver.resolve(world, shooter, BattleAttack.getBasicMeleeAttack())!!

    assertEquals(Element.FIRE, shot.attackElement)
    assertEquals(BOW.range, shot.range)
    assertEquals(AttackType.RANGED_PHYSICAL, shot.attackType)
  }

  @Test
  fun `without a ranged weapon the fallback swing is used and no arrow is touched`() {
    val fighter = world.spawnArcher(quiver = listOf(ARROW.id), ARROW.id to 3, weapon = null)
    val target = world.spawnTarget(at = Vec3L(4, 0, 0))

    assertEquals(AttackOutcome.OUT_OF_RANGE, sut.attackWithHeldWeapon(world, fighter, target))

    assertEquals(3, heldArrows(fighter, ARROW.id))
  }

  @Test
  fun `the arrow's attack adds to the shot`() {
    val light = world.spawnArcher(quiver = listOf(ARROW.id), ARROW.id to 1)
    val heavy = world.spawnArcher(quiver = listOf(HEAVY_ARROW.id), HEAVY_ARROW.id to 1)
    val lightTarget = world.spawnTarget(at = Vec3L(2, 0, 0))
    val heavyTarget = world.spawnTarget(at = Vec3L(2, 0, 0))

    sut.attackWithHeldWeapon(world, light, lightTarget)
    sut.attackWithHeldWeapon(world, heavy, heavyTarget)

    // A crit: floor((2 * RATK + 7 + ammo) * 1.4), so 37 more ammo attack is 37 * 1.4 = 51.8 more, before the floor.
    val difference = world.get(heavyTarget, IncomingDamage::class)!!.total() -
        world.get(lightTarget, IncomingDamage::class)!!.total()
    assertTrue(difference in 51..52, "difference was $difference")
  }

  @Test
  fun `special ammo runs its effect before the roll and after a hit`() {
    val shooter = world.spawnArcher(quiver = listOf(COUNTING_ARROW.id), COUNTING_ARROW.id to 1)
    val target = world.spawnTarget(at = Vec3L(2, 0, 0))

    sut.attackWithHeldWeapon(world, shooter, target)

    assertEquals(1, counting.prepared)
    assertEquals(listOf(target), counting.hit)
  }

  private fun heldArrows(entityId: EntityId, itemId: Long): Int {
    return world.get(entityId, Inventory::class)!!.getItems().filter { it.itemId == itemId }.sumOf { it.amount }
  }

  private fun World.spawnArcher(
    quiver: List<Long>,
    vararg arrows: Pair<Long, Int>,
    weapon: RangedWeapon? = BOW,
  ): EntityId = createEntity { id ->
    add(id, Position.fromVec3(Vec3L(0, 0, 0)))
    val stats = BattleContextFixture.combatStats()
    add(id, CombatStats(stats.values.copy(weapon = Weapon(atk = 7, matk = 0, upgradeLevel = 0, ranged = weapon))))
    add(id, Equipment(availableSlotMask = EquipmentSlots.ALL, quiver = quiver.toMutableList()))
    add(id, Inventory(arrows.map { (itemId, amount) -> Inventory.Item(itemId, amount) }.toMutableList()))
    add(id, Master(MASTER_ID))
    add(id, Account(ACCOUNT_ID))
  }

  private fun World.spawnTarget(at: Vec3L): EntityId = createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, BattleContextFixture.combatStats())
  }

  /** Stands in for special ammo: counts its two hooks. */
  private class CountingArrow : AmmoEffect {
    var prepared = 0
    val hit = mutableListOf<EntityId>()

    override fun beforeHit(ctx: BattleContext) {
      prepared++
    }

    override fun onHit(world: World, attackerId: EntityId, targetId: EntityId, damage: Damage) {
      hit += targetId
    }
  }

  private companion object {
    const val MASTER_ID = 7L
    const val ACCOUNT_ID = 3L

    val BOW = RangedWeapon(range = 5, ammoType = AmmoType.ARROW)

    val ARROW = ammo(96L, "arrow")
    val HEAVY_ARROW = ammo(200L, "heavy_arrow")
    val FIRE_ARROW = ammo(201L, "fire_arrow", element = Element.FIRE)
    val COUNTING_ARROW = ammo(202L, "counting_arrow", script = CountingArrow::class.simpleName)

    fun ammo(id: Long, identifier: String, element: Element = Element.NORMAL, script: String? = null): Item {
      return Item(
        id = id,
        identifier = identifier,
        weight = 1,
        type = Item.ItemType.AMMO,
        script = script,
        ammoType = AmmoType.ARROW,
        element = element,
        material = ItemMaterial.WOOD
      )
    }
  }
}
