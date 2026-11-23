package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.battle.ecs.attack.AttackTarget
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.net.AttackEntityCMSG
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.control.net.InteractEntityCMSG
import net.bestia.zone.control.net.SelectEntityCMSG
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildInfoSMSG
import net.bestia.zone.guild.GuildNoticeSMSG
import net.bestia.zone.guild.GuildService
import net.bestia.zone.guild.net.CreateGuildCMSG
import net.bestia.zone.guild.persistence.GuildRepository
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.item.StorageOpenedSMSG
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.EmpyriteInteraction
import net.bestia.zone.stronghold.EmpyritePlantOfferSMSG
import net.bestia.zone.stronghold.EmpyriteSeedDiscoveries
import net.bestia.zone.stronghold.EmpyriteSeedInteraction
import net.bestia.zone.stronghold.EmpyriteSeedRegistry
import net.bestia.zone.stronghold.Empyrites
import net.bestia.zone.stronghold.SeedSpots
import net.bestia.zone.stronghold.StrongholdRegistry
import net.bestia.zone.stronghold.StrongholdSMSG
import net.bestia.zone.stronghold.Stewards
import net.bestia.zone.stronghold.StrongholdTrack
import net.bestia.zone.stronghold.WatcherKind
import net.bestia.zone.stronghold.Watchers
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.ecs.EmpyriteSeedSprout
import net.bestia.zone.stronghold.ecs.Steward
import net.bestia.zone.stronghold.ecs.Watcher
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A Stronghold's life from the wire, in order: player1's guild discovers a seed, plants an Empyrite and builds it,
 * invests, hires a steward, opens the guild storage and calls a watcher; player2's guild then breaks the Empyrite and
 * takes the Stronghold, which its grace protects for a while.
 *
 * What would only cost waiting is set up directly: the guilds' skills, the coins, where the masters stand, the
 * half-hour build and the Empyrite's health before the breaking blow. Everything a player does goes over the wire.
 */
class StrongholdScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var connections: ConnectionInfoService

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var guildService: GuildService

  @Autowired
  private lateinit var guildRepository: GuildRepository

  @Autowired
  private lateinit var commodities: CommodityItems

  @Autowired
  private lateinit var seeds: EmpyriteSeedRegistry

  @Autowired
  private lateinit var spots: SeedSpots

  @Autowired
  private lateinit var discoveries: EmpyriteSeedDiscoveries

  @Autowired
  private lateinit var strongholds: StrongholdRegistry

  @Autowired
  private lateinit var empyrites: Empyrites

  @Autowired
  private lateinit var stewards: Stewards

  @Autowired
  private lateinit var watchers: Watchers

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val tx: TransactionTemplate get() = TransactionTemplate(transactionManager)

  private val seed: EmpyriteSeed by lazy { seeds.awake().first() }
  private val leader by lazy { clientPlayer1 }
  private val rival by lazy { clientPlayer2 }

  /** Where each master stood before this scenario moved it. Other scenarios expect the players side by side. */
  private val homes = mutableMapOf<GameClientMock, Vec3L>()

  @AfterAll
  fun letTheStrongholdGo() {
    // The Spring context is shared with every other scenario: nothing of this one may stay behind.
    world.read {
      if (strongholds.on(seed.rank) != null) empyrites.broken(this, seed.rank, breakerGuildId = null)
      stewards.dismiss(this, seed.rank)
      watchers.dismissAll(this, seed.rank)
    }
    homes.forEach { (client, home) -> standAt(client, home) }
    listOf(leader to LEADER_GUILD, rival to RIVAL_GUILD).forEach { (client, name) ->
      try {
        guildService.disband(client.connectedPlayerId, name)
      } catch (_: GuildException) {
        // Never founded, or already gone.
      }
    }
  }

  @Test
  @Order(1)
  fun `two guilds with Official Guild Approval`() {
    takeControlOfMaster(leader)
    takeControlOfMaster(rival)
    found(leader, LEADER_GUILD)
    found(rival, RIVAL_GUILD)

    learn(leader, SkillId.OFFICIAL_GUILD_APPROVAL, SkillId.STEWARD_CONTRACT, SkillId.GUILD_STORAGE, SkillId.WATCHER_RESEARCH)
    learn(rival, SkillId.OFFICIAL_GUILD_APPROVAL)
  }

  @Test
  @Order(2)
  fun `walking onto a seed's core discovers it, and clicking its sprout offers the planting`() {
    // The offer already asks whether the member carries the price.
    giveCoins(leader, 20_000)
    standAt(leader, spots.standingAt(seed))

    await { assertTrue(world.read { discoveries.isDiscovered(masterIdOf(leader), seed.rank) }) }

    leader.sendMessage(InteractEntityCMSG(leader.connectedPlayerId, sprout(), ScriptArgs.EMPTY))
    await { assertEquals(seed.name, leader.getLastReceived(EmpyritePlantOfferSMSG::class).seedName) }
  }

  @Test
  @Order(3)
  fun `planting pays and raises the guild's site, and finishing it raises the Empyrite`() {
    leader.sendMessage(InteractEntityCMSG(leader.connectedPlayerId, sprout(), ScriptArgs.of(ScriptArgKeys.ACTION to EmpyriteSeedInteraction.PLANT)))

    await { assertEquals(State.SITE, world.read { strongholds.on(seed.rank)?.state }) }
    await { assertTrue(leader.receivedAny(GuildNoticeSMSG::class) { it.notice == GuildNoticeSMSG.Notice.EMPYRITE_PLANTED }) }

    await { assertNotNull(siteEntity()) }
    val site = siteEntity()!!
    world.read { get(site, ConstructionSite::class)!!.let { it.countdown(it.remainingSeconds - 1f) } }
    leader.sendMessage(InteractEntityCMSG(leader.connectedPlayerId, site, ScriptArgs.EMPTY))

    await { assertEquals(State.STANDING, world.read { strongholds.on(seed.rank)?.state }) }
    assertNotNull(empyrite())
  }

  @Test
  @Order(4)
  fun `a member invests in Defense at the Empyrite and hires the steward`() {
    giveCoins(leader, 250_000)

    leader.sendMessage(
      InteractEntityCMSG(
        leader.connectedPlayerId, empyrite(),
        ScriptArgs.of(ScriptArgKeys.ACTION to EmpyriteInteraction.INVEST, ScriptArgKeys.TRACK to StrongholdTrack.DEFENSE.name)
      )
    )
    await { assertEquals(1, world.read { strongholds.on(seed.rank)!!.progressOf(StrongholdTrack.DEFENSE).steps }) }

    leader.sendMessage(
      InteractEntityCMSG(leader.connectedPlayerId, empyrite(), ScriptArgs.of(ScriptArgKeys.ACTION to EmpyriteInteraction.HIRE_STEWARD))
    )
    await { assertTrue(leader.getLastReceived(StrongholdSMSG::class).steward.isHired) }
    assertNotNull(stewardEntity())
  }

  @Test
  @Order(5)
  fun `the steward opens the guild storage, and the leader calls a watcher`() {
    leader.sendMessage(InteractEntityCMSG(leader.connectedPlayerId, assertNotNull(stewardEntity()), ScriptArgs.EMPTY))
    await { assertEquals(StorageOpenedSMSG.Kind.GUILD_STORAGE, leader.getLastReceived(StorageOpenedSMSG::class).kind) }

    leader.sendMessage(
      InteractEntityCMSG(
        leader.connectedPlayerId, empyrite(),
        ScriptArgs.of(ScriptArgKeys.ACTION to EmpyriteInteraction.ADD_WATCHER, ScriptArgKeys.WATCHER to WatcherKind.MELEE.name)
      )
    )
    await {
      val watcher = assertNotNull(watcherEntity())
      assertEquals(GuildOwned(guildIdOf(leader)), world.read { get(watcher, GuildOwned::class) })
    }
  }

  @Test
  @Order(6)
  fun `the rival's breaking blow takes the Stronghold, and both guilds hear of it`() {
    // The Guardian roams the grounds and would kill the lone rival first. It falls, as it would to a raid party.
    world.modify(assertNotNull(watcherEntity())) { id -> add(id, Dead()) }
    await { assertTrue(watchers.fallenOf(seed.rank).isNotEmpty()) }

    val target = empyrite()
    standAt(rival, positionOf(target).let { Vec3L(it.x + 1, it.y, it.z) })
    awaitWardFaded(rival)

    // Again and again: the Empyrite regenerates, and a regen tick may come between the cracking and the blow.
    await {
      world.read { get(target, Health::class)?.current = 1 }
      rival.sendMessage(AttackEntityCMSG(rival.connectedPlayerId, target))
      assertEquals(guildIdOf(rival), world.read { strongholds.on(seed.rank)?.guildId }, blowState(target))
    }
    await { assertTrue(rival.receivedAny(GuildNoticeSMSG::class) { it.notice == GuildNoticeSMSG.Notice.STRONGHOLD_CAPTURED }) }
    await { assertTrue(leader.receivedAny(GuildNoticeSMSG::class) { it.notice == GuildNoticeSMSG.Notice.STRONGHOLD_LOST }) }
    assertEquals(null, stewardEntity(), "the steward left with the old guild's contract")
    assertTrue(watchers.fallenOf(seed.rank).isEmpty(), "the fallen Guardian does not come back for the new owner")
  }

  @Test
  @Order(7)
  fun `the captured Empyrite tells its old owner to wait out the grace`() {
    standAt(leader, positionOf(empyrite()).let { Vec3L(it.x - 1, it.y, it.z) })

    leader.sendMessage(AttackEntityCMSG(leader.connectedPlayerId, empyrite()))

    await { assertEquals(OperationErrorSMSG(OpError.COMBAT_TARGET_IN_GRACE), leader.getLastReceived(OperationErrorSMSG::class)) }
  }

  /** An earlier scenario may have left a player steering a bestia; here the masters act themselves. */
  private fun takeControlOfMaster(client: GameClientMock) {
    client.sendMessage(SelectEntityCMSG(client.connectedPlayerId, masterEntityOf(client)))
    await { assertEquals(masterEntityOf(client), connections.getActiveEntityId(client.connectedPlayerId)) }
  }

  private fun found(client: GameClientMock, name: String) {
    inventoryService.addItem(masterIdOf(client), "founders_seal", 1)
    client.sendMessage(CreateGuildCMSG(client.connectedPlayerId, name))
    await { assertEquals(name, client.getLastReceived(GuildInfoSMSG::class).name) }
  }

  /** Into the guild's row and onto its leader's membership, as raising them point by point would. */
  private fun learn(client: GameClientMock, vararg skills: SkillId) {
    val guildId = guildIdOf(client)
    tx.executeWithoutResult {
      val guild = guildRepository.findById(guildId).orElseThrow()
      skills.forEach { guild.skillLevels[it.name] = 1 }
      guildRepository.save(guild)
    }
    world.modify(masterEntityOf(client)) { id ->
      val membership = get(id, GuildMembership::class)!!
      add(id, membership.copy(skillLevels = membership.skillLevels + skills.associate { it.name to 1 }))
    }
  }

  private fun giveCoins(client: GameClientMock, amount: Int) {
    val coin = commodities.coinItemId()!!
    val before = coinsOf(client)
    world.modify(masterEntityOf(client)) { id -> add(id, ObtainItemIntent.CreateItemIntent(coin, amount)) }
    await { assertTrue(coinsOf(client) >= before + amount) }
  }

  private fun coinsOf(client: GameClientMock): Int {
    val coin = commodities.coinItemId()!!
    return world.read { get(masterEntityOf(client), Inventory::class)!!.getItems().filter { it.itemId == coin }.sumOf { it.amount } }
  }

  private fun standAt(client: GameClientMock, at: Vec3L) {
    homes.getOrPut(client) { positionOf(masterEntityOf(client)) }
    world.modify(masterEntityOf(client)) { id -> get(id, Position::class)!!.apply { x = at.x; y = at.y; z = at.z } }
  }

  private fun blowState(target: EntityId): String = world.read {
    val attacker = masterEntityOf(rival)
    "rival steering ${connections.getActiveEntityId(rival.connectedPlayerId)} from master $attacker " +
      "at ${get(attacker, Position::class)?.toVec3L()}, ordered at ${get(attacker, AttackTarget::class)?.targetEntityId}, " +
      "Empyrite $target at ${get(target, Position::class)?.toVec3L()} with ${get(target, Health::class)?.current} HP, " +
      "rival's last refusal ${rival.tryGetLastReceived(OperationErrorSMSG::class)?.code}, " +
      "rival dead ${has(attacker, Dead::class)}, " +
      "rival's effects ${get(attacker, StatusEffects::class)?.activeEffects?.map { "${it.definitionId} locks ${it.locks}" }}"
  }

  /** Fresh from the start town, a master still carries its ward for a few seconds and may harm no player's side. */
  private fun awaitWardFaded(client: GameClientMock) {
    await {
      val effects = world.read { get(masterEntityOf(client), StatusEffects::class) }
      assertFalse(effects?.hasEffect(StatusEffectId.WARDED.id) == true)
    }
  }

  private fun positionOf(entityId: EntityId): Vec3L = world.read { get(entityId, Position::class)!!.toVec3L() }

  private fun masterEntityOf(client: GameClientMock): EntityId = connections.getSelectedMasterEntityId(client.connectedPlayerId)

  private fun masterIdOf(client: GameClientMock): Long = connections.getMasterId(client.connectedPlayerId)

  private fun guildIdOf(client: GameClientMock): Long = world.read { get(masterEntityOf(client), GuildMembership::class)!!.guildId }

  private fun sprout(): EntityId = assertNotNull(firstWith(EmpyriteSeedSprout::class) { it.rank == seed.rank }, "the seed shows no sprout")

  private fun siteEntity(): EntityId? = firstWith(ConstructionSite::class) { it.kind.name == "EMPYRITE_SITE" }

  private fun empyrite(): EntityId = assertNotNull(firstWith(Empyrite::class) { it.rank == seed.rank }, "no Empyrite stands")

  private fun stewardEntity(): EntityId? = firstWith(Steward::class) { it.rank == seed.rank }

  private fun watcherEntity(): EntityId? = firstWith(Watcher::class) { it.rank == seed.rank }

  private fun <T : net.bestia.zone.ecs.core.Component> firstWith(type: kotlin.reflect.KClass<T>, test: (T) -> Boolean): EntityId? {
    return world.read {
      var found: EntityId? = null
      query(type).each { id -> if (found == null && isAlive(id) && test(get(id, type)!!)) found = id }
      found
    }
  }

  private companion object {
    const val LEADER_GUILD = "Goldwacht"
    const val RIVAL_GUILD = "Eisenfaust"
  }
}
