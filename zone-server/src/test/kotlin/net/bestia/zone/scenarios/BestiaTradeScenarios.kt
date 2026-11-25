package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.BestiaOrigin
import net.bestia.zone.bestia.Sex
import net.bestia.zone.breeding.BreedingFarmService
import net.bestia.zone.breeding.Hatching
import net.bestia.zone.breeding.persistence.BestiaEgg
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.BestiaTag
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.LootProtection
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.master.bestia.PlayerBestiaLoss
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.trade.TradeRequestSMSG
import net.bestia.zone.trade.TradeStateSMSG
import net.bestia.zone.trade.net.AnswerTradeRequestCMSG
import net.bestia.zone.trade.net.CancelTradeCMSG
import net.bestia.zone.trade.net.ConfirmTradeCMSG
import net.bestia.zone.trade.net.OfferTradeItemCMSG
import net.bestia.zone.trade.net.RequestTradeCMSG
import net.bestia.zone.trade.net.RetractTradeItemCMSG
import net.bestia.zone.trade.net.SetTradeLockCMSG
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Trading party bestias, end to end: the offer, the carried-items warning, the exchange, a full party, and an egg
 * keeping its genetics on the way.
 *
 * The scenario suite shares one Spring context and a live tick loop, and is known to be flaky on a clean tree -
 * baseline it before reading a failure here as this feature's fault.
 */
class BestiaTradeScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var playerBestias: PlayerBestiaRepository

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var eggs: BestiaEggRepository

  @Autowired
  private lateinit var hatching: Hatching

  @Autowired
  private lateinit var catalogue: BestiaCatalogue

  @Autowired
  private lateinit var loss: PlayerBestiaLoss

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val player1 get() = clientPlayer1.connectedPlayerId
  private val player2 get() = clientPlayer2.connectedPlayerId
  private val master1 get() = connectionInfoService.getMasterId(player1)
  private val master2 get() = connectionInfoService.getMasterId(player2)

  /** Every bestia this class made, given up again at the end so the shared masters keep their fixture bestias only. */
  private val created = mutableListOf<Long>()

  /** The bestia traded in the second test, then compared in the later ones. */
  private var traded: PlayerBestia? = null

  @AfterAll
  fun leaveNothingBehind() {
    for (playerBestiaId in created) {
      val row = playerBestias.findByIdOrNull(playerBestiaId) ?: continue
      if (row.isStored) {
        loss.loseStored(playerBestiaId)
      } else {
        row.entityId?.let { entityId -> world.read { loss.lose(this, entityId) } }
      }
    }
    bundlesOnGround().forEach { bundle -> world.modify(bundle) { id -> destroy(id) } }
  }

  @Test
  @Order(1)
  fun `a bestia carrying items is only offered once its owner agrees they go too, and shows grades only`() {
    val bestia = create(master1)
    carryApples(master1, bestia, 2)
    val tradeId = openTrade()

    clientPlayer1.sendMessage(OfferTradeItemCMSG(player1, tradeId, bestiaEntityId = bestia.entityId!!))

    val refusal = awaitValue {
      assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)).also { assertEquals(OpError.TRADE_BESTIA_CARRIES_ITEMS, it.code) }
    }
    assertEquals(listOf("1"), refusal.args, "one pile of apples")

    clientPlayer1.sendMessage(
      OfferTradeItemCMSG(player1, tradeId, bestiaEntityId = bestia.entityId!!, acceptCarriedItems = true)
    )

    val theirView = awaitValue {
      val state = assertNotNull(clientPlayer2.tryGetLastReceived(TradeStateSMSG::class))
      assertEquals(1, state.partnerBestias.size)
      state
    }
    val offered = theirView.partnerBestias.single().toProto()
    assertEquals(bestia.id, offered.playerBestiaId)
    assertEquals(1, offered.carriedItemCount)
    assertEquals(9, offered.ivs.gradesCount, "every IV as a grade")
    assertEquals(0, offered.ivs.exactCount, "never the exact numbers, whoever reads it")
    assertEquals(BestiaOrigin.CAUGHT.toBnet(), offered.origin)

    // Taking it back leaves it where it was: it never left its owner.
    clientPlayer1.sendMessage(RetractTradeItemCMSG(player1, tradeId, playerBestiaId = bestia.id))
    await {
      val state = assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class))
      assertTrue(state.ownBestias.isEmpty())
    }
    assertTrue(world.read { isAlive(bestia.entityId!!) })

    endTrade(clientPlayer1, tradeId)
  }

  @Test
  @Order(2)
  fun `a confirmed trade hands the bestia and what it carries to the partner, who starts with fresh orders`() {
    val bestia = create(master1)
    carryApples(master1, bestia, 3)
    setOrders(bestia)
    val tradeId = openTrade()

    clientPlayer1.sendMessage(
      OfferTradeItemCMSG(player1, tradeId, bestiaEntityId = bestia.entityId!!, acceptCarriedItems = true)
    )
    await { assertEquals(1, assertNotNull(clientPlayer2.tryGetLastReceived(TradeStateSMSG::class)).partnerBestias.size) }

    lockAndConfirm(tradeId)

    await {
      val row = assertNotNull(playerBestias.findByIdOrNull(bestia.id))
      assertEquals(master2, row.master.id)
      assertFalse(row.isStored)
    }
    val row = playerBestias.findByIdOrNull(bestia.id)!!
    assertEquals(BestiaOrigin.CAUGHT, row.origin)
    assertEquals(masterName(master1), row.originMasterName, "caught by its first owner, whoever owns it now")
    assertEquals(AiConfig().job, row.aiConfig.job, "its old owner's orders are gone")
    assertEquals(3, applesIn(row.id), "what it carried came along")

    await {
      val live = connectionInfoService.getOwnedEntitiesByMaster(player2, master2).single { it.playerBestiaId == bestia.id }
      assertEquals(master2, world.read { get(live.entityId, OwnedBestia::class)?.masterId })
      assertEquals(masterName(master2), world.read { get(live.entityId, BestiaTag::class)?.ownerName })
    }
    assertTrue(connectionInfoService.getOwnedEntitiesByMaster(player1, master1).none { it.playerBestiaId == bestia.id })

    traded = row
  }

  @Test
  @Order(3)
  fun `a bestia traded into a full party goes into storage and drops what it carried at its new owner's feet`() {
    // Fill the partner's party; the bestia that no longer fits went into storage already.
    while (!create(master2).isStored) Unit
    val bestia = create(master1)
    carryApples(master1, bestia, 2)
    val tradeId = openTrade()

    clientPlayer1.sendMessage(
      OfferTradeItemCMSG(player1, tradeId, bestiaEntityId = bestia.entityId!!, acceptCarriedItems = true)
    )
    await { assertEquals(1, assertNotNull(clientPlayer2.tryGetLastReceived(TradeStateSMSG::class)).partnerBestias.size) }

    lockAndConfirm(tradeId)

    await {
      val row = assertNotNull(playerBestias.findByIdOrNull(bestia.id))
      assertEquals(master2, row.master.id)
      assertTrue(row.isStored, "the party was full")
    }
    assertEquals(0, applesIn(bestia.id), "a stored bestia carries nothing")
    await {
      val bundle = bundlesOnGround().single()
      assertEquals(player2, world.read { get(bundle, LootProtection::class)?.ownerAccountId })
    }
    assertFalse(world.read { isAlive(bestia.entityId!!) }, "a stored bestia is not in the world")
  }

  @Test
  @Order(4)
  fun `an egg traded away keeps its genetics and hatches bred by whoever laid it`() {
    val egg = layEgg(master1)
    val tradeId = 9_000_001L

    val reserved = assertNotNull(
      inventoryService.reserveForTrade(master1, tradeId, egg.itemId, egg.instanceId, 1, null)
    )
    inventoryService.settleTrade(tradeId, master1, master2, setOf(reserved.offerSlotId), emptySet())

    val row = assertNotNull(eggs.findByIdOrNull(egg.instanceId), "the egg's row went along with its instance")
    assertEquals(egg.ivs, row.individualValues)
    assertEquals(masterName(master1), row.breederName)

    // Into an incubator, which detaches the egg from the bag as IncubatorService does, and out of it hatched.
    assertNotNull(inventoryService.removeOneFromMaster(master2, egg.itemId, 1, egg.instanceId))
    val hatched = transaction { hatching.hatch(master2, egg.instanceId, Vec3L.ZERO) }
    created += hatched.id
    assertEquals(master2, hatched.master.id)
    assertEquals(egg.ivs, hatched.individualValues)
    assertEquals(Sex.FEMALE, hatched.sex)
    assertEquals(BestiaOrigin.BRED, hatched.origin)
    assertEquals(masterName(master1), hatched.originMasterName, "bred by the egg's breeder, not its hatcher")
  }

  private class LaidEgg(val instanceId: Long, val itemId: Long, val ivs: IndividualValues)

  /** An egg in [masterId]'s bag, as a farm of theirs would have laid it. */
  private fun layEgg(masterId: Long): LaidEgg {
    val template = itemRepository.findByIdentifierOrThrow(BreedingFarmService.BESTIA_EGG)
    val instance = inventoryService.mintInstanceForMaster(masterId, template)
    val ivs = IndividualValues(strength = 77, vitality = 12, hp = 91)
    eggs.save(
      BestiaEgg(
        itemInstanceId = instance.id,
        speciesId = catalogue.byIdentifier("blob").id,
        sex = Sex.FEMALE,
        individualValues = ivs,
        encodedAttackLearnLevels = "",
        hatchMillis = 1_000,
        breederName = masterName(masterId),
      )
    )

    return LaidEgg(instance.id, template.id, ivs)
  }

  private fun create(masterId: Long): PlayerBestia {
    return playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))
      .also { created += it.id }
  }

  /** Into the bag and its live copy both, as a bestia that picked them up has them. */
  private fun carryApples(masterId: Long, bestia: PlayerBestia, amount: Int) {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    inventoryService.grantToOwner(masterId, bestia.id, apple, amount)
    world.modify(bestia.entityId!!) { id ->
      getOrThrow(id, Inventory::class).addItem(Inventory.Item(itemId = apple, amount = amount))
    }
  }

  private fun applesIn(playerBestiaId: Long): Int = transaction {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    playerBestias.findByIdOrNull(playerBestiaId)!!.container.slots
      .filter { it.template.id == apple }
      .sumOf { it.amount }
  }

  private fun setOrders(bestia: PlayerBestia) {
    transaction {
      val row = playerBestias.findByIdOrNull(bestia.id)!!
      row.aiConfig = AiConfig(job = BestiaJob.GATHER)
      playerBestias.save(row)
    }
  }

  private fun masterName(masterId: Long): String = masterRepository.findByIdOrThrow(masterId).name

  private fun <T> transaction(block: () -> T): T = TransactionTemplate(transactionManager).execute { block() }!!

  private fun openTrade(): Long {
    clientPlayer1.sendMessage(RequestTradeCMSG(player1, masterEntityOf(player2)))

    val request = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(TradeRequestSMSG::class)) }
    clientPlayer2.sendMessage(AnswerTradeRequestCMSG(player2, request.tradeId, accept = true))
    await { assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class)) }

    return request.tradeId
  }

  private fun lockAndConfirm(tradeId: Long) {
    clientPlayer1.sendMessage(SetTradeLockCMSG(player1, tradeId, locked = true))
    clientPlayer2.sendMessage(SetTradeLockCMSG(player2, tradeId, locked = true))
    await { assertEquals(TradeStateSMSG.Status.LOCKED, assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class)).status) }

    clientPlayer1.sendMessage(ConfirmTradeCMSG(player1, tradeId))
    await { assertTrue(assertNotNull(clientPlayer2.tryGetLastReceived(TradeStateSMSG::class)).partnerConfirmed) }
    clientPlayer2.sendMessage(ConfirmTradeCMSG(player2, tradeId))

    await {
      assertEquals(TradeStateSMSG.Status.COMPLETED, assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class)).status)
    }
    assertFalse(clientPlayer1.receivedAny(OperationErrorSMSG::class) { it.code == OpError.TRADE_FAILED })
  }

  private fun endTrade(client: GameClientMock, tradeId: Long) {
    client.sendMessage(CancelTradeCMSG(client.connectedPlayerId, tradeId))

    await {
      assertEquals(TradeStateSMSG.Status.CANCELLED, assertNotNull(client.tryGetLastReceived(TradeStateSMSG::class)).status)
    }
  }

  private fun masterEntityOf(accountId: Long): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun bundlesOnGround(): List<EntityId> {
    return world.read {
      val found = mutableListOf<EntityId>()
      query(GroundItemStack::class).each { id ->
        if (get<GroundItemStack>().itemId == ItemBundles.ITEM_ID) found.add(id)
      }
      found
    }
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }
}
