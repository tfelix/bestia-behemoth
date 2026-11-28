package net.bestia.zone.faction.banishment

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.Faction
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.casting.GearOperations
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.faction.FactionConfig
import net.bestia.zone.faction.FactionService
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.EquipmentChanges
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaTimeConfig
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CitadelBanishmentTest {

  private var now = Instant.parse("2026-11-28T12:00:00Z")
  private val clock = mockk<Clock> { every { instant() } answers { now } }

  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val jobs = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val templates = itemTemplatesOf(SIGIL)
  private val gear = GearOperations(EquipmentChanges(templates), inventoryService, jobs, templates)

  private val statusEffects = StatusEffectService(
    StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() },
    StatusEffectScriptRegistry(listOf(Unsworn())),
  )
  private val mark = UnswornMark(statusEffects, clock)
  private val factions = FactionService(FactionConfig(), clock)
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val rite = CitadelBanishmentScript(factions, gear, mark, BestiaTimeConfig(speedFactor = 3.0), out)

  private val world = testWorld(systems = listOf(UnswornSystem(mark)))

  @Test
  fun `the rite frees a sworn master, destroys its sigil and leaves it unsworn for half its level in Bestia days`() {
    val master = master(sworn = true, wearsSigil = true)

    assertTrue(rite.execute(world, master, ScriptArgs.EMPTY))

    assertNull(factions.factionOf(world, master))
    // 37 Bestia days at three Bestia hours per real hour.
    assertEquals(now.plus(Duration.ofHours(37 * 8)), world.get(master, FactionMembership::class)?.unswornUntil)
    assertNull(world.get(master, Equipment::class)!!.get(EquipmentSlot.ACCESSORY_1))
    assertNull(world.get(master, Inventory::class)!!.byPlayerItemId(SIGIL_INSTANCE))
    verify { inventoryService.destroyWorn(MASTER_ID, null, EquipmentSlot.ACCESSORY_1) }
    assertTrue(isUnsworn(master))
  }

  @Test
  fun `an unsworn master keeps the scroll`() {
    val master = master(sworn = false, wearsSigil = true)

    assertFalse(rite.execute(world, master, ScriptArgs.EMPTY))

    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.FACTION_NOT_SWORN)) }
  }

  @Test
  fun `a sigil in the bag is not enough, it must be worn`() {
    val master = master(sworn = true, wearsSigil = false)

    assertFalse(rite.execute(world, master, ScriptArgs.EMPTY))

    assertEquals(Faction.ETERNITY, factions.factionOf(world, master))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.FACTION_SIGIL_NOT_WORN)) }
  }

  @Test
  fun `the mark comes back after a login and goes once the time is over`() {
    val master = master(sworn = false, wearsSigil = false)
    world.add(master, FactionMembership(unswornUntil = now.plus(Duration.ofHours(2))))

    world.tick(10f)
    assertTrue(isUnsworn(master))

    now = now.plus(Duration.ofHours(3))
    world.get(master, StatusEffects::class)!!.removeEffect(StatusEffectId.UNSWORN.id)
    world.tick(10f)
    assertFalse(isUnsworn(master))
  }

  private fun master(sworn: Boolean, wearsSigil: Boolean): EntityId {
    val sigil = Inventory.Item(itemId = SIGIL.id, amount = 1, uniqueId = SIGIL_INSTANCE, stackable = false, equipped = wearsSigil)
    val equipment = Equipment(EquipmentSlots.ALL)
    if (wearsSigil) {
      equipment.equip(EquipmentSlot.ACCESSORY_1, Equipment.EquippedItem(itemId = SIGIL.id, uniqueId = SIGIL_INSTANCE))
    }

    return world.createEntity { id ->
      add(id, Account(accountId = ACCOUNT_ID))
      add(id, Master(masterId = MASTER_ID))
      add(id, Level(74))
      add(id, StatusEffects())
      add(id, Inventory(mutableListOf(sigil)))
      add(id, equipment)
      add(id, if (sworn) FactionMembership(Faction.ETERNITY, swornAt = now) else FactionMembership())
    }
  }

  private fun isUnsworn(master: EntityId): Boolean {
    return world.get(master, StatusEffects::class)!!.hasEffect(StatusEffectId.UNSWORN.id)
  }

  private companion object {
    const val ACCOUNT_ID = 7L
    const val MASTER_ID = 42L
    const val SIGIL_INSTANCE = 9L
    val SIGIL = Item(
      id = CitadelBanishmentScript.SIGIL_ITEM_ID, identifier = "sigil_of_redemption", weight = 100,
      type = Item.ItemType.EQUIP, equipSlot = EquipmentSlot.ACCESSORY_1, material = ItemMaterial.METAL
    )
  }
}
