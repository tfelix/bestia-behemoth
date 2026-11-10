package net.bestia.zone.casting

import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.battle.Revival
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.battle.Knockback
import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.skill.PartyMates
import net.bestia.zone.battle.damage.Blocked
import net.bestia.zone.battle.damage.weighedTo
import net.bestia.zone.battle.damage.DamageLanding
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.Sides
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.casting.detect.Detecting
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG
import net.bestia.zone.skill.GuildMates
import net.bestia.zone.world.stream.ChunkStreamInbox
import net.bestia.zone.util.EntityId
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.spoor.TrackReading
import net.bestia.zone.battle.ecs.damage.IncomingDamage
/**
 * The real [SkillWorld]: every operation is charged against the cast's [SkillBudget]. A cast resolves on the
 * tick thread (see [SkillExecutionService]), so it uses the [World] directly.
 *
 * One instance per cast, because the budget is.
 */
class BudgetedSkillWorld(
  private val world: World,
  private val budget: SkillBudget,
  private val services: SkillWorldServices,
  private val casterId: EntityId,
  private val skillId: Long,
  private val skillLevel: Int,
  /** How the skill's blows are weighed when a script names no kind of its own; see [HitKind.ofSkill]. */
  private val skillHitKind: HitKind = HitKind.UNTYPED,
  /** The cast's Potency, which every status it leaves carries. */
  private val castPotency: Int = 0,
) : SkillWorld {

  override val remainingOps: Int get() = budget.remainingOps

  override fun isAlive(entityId: EntityId): Boolean {
    budget.charge()

    return world.isAlive(entityId)
  }

  override fun positionOf(entityId: EntityId): Vec3L? {
    budget.charge()

    return world.get(entityId, Position::class)?.toVec3L()
  }

  override fun masterIdOf(entityId: EntityId): Long? {
    budget.charge()

    return world.get(entityId, Master::class)?.masterId
  }

  override fun accountIdOf(entityId: EntityId): Long? {
    budget.charge()

    return world.get(entityId, Account::class)?.accountId
  }

  override fun guildMembershipOf(entityId: EntityId): GuildMembership? {
    budget.charge()

    return world.get(entityId, GuildMembership::class)
  }

  override fun entitiesInCube(centre: Vec3L, edge: Long, layers: Set<AoiLayer>): Set<EntityId> {
    budget.charge()

    val found = services.aoi.queryEntitiesInCube(centre, edge, layers)
    budget.chargeQueryResults(found.size)

    return found
  }

  override fun stationNear(around: Vec3L, kind: StaticEntityKind): EntityId? {
    budget.charge()

    return services.structures.stationNear(world, around, kind)
  }

  override fun spawnAreaEffect(centre: Vec3L, visualId: Long, effect: AreaEffect): EntityId {
    budget.charge(SPAWN_OPS)

    return services.areaEffectSpawner.spawn(world, centre, visualId, effect)
  }

  override fun say(text: String) {
    budget.charge()

    services.speech.say(world, casterId, text)
  }

  override fun dropItem(itemId: Long, amount: Int, at: Vec3L) {
    budget.charge(SPAWN_OPS)

    val ownerAccountId = world.get(casterId, Account::class)?.accountId
    services.lootItems.spawnLootItem(world, itemId = itemId, amount = amount, pos = at, lootOwner = ownerAccountId)
  }

  override fun igniteGroundFire(centre: Vec3L, radiusTiles: Long): Boolean {
    budget.charge(SPAWN_OPS)

    return services.groundFire.ignite(centre, radiusTiles, casterId, skillId, skillLevel) != null
  }

  /**
   * A heal moves [Health] directly; damage is staged as a [IncomingDamage] so `ReceivedDamageSystem` drains
   * it, which is also what handles death, threat and interrupting the victim's own cast.
   *
   * Two casts landing on the same target share one component rather than one replacing the other: a cast
   * resolves between ticks, so no system is iterating and `World.add` applies immediately.
   */
  override fun apply(targetEntityId: EntityId, damage: Damage, kind: HitKind?) {
    budget.charge()

    if (damage is Miss || damage is Blocked) {
      broadcastDamage(targetEntityId, damage)
      return
    }

    val hitKind = kind ?: skillHitKind
    val (receiver, dealer) = if (damage is Heal) targetEntityId to casterId else reflected(targetEntityId)
    val landing = damage.weighedTo(DamageLanding.weigh(world, dealer, receiver, hitKind, damage.amount))

    // `true` only from inside the scope, so it distinguishes "the entity is gone" from "the entity is here
    // but has no Health to heal" - a `when` returning Unit? would conflate the two.
    val landed = onEntity(receiver) { target ->
      when (landing) {
        is Heal -> Healing.heal(this, target, landing.amount)

        else -> {
          // This branch only: a miss and a heal stay true of a target the gate shields.
          if (DamageGate.verdict(this, dealer, target) != DamageGate.Verdict.ADMITTED) {
            return@onEntity false
          }

          val staged = get(target, IncomingDamage::class) ?: add(target, IncomingDamage())
          staged.add(landing.amount, dealer, hitKind)
        }
      }

      true
    } == true

    // False means the blow never landed: the target died between the snapshot and now, which off-thread
    // resolution makes possible, or it cannot be hurt at all.
    if (landed) {
      broadcastDamage(receiver, landing)
    }
  }

  override fun forceMove(entityId: EntityId, towards: Vec3L, maxTiles: Int, keepDistance: Int): Int {
    budget.charge()

    return services.forcedMovement.towards(world, entityId, towards, maxTiles, keepDistance)
  }

  override fun inflict(
    targetEntityId: EntityId,
    status: String,
    chancePercent: Int,
    durationSeconds: Double?,
    level: Int,
    amount: Int,
  ): Boolean {
    budget.charge()

    val effect = StatusEffectId.valueOf(status)

    return onEntity(targetEntityId) { target ->
      services.statusEffects.tryInflict(
        this, target, effect, level, casterId, chancePercent, durationSeconds, amount, castPotency
      )
    } == true
  }

  override fun interrupt(targetEntityId: EntityId) {
    budget.charge()

    services.castCancel.interrupt(world, targetEntityId)
  }

  override fun isFoe(entityId: EntityId): Boolean {
    budget.charge()

    return entityId != casterId && world.isAlive(entityId) && world.has(entityId, Health::class) &&
        !world.has(entityId, Dead::class) &&
        !Sides.areAllied(world, casterId, entityId) &&
        DamageGate.verdict(world, casterId, entityId) == DamageGate.Verdict.ADMITTED
  }

  override fun natureOf(entityId: EntityId): Nature? {
    budget.charge()

    return world.get(entityId, Nature::class)
  }

  override fun carriedKilogramsOf(entityId: EntityId): Int {
    budget.charge()

    return (world.get(entityId, Inventory::class)?.totalWeight ?: 0) / WEIGHT_UNITS_PER_KILOGRAM
  }

  override fun restoreMana(entityId: EntityId, amount: Int) {
    budget.charge()

    world.get(entityId, Mana::class)?.let { it.current += amount }
  }

  /**
   * Bounces a blow off whoever wears a reflect, at most [MAX_REFLECTIONS] times, so two mirrors facing each other
   * still let it land. Returns who takes it and whom it counts as coming from.
   */
  private fun reflected(targetEntityId: EntityId): Pair<EntityId, EntityId> {
    var receiver = targetEntityId
    var dealer = casterId

    repeat(MAX_REFLECTIONS) {
      if (receiver == dealer || !spendReflection(receiver)) return receiver to dealer
      receiver = dealer.also { dealer = receiver }
    }

    return receiver to dealer
  }

  private fun spendReflection(id: EntityId): Boolean {
    val reflect = world.get(id, StatusEffects::class)?.activeEffects
      ?.firstOrNull { it.definitionId == StatusEffectId.REFLECT.id && it.count > 0 }
      ?: return false

    reflect.count -= 1
    if (reflect.count <= 0) {
      services.statusEffects.removeEffect(world, id, StatusEffectId.REFLECT.id, RemovalReason.CANCELLED)
    }

    return true
  }

  override fun applyStatusEffect(
    targetEntityId: EntityId,
    effectId: Long,
    level: Int,
    amount: Int,
    durationSeconds: Double?,
  ) {
    budget.charge()

    onEntity(targetEntityId) { target ->
      if (services.statusEffects.isHeldStronger(this, target, effectId, castPotency)) {
        tellCaster(OpError.SKILL_WEAKER_THAN_ACTIVE)
        return@onEntity
      }
      services.statusEffects.applyEffect(this, target, effectId, level, casterId, durationSeconds, amount, castPotency)
    }
  }

  override fun effectSourceOf(entityId: EntityId, effectId: Long): EntityId? {
    budget.charge()

    return world.get(entityId, StatusEffects::class)?.activeEffects?.firstOrNull { it.definitionId == effectId }?.sourceEntityId
  }

  override fun hasEffect(entityId: EntityId, effectId: Long): Boolean {
    budget.charge()

    return world.get(entityId, StatusEffects::class)?.hasEffect(effectId) ?: false
  }

  override fun removeEffect(entityId: EntityId, effectId: Long) {
    budget.charge()

    services.statusEffects.removeEffect(world, entityId, effectId, RemovalReason.DISPELLED)
  }

  override fun dispel(entityId: EntityId): Int {
    budget.charge()

    return services.statusEffects.dispel(world, entityId)
  }

  override fun dispelGroundItem(entityId: EntityId, baseChancePercent: Int): Int {
    budget.charge()

    return services.groundItemDispel.dispel(world, entityId, baseChancePercent).size
  }

  override fun manaFractionOf(entityId: EntityId): Float {
    budget.charge()

    val mana = world.get(entityId, Mana::class) ?: return 0f

    return if (mana.max <= 0) 0f else mana.current.toFloat() / mana.max
  }

  override fun spendAllCasterMana(): Int {
    budget.charge()

    val mana = world.get(casterId, Mana::class) ?: return 0
    val spent = mana.current
    mana.current = 0

    return spent
  }

  override fun strip(entityId: EntityId, slot: EquipmentSlot): Boolean {
    budget.charge()

    return services.gear.strip(world, entityId, slot)
  }

  override fun wearGear(entityId: EntityId, percent: Int): Boolean {
    budget.charge()

    return services.gear.wear(world, entityId, percent)
  }

  override fun detect(seconds: Float) {
    budget.charge()

    val detecting = world.get(casterId, Detecting::class)
    if (detecting != null) {
      detecting.remainingSeconds = seconds
      detecting.potency = castPotency
    } else {
      world.add(casterId, Detecting(seconds, castPotency))
    }
  }

  override fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean {
    budget.charge()

    return onEntity(targetEntityId) { target ->
      val present = get(target, StatusEffects::class)
        ?.activeEffects
        ?.any { it.definitionId == effectId } == true

      if (present) {
        return@onEntity false
      }

      services.statusEffects.applyEffect(this, target, effectId, level, casterId)
    } ?: false
  }

  override fun applyStatusEffectToGuildNear(
    guildId: Long,
    centre: Vec3L,
    radiusMetres: Long,
    effectId: Long,
    level: Int,
  ) {
    budget.charge()

    for (member in GuildMates.near(world, guildId, centre, radiusMetres)) {
      services.statusEffects.applyEffect(world, member, effectId, level, casterId)
    }
  }

  override fun restoreGuildNear(guildId: Long, centre: Vec3L, radiusMetres: Long, percent: Int) {
    budget.charge()

    for (member in GuildMates.near(world, guildId, centre, radiusMetres)) {
      if (world.has(member, Dead::class)) {
        continue
      }
      world.get(member, Health::class)?.let { Healing.heal(world, member, it.max * percent / 100) }
      world.get(member, Mana::class)?.let { it.current += it.max * percent / 100 }
    }
  }

  override fun summonGuild(guildId: Long, to: Vec3L, includeCaster: Boolean) {
    budget.charge()

    val casterAccount = world.get(casterId, Account::class)?.accountId
    val summoned = GuildMates.playedBy(world, guildId)
      .filter { (accountId, played) -> !world.has(played, Dead::class) && (includeCaster || accountId != casterAccount) }
      .keys

    summoned.forEachIndexed { i, accountId ->
      val spot = SUMMON_SPOTS[i % SUMMON_SPOTS.size]
      services.chunkStream.offerTeleport(ChunkStreamInbox.Teleport(accountId, to.x + spot.first, to.y + spot.second))
    }
  }

  override fun tellGuild(guildId: Long, message: SMSG) {
    budget.charge()

    for (accountId in GuildMates.playedBy(world, guildId).keys) {
      services.messages.sendToPlayer(accountId, message)
    }
  }

  override fun strike(targetEntityId: EntityId, attack: BattleAttack, powerPercent: Int): Damage? {
    budget.charge()

    val strikes = services.strikes ?: return null
    val damage = strikes.strike(world, casterId, targetEntityId, attack, powerPercent) ?: return null
    val reach = if (attack.attackType == AttackType.RANGED_PHYSICAL) HitReach.RANGED else HitReach.MELEE
    apply(targetEntityId, damage, HitKind.physical(reach, attack.attackElement))

    return damage
  }

  override fun drawAmmo(): Ammunition? {
    budget.charge()

    return services.strikes?.drawAmmo(world, casterId)
  }

  override fun isBehind(targetEntityId: EntityId): Boolean {
    budget.charge()

    val facing = world.get(targetEntityId, Facing::class) ?: return false
    val targetAt = world.get(targetEntityId, Position::class)?.toVec3L() ?: return false
    val casterAt = world.get(casterId, Position::class)?.toVec3L() ?: return false

    return facing.isBehind(targetAt, casterAt)
  }

  override fun callOwnBestias(): Int {
    budget.charge()

    val masterId = world.get(casterId, Master::class)?.masterId ?: return 0
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return 0
    val answering = OwnedBestia.ownedBy(world, masterId)
      .map { it.entityId }
      .filter { !world.has(it, InCombat::class) && !world.has(it, Dead::class) }
    budget.chargeQueryResults(answering.size)

    return answering.withIndex().count { (index, bestiaId) ->
      val (dx, dy) = CALL_OFFSETS[index % CALL_OFFSETS.size]
      services.forcedMovement.blinkTo(world, bestiaId, Vec3L(at.x + dx, at.y + dy, at.z))
    }
  }

  override fun replant(at: Vec3L, growthPercent: Int, bonusYield: Int): Boolean {
    budget.charge()

    return services.planting?.plant(world, at, growthPercent, bonusYield) == true
  }

  override fun pitchCamp(at: Vec3L, level: Int, durationSeconds: Long): Boolean {
    budget.charge(SPAWN_OPS)

    return services.camps?.pitch(world, casterId, skillId, at, level, durationSeconds) == true
  }

  override fun isKnockbackImmune(entityId: EntityId): Boolean {
    budget.charge()

    return Knockback.isImmune(world, entityId)
  }

  override fun giveItem(itemId: Long, amount: Int) {
    budget.charge()

    onEntity(casterId) { caster ->
      val pending = get(caster, ObtainItemIntent.CreateItemsIntent::class)?.grants.orEmpty()
      add(caster, ObtainItemIntent.CreateItemsIntent(pending + ObtainItemIntent.CreateItemIntent(itemId, amount)))
    }
  }

  override fun revive(entityId: EntityId, healthPercent: Int): Boolean {
    budget.charge()

    return onEntity(entityId) { id -> Revival.revive(this, id, healthPercent) } == true
  }

  override fun blink(entityId: EntityId, to: Vec3L): Boolean {
    budget.charge()

    return onEntity(entityId) { id -> services.forcedMovement.blinkTo(this, id, to) } == true
  }

  override fun partyNear(radiusMetres: Long): List<EntityId> {
    budget.charge()

    val centre = world.get(casterId, Position::class)?.toVec3L() ?: return listOf(casterId)
    val reached = PartyMates.near(world, casterId, centre, radiusMetres)
    budget.chargeQueryResults(reached.size)

    return reached
  }

  override fun payCasterCost(mana: Int, stamina: Int): Boolean {
    if (mana <= 0 && stamina <= 0) {
      return true
    }

    budget.charge()

    return onEntity(casterId) { caster ->
      val manaPool = get(caster, Mana::class)
      val staminaPool = get(caster, Stamina::class)
      val canPayMana = mana <= 0 || manaPool == null || manaPool.current >= mana
      val canPayStamina = stamina <= 0 || staminaPool == null || staminaPool.current >= stamina
      if (!canPayMana || !canPayStamina) {
        return@onEntity false
      }

      if (mana > 0 && manaPool != null) {
        manaPool.current -= mana
      }
      if (stamina > 0 && staminaPool != null) {
        staminaPool.current -= stamina
      }
      true
    } ?: false
  }

  /** One op, though the service reads several components. */
  override fun offerRecipes(skillId: Long) {
    budget.charge()

    services.crafting.offerRecipes(world, casterId, skillId)
  }

  override fun readTracks(centre: Vec3L, radiusTiles: Long): TrackReading? {
    budget.charge()

    return services.spoor.read(centre, radiusTiles)
  }

  /** [block] against [id], or null if [id] is gone. */
  private inline fun <T> onEntity(id: EntityId, block: World.(EntityId) -> T): T? {
    return if (world.isAlive(id)) world.block(id) else null
  }

  private fun tellCaster(error: OpError) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    services.messages.sendToPlayer(accountId, OperationErrorSMSG(error))
  }

  private fun broadcastDamage(targetEntityId: EntityId, damage: Damage) {
    services.messages.sendToObserversOf(
      world,
      targetEntityId,
      DamageEntitySMSG(
        entityId = targetEntityId,
        sourceEntityId = casterId,
        attackId = skillId.toInt(),
        div = damage.hits,
        damage = damage.amount,
        skillLevel = skillLevel,
        type = DamageEntitySMSG.DamageType.of(damage)
      )
    )
  }

  private companion object {
    /** Where called bestias stand: in a ring around their master, one tile out. */
    val CALL_OFFSETS = listOf(1L to 0L, -1L to 0L, 0L to 1L, 0L to -1L, 1L to 1L, -1L to -1L, 1L to -1L, -1L to 1L)

    /** Off the target, then off the caster's own reflect; after that the blow lands whatever. */
    const val MAX_REFLECTIONS = 2

    /** Item weights are authored in tenths of a kilogram, as in Ragnarok Online. */
    const val WEIGHT_UNITS_PER_KILOGRAM = 10

    /**
     * Creating an entity is a structural change plus several component adds, so it costs more than a read -
     * not because it holds the tick much longer, but so a script that spawns in a loop runs out of budget an
     * order of magnitude sooner than one that only looks around.
     */
    const val SPAWN_OPS = 8

    /** Rings of tiles around a summoning point, nearest first, so a summoned guild does not stand in one spot. */
    val SUMMON_SPOTS: List<Pair<Long, Long>> = (1L..4L).flatMap { ring ->
      (-ring..ring).flatMap { dx -> (-ring..ring).map { dy -> dx to dy } }
        .filter { (dx, dy) -> maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) == ring }
    }
  }
}
