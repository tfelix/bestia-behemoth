package net.bestia.zone.settlement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.prop.ecs.construction.SiteCompletion
import net.bestia.zone.settlement.ecs.CitySign
import net.bestia.zone.settlement.SettlementNoticeSMSG.Notice
import net.bestia.zone.settlement.persistence.PlayerSettlement.State
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.DisplayName
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Founds, grows, hands over and ends player towns. See the design docs' player settlements page.
 *
 * Tick thread only, like the registries it writes.
 */
@Service
class TownService(
  private val towns: TownRegistry,
  private val structures: PlayerStructureService,
  private val structureRegistry: PlayerStructureRegistry,
  private val foundingSpot: FoundingSpot,
  private val aoi: EntityAOIService,
  private val outMessageProcessor: OutMessageProcessor,
  private val events: ApplicationEventPublisher,
  private val skills: SkillRepository,
  private val worldService: WorldService,
  private val clock: Clock = Clock.systemUTC(),
) : SiteCompletion {

  override val kind = StaticEntityKind.CITY_SIGN

  /** Resolved at boot, after the skills are imported: the tick may not query the database. */
  private var founderSkillId: Long? = null

  /** The name each master chose for the town their next Founder cast plants. */
  private val plannedNames = HashMap<Long, String>()

  private val signEntityOf = HashMap<Long, EntityId>()

  /** Town id -> the master it was offered to, until they answer. */
  private val offers = HashMap<Long, Long>()

  fun resolveSkills() {
    founderSkillId = skills.findByIdentifier(SkillId.M_FOUNDER)?.id
  }

  fun plan(accountId: AccountId, masterId: Long, rawName: String) {
    val name = DisplayName.normalizeOrNull(rawName, MAX_NAME_LENGTH)?.takeIf { it.length >= MIN_NAME_LENGTH }
    val refusal = when {
      name == null -> OpError.SETTLEMENT_NAME_INVALID
      towns.isNameTaken(name) -> OpError.SETTLEMENT_NAME_TAKEN
      towns.heldBy(masterId) != null -> OpError.SETTLEMENT_ALREADY_HELD
      else -> null
    }
    if (refusal != null) {
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(refusal))
      return
    }

    plannedNames[masterId] = name!!
    outMessageProcessor.sendToPlayer(accountId, SettlementPlannedSMSG(name))
  }

  /** Why [casterId] may not found a town where it stands, or null. */
  fun foundingRefusal(world: World, casterId: EntityId): OpError? {
    val masterId = world.get(casterId, Master::class)?.masterId ?: return OpError.REQUEST_REFUSED
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return OpError.REQUEST_REFUSED
    val name = plannedNames[masterId] ?: return OpError.REQUEST_REFUSED

    return when {
      towns.heldBy(masterId) != null -> OpError.SETTLEMENT_ALREADY_HELD
      towns.isNameTaken(name) -> OpError.SETTLEMENT_NAME_TAKEN
      !foundingSpot.isFree(at) -> OpError.SETTLEMENT_TOO_CLOSE
      else -> null
    }
  }

  /** Plants the sign site where [casterId] stands, under the name it planned. */
  fun found(world: World, casterId: EntityId): Boolean {
    if (foundingRefusal(world, casterId) != null) return false

    val master = world.get(casterId, Master::class) ?: return false
    val accountId = world.get(casterId, Account::class)?.accountId ?: return false
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return false
    val name = plannedNames.remove(master.masterId) ?: return false

    val siteId = structures.beginConstruction(world, kind, master.masterId, accountId, at, 0f, SIGN_BUILD_SECONDS)
      ?: return false
    val structureId = world.get(siteId, PlayerStructureIdentity::class)?.structureId ?: return false

    val town = towns.create(structureId, name, master.masterId, accountId, master.name, at, clock.instant())
    LOG.info { "Master ${master.masterId} planted the sign site of ${town.name} (town ${town.id}) at $at" }

    return true
  }

  /** The sign is finished: it stops being a structure and becomes the town's own entity. */
  override fun complete(world: World, siteEntityId: EntityId, site: ConstructionSite) {
    val town = towns.all().firstOrNull { it.siteStructureId == site.structureId }
    val at = world.get(siteEntityId, Position::class)?.toVec3L()
    world.destroy(siteEntityId)
    structureRegistry.of(site.structureId)?.let { removeStructure(it.id, it.position) }

    if (town == null || at == null) {
      LOG.warn { "Sign site of structure ${site.structureId} finished without a town to raise" }
      return
    }

    val standing = town.copy(state = State.STANDING, sign = at, siteStructureId = null, signHp = TownGrowth.BASE_SIGN_HP)
    towns.update(standing)
    raiseSign(world, standing)
    tell(standing.founderAccountId, Notice.FOUNDED, standing.name)
  }

  /** Puts the sign of a standing town into the world. At boot, and when its site finishes. */
  fun raiseSign(world: World, town: Town) {
    val maxHp = TownGrowth.signMaxHp(town.size.points)
    val signId = world.createEntity { id ->
      add(id, Position.fromVec3(town.sign))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.CITY_SIGN.ordinal.toLong()))
      add(id, Health(current = town.signHp.coerceIn(1, maxHp), max = maxHp))
      add(id, CombatStats(CombatValues.ofStructure(TownGrowth.signDefense(town.size.points))))
      add(id, CitySign(town.id))
    }
    signEntityOf[town.id] = signId
  }

  /** Recounts every standing town's buildings and hardens or weakens its sign to match. */
  fun regrow(world: World) {
    val finished = structureRegistry.all().filterNot { it.isUnderConstruction }

    for (town in towns.all().filter { it.isStanding }) {
      val buildings = finished.map { TownGrowth.Building(it.kind, towns.metresBetween(town.sign, it.position)) }
        .filter { it.distanceMetres <= TownGrowth.MAX_RADIUS_METRES }
      val size = TownGrowth.sizeOf(buildings)
      if (size == town.size) continue

      towns.resize(town.id, size)
      signEntityOf[town.id]?.let { harden(world, it, size) }
    }
  }

  /** Damage taken stays taken: a bigger sign gains the new health, not a full heal. */
  private fun harden(world: World, signId: EntityId, size: TownGrowth.Size) {
    val health = world.get(signId, Health::class) ?: return
    val missing = health.max - health.current
    health.max = TownGrowth.signMaxHp(size.points)
    health.current = (health.max - missing).coerceAtLeast(1)

    world.get(signId, CombatStats::class)?.values = CombatValues.ofStructure(TownGrowth.signDefense(size.points))
  }

  /** Stores the sign's health now and then, so a restart keeps the damage. */
  fun recordSignHp(townId: Long, hp: Int) {
    val town = towns.of(townId) ?: return
    if (town.signHp != hp) towns.update(town.copy(signHp = hp))
  }

  /** The sign was destroyed. Its entity is already on its way out with the dead. */
  fun fall(townId: Long) {
    val town = towns.of(townId)?.takeIf { it.isStanding } ?: return
    signEntityOf.remove(townId)
    offers.remove(townId)

    towns.update(town.copy(state = State.FALLEN, signHp = 0, fellAt = clock.instant()))
    events.publishEvent(TownFellEvent(this, town.id, town.name, town.sign))
    tell(town.founderAccountId, Notice.FELL, town.name)
    LOG.info { "The sign of ${town.name} (town $townId) was destroyed" }
  }

  /** Lifts the lock on fallen towns whose time is up, and gives up sign sites that are gone or idle. */
  fun expire(world: World) {
    val now = clock.instant()

    for (town in towns.all().toList()) {
      when (town.state) {
        State.FALLEN -> if (town.fellAt == null || town.fellAt.plus(FALLEN_LOCK) < now) towns.remove(town.id)
        State.SITE -> expireSite(world, town, now)
        State.STANDING -> Unit
      }
    }
  }

  private fun expireSite(world: World, town: Town, now: Instant) {
    val structure = town.siteStructureId?.let(structureRegistry::of)
    val siteId = structure?.let { siteEntityOf(world, it.id) }
    val worked = siteId?.let { world.get(it, ConstructionSite::class)?.active } == true

    when {
      structure == null -> Unit
      worked -> {
        towns.update(town.copy(siteWorkedAt = now))
        return
      }
      town.siteWorkedAt.plus(IDLE_SITE_LIMIT) > now -> return
      else -> {
        siteId?.let(world::destroy)
        removeStructure(structure.id, structure.position)
      }
    }

    towns.remove(town.id)
    tell(town.founderAccountId, Notice.SITE_LOST, town.name)
  }

  /** Why a [kind] that only works in a town may not go up at [at]: a town takes one of each. */
  fun buildingRefusal(at: Vec3L, kind: StaticEntityKind): OpError? {
    val town = towns.standingAt(at) ?: return OpError.SETTLEMENT_NEEDS_TOWN
    val taken = structureRegistry.all().any {
      it.kind == kind && towns.metresBetween(town.sign, it.position) <= town.size.radiusMetres
    }

    return if (taken) OpError.SETTLEMENT_ALREADY_HAS_ONE else null
  }

  fun openSign(world: World, actorId: EntityId, signId: EntityId): OpError? {
    val town = townAtSign(world, actorId, signId) ?: return OpError.SETTLEMENT_OUT_OF_RANGE
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null
    val masterId = world.get(actorId, Master::class)?.masterId

    outMessageProcessor.sendToPlayer(
      accountId,
      SettlementSignSMSG(
        signEntityId = signId,
        townId = town.id,
        name = town.name,
        founderName = town.founderName,
        isFounder = masterId == town.founderMasterId,
        radiusMetres = town.size.radiusMetres.roundToInt(),
        buildingPointsTenths = (town.size.points * 10).roundToInt(),
        signDefense = TownGrowth.signDefense(town.size.points),
        canTearDown = !hasFunctionalBuilding(town),
      )
    )

    return null
  }

  fun tearDown(world: World, actorId: EntityId, signId: EntityId): OpError? {
    val town = founderTownAtSign(world, actorId, signId) ?: return OpError.SETTLEMENT_OUT_OF_RANGE
    if (hasFunctionalBuilding(town)) return OpError.SETTLEMENT_NOT_EMPTY

    world.destroy(signId)
    signEntityOf.remove(town.id)
    offers.remove(town.id)
    towns.remove(town.id)
    tell(town.founderAccountId, Notice.TORN_DOWN, town.name)
    LOG.info { "Master ${town.founderMasterId} tore down ${town.name} (town ${town.id})" }

    return null
  }

  fun offer(world: World, actorId: EntityId, signId: EntityId, masterName: String): OpError? {
    val town = founderTownAtSign(world, actorId, signId) ?: return OpError.SETTLEMENT_OUT_OF_RANGE
    val receiverId = receiverNear(world, actorId, masterName)
      ?: return OpError.SETTLEMENT_CANNOT_RECEIVE
    val receiverAccount = world.get(receiverId, Account::class)?.accountId ?: return OpError.SETTLEMENT_CANNOT_RECEIVE

    offers[town.id] = world.get(receiverId, Master::class)!!.masterId
    outMessageProcessor.sendToPlayer(receiverAccount, SettlementOfferSMSG(town.id, town.name, town.founderName))

    return null
  }

  fun answer(world: World, actorId: EntityId, townId: Long, accept: Boolean) {
    val master = world.get(actorId, Master::class) ?: return
    val accountId = world.get(actorId, Account::class)?.accountId ?: return
    if (offers[townId] != master.masterId) return
    offers.remove(townId)

    val town = towns.of(townId)?.takeIf { it.isStanding } ?: return
    if (!accept) {
      outMessageProcessor.sendToPlayer(
        town.founderAccountId,
        OperationErrorSMSG(OpError.SETTLEMENT_OFFER_DECLINED, listOf(master.name))
      )
      return
    }
    // Checked again: the receiver could have founded a town of their own since the offer.
    if (!mayReceive(world, actorId)) {
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.SETTLEMENT_ALREADY_HELD))
      return
    }

    towns.update(town.copy(founderMasterId = master.masterId, founderAccountId = accountId, founderName = master.name))
    tell(town.founderAccountId, Notice.HANDED_OVER, town.name)
    tell(accountId, Notice.RECEIVED, town.name)
    LOG.info { "${town.name} (town ${town.id}) passed from master ${town.founderMasterId} to ${master.masterId}" }
  }

  private fun receiverNear(world: World, actorId: EntityId, masterName: String): EntityId? {
    val at = world.get(actorId, Position::class)?.toVec3L() ?: return null

    return aoi.queryEntitiesInCube(at, HAND_OVER_REACH_TILES * 2, setOf(AoiLayer.DYNAMIC))
      .filter { it != actorId }
      .firstOrNull { world.get(it, Master::class)?.name.equals(masterName, ignoreCase = true) }
      ?.takeIf { mayReceive(world, it) }
  }

  private fun mayReceive(world: World, masterEntityId: EntityId): Boolean {
    val masterId = world.get(masterEntityId, Master::class)?.masterId ?: return false
    val knowsFounder = founderSkillId?.let { world.get(masterEntityId, KnownSkills::class)?.levelOf(it) }?.let { it > 0 }

    return knowsFounder == true && towns.heldBy(masterId) == null
  }

  private fun townAtSign(world: World, actorId: EntityId, signId: EntityId): Town? {
    val townId = world.get(signId, CitySign::class)?.townId ?: return null
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return null
    val signAt = world.get(signId, Position::class)?.toVec3L() ?: return null
    if (actorAt.distance(signAt) > SIGN_REACH_TILES) return null

    return towns.of(townId)?.takeIf { it.isStanding }
  }

  private fun founderTownAtSign(world: World, actorId: EntityId, signId: EntityId): Town? {
    val masterId = world.get(actorId, Master::class)?.masterId ?: return null

    return townAtSign(world, actorId, signId)?.takeIf { it.founderMasterId == masterId }
  }

  /** Sites count: a half-built post office already belongs to the town. */
  private fun hasFunctionalBuilding(town: Town): Boolean {
    return structureRegistry.all().any {
      it.kind in TownGrowth.FUNCTIONAL_KINDS && towns.metresBetween(town.sign, it.position) <= town.size.radiusMetres
    }
  }

  private fun siteEntityOf(world: World, structureId: Long): EntityId? {
    var found: EntityId? = null
    world.each(ConstructionSite::class) { id, site -> if (site.structureId == structureId) found = id }

    return found
  }

  private fun removeStructure(structureId: Long, at: Vec3L) {
    val chunkSize = worldService.config.chunkSize.toLong()
    structureRegistry.remove(structureId, Math.floorDiv(at.x, chunkSize).toInt(), Math.floorDiv(at.y, chunkSize).toInt())
  }

  private fun tell(accountId: AccountId, notice: Notice, name: String) {
    outMessageProcessor.sendToPlayer(accountId, SettlementNoticeSMSG(notice, name))
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val MIN_NAME_LENGTH = 3
    const val MAX_NAME_LENGTH = 24

    /** The docs' 10 minutes of work. */
    const val SIGN_BUILD_SECONDS = 600f

    const val SIGN_REACH_TILES = 4L
    const val HAND_OVER_REACH_TILES = 10L

    val FALLEN_LOCK: Duration = Duration.ofHours(48)
    val IDLE_SITE_LIMIT: Duration = Duration.ofHours(24)
  }
}
