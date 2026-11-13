package net.bestia.zone.casting

import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.SlotLock
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.spoor.TrackReading

/**
 * A [SkillWorld] that records what a script did to it instead of doing it.
 *
 * Script tests assert on *effects* rather than on a return value, because that is where a script's behaviour
 * now lives - `Ember` returns nothing at all, and what makes it Ember is the patch it spawns. Answers to
 * reads are set up as fields so a test can put the caster somewhere and make it a master.
 */
class RecordingSkillWorld(
  private val positions: MutableMap<EntityId, Vec3L> = mutableMapOf(),
  private val masterIds: MutableMap<EntityId, Long> = mutableMapOf(),
  private val accountIds: MutableMap<EntityId, Long> = mutableMapOf(),
  private val stations: MutableMap<StaticEntityKind, EntityId> = mutableMapOf(),
  private val entitiesNearby: Set<EntityId> = emptySet(),

  /** What [payCasterCost] answers - false models a caster who cannot pay. */
  var manaAvailable: Boolean = true,

  private val memberships: MutableMap<EntityId, GuildMembership> = mutableMapOf(),
) : SkillWorld {

  val spawnedAreaEffects = mutableListOf<SpawnedAreaEffect>()
  val appliedDamage = mutableListOf<Pair<EntityId, Damage>>()
  val appliedEffects = mutableListOf<AppliedEffect>()
  val recipeOffers = mutableListOf<Long>()
  val manaSpent = mutableListOf<Int>()
  val staminaSpent = mutableListOf<Int>()
  val guildEffects = mutableListOf<GuildEffect>()
  val guildRestorations = mutableListOf<GuildRestoration>()
  val guildMessages = mutableListOf<Pair<Long, SMSG>>()
  val inflicted = mutableListOf<Inflicted>()
  val interrupted = mutableListOf<EntityId>()
  val forcedMoves = mutableListOf<ForcedMove>()
  val natures = mutableMapOf<EntityId, Nature>()
  val foes = mutableSetOf<EntityId>()
  val manaRestored = mutableListOf<Pair<EntityId, Int>>()
  val effectsOn = mutableMapOf<EntityId, MutableSet<Long>>()
  val removedEffects = mutableListOf<Pair<EntityId, Long>>()
  val dispelled = mutableListOf<EntityId>()
  val dispelledGroundItems = mutableListOf<Pair<EntityId, Int>>()
  val stripped = mutableListOf<Pair<EntityId, EquipmentSlot>>()
  val gearWorn = mutableListOf<Pair<EntityId, Int>>()
  val detections = mutableListOf<Float>()
  var manaFraction: Float = 1f
  var casterMana: Int = 0
  var strippable: Boolean = true

  override val remainingOps: Int = Int.MAX_VALUE

  fun putPosition(entityId: EntityId, at: Vec3L) = apply { positions[entityId] = at }

  fun putMaster(entityId: EntityId, masterId: Long, accountId: Long? = null) = apply {
    masterIds[entityId] = masterId
    accountId?.let { accountIds[entityId] = it }
  }

  fun putStation(kind: StaticEntityKind, entityId: EntityId) = apply { stations[kind] = entityId }

  fun putMembership(entityId: EntityId, membership: GuildMembership) = apply { memberships[entityId] = membership }

  override fun guildMembershipOf(entityId: EntityId): GuildMembership? = memberships[entityId]

  override fun applyStatusEffectToGuildNear(guildId: Long, centre: Vec3L, radiusMetres: Long, effectId: Long, level: Int) {
    guildEffects += GuildEffect(guildId, centre, radiusMetres, effectId, level)
  }

  override fun restoreGuildNear(guildId: Long, centre: Vec3L, radiusMetres: Long, percent: Int) {
    guildRestorations += GuildRestoration(guildId, centre, radiusMetres, percent)
  }

  val summons = mutableListOf<Summon>()

  override fun forceMove(entityId: EntityId, towards: Vec3L, maxTiles: Int, keepDistance: Int): Int {
    forcedMoves.add(ForcedMove(entityId, towards, maxTiles, keepDistance))
    return maxTiles
  }

  override fun inflict(
    targetEntityId: EntityId,
    status: String,
    chancePercent: Int,
    durationSeconds: Double?,
    level: Int,
    amount: Int,
  ): Boolean {
    inflicted.add(Inflicted(targetEntityId, status, chancePercent, durationSeconds, level, amount))
    return true
  }

  override fun interrupt(targetEntityId: EntityId) {
    interrupted.add(targetEntityId)
  }

  override fun isFoe(entityId: EntityId): Boolean {
    return entityId in foes
  }

  override fun natureOf(entityId: EntityId): Nature? {
    return natures[entityId]
  }

  override fun carriedKilogramsOf(entityId: EntityId): Int {
    return 0
  }

  override fun restoreMana(entityId: EntityId, amount: Int) {
    manaRestored.add(entityId to amount)
  }

  /** Each [restoreCaster] as its stamina and health percent. */
  val casterRestorations = mutableListOf<Pair<Int, Int>>()

  override fun restoreCaster(staminaPercent: Int, healthPercent: Int) {
    casterRestorations.add(staminaPercent to healthPercent)
  }

  val manaDrained = mutableListOf<Pair<EntityId, Int>>()

  override fun drainMana(entityId: EntityId, percent: Int) {
    manaDrained.add(entityId to percent)
  }

  override fun effectSourceOf(entityId: EntityId, effectId: Long): EntityId? {
    return if (appliedEffects.any { it.targetEntityId == entityId && it.effectId == effectId }) effectSource else null
  }

  /** Who [effectSourceOf] says gave an effect that was applied. */
  var effectSource: EntityId? = null

  override fun hasEffect(entityId: EntityId, effectId: Long): Boolean {
    return effectsOn[entityId]?.contains(effectId) == true
  }

  override fun removeEffect(entityId: EntityId, effectId: Long) {
    removedEffects.add(entityId to effectId)
    effectsOn[entityId]?.remove(effectId)
  }

  override fun dispel(entityId: EntityId): Int {
    dispelled.add(entityId)
    return 0
  }

  override fun dispelGroundItem(entityId: EntityId, baseChancePercent: Int): Int {
    dispelledGroundItems.add(entityId to baseChancePercent)
    return 0
  }

  override fun manaFractionOf(entityId: EntityId): Float {
    return manaFraction
  }

  override fun spendAllCasterMana(): Int {
    return casterMana.also { casterMana = 0 }
  }

  /** Whether a Triple Blow just landed, until a Chain Combo spends it. */
  var tripleBlowOpen: Boolean = false

  override fun followsTripleBlow(): Boolean {
    return tripleBlowOpen
  }

  override fun spendTripleBlow(): Boolean {
    return tripleBlowOpen.also { tripleBlowOpen = false }
  }

  override fun strip(entityId: EntityId, slot: EquipmentSlot): Boolean {
    if (strippable) stripped.add(entityId to slot)
    return strippable
  }

  /** What [strippableGearOf] answers, by target. */
  val wornGear = mutableMapOf<EntityId, List<SlotLock>>()
  val dualWielders = mutableSetOf<EntityId>()

  override fun strippableGearOf(entityId: EntityId): List<SlotLock> = wornGear[entityId].orEmpty()

  override fun isDualWielding(entityId: EntityId): Boolean = entityId in dualWielders

  /** What [heldWeaponTypeOf] answers, by entity. */
  val heldWeapons = mutableMapOf<EntityId, WeaponType>()

  override fun heldWeaponTypeOf(entityId: EntityId): WeaponType? = heldWeapons[entityId]

  /** Whom [isPartyOf] counts as the caster's party. */
  val partyMembers = mutableSetOf<EntityId>()

  override fun isPartyOf(entityId: EntityId): Boolean = entityId in partyMembers

  /** Who [holdsShield] says holds a shield. */
  val shieldBearers = mutableSetOf<EntityId>()

  override fun holdsShield(entityId: EntityId): Boolean = entityId in shieldBearers

  /** What [swingStatusChanceOf] answers for a stun, by entity. */
  val gearStunChances = mutableMapOf<EntityId, Int>()

  override fun swingStatusChanceOf(entityId: EntityId, effect: StatusEffectId): Int {
    return if (effect == StatusEffectId.STUN) gearStunChances[entityId] ?: 0 else 0
  }

  override fun wearGear(entityId: EntityId, percent: Int): Boolean {
    gearWorn.add(entityId to percent)
    return true
  }

  override fun detect(seconds: Float) {
    detections.add(seconds)
  }

  val markedPrey = mutableListOf<Pair<EntityId, Int>>()

  override fun markPrey(targetEntityId: EntityId, level: Int) {
    markedPrey.add(targetEntityId to level)
  }

  /** What [throwHeldWeapon] hands out, once each. */
  val throwable = ArrayDeque<Ammunition>()

  override fun throwHeldWeapon(): Ammunition? {
    return throwable.removeFirstOrNull()
  }

  /** The radius of each [revealHiddenNear], and how many it answers it found. */
  val reveals = mutableListOf<Long>()
  var hiddenFound: Int = 0

  override fun revealHiddenNear(radiusTiles: Long): Int {
    reveals += radiusTiles
    return hiddenFound
  }

  data class Inflicted(
    val targetEntityId: EntityId,
    val status: String,
    val chancePercent: Int,
    val durationSeconds: Double?,
    val level: Int,
    val amount: Int = 0,
  )

  data class KnockBack(val entityId: EntityId, val from: Vec3L, val tiles: Int)

  data class ForcedMove(val entityId: EntityId, val towards: Vec3L, val maxTiles: Int, val keepDistance: Int)

  data class Summon(val guildId: Long, val to: Vec3L, val includeCaster: Boolean)

  override fun summonGuild(guildId: Long, to: Vec3L, includeCaster: Boolean) {
    summons += Summon(guildId, to, includeCaster)
  }

  override fun tellGuild(guildId: Long, message: SMSG) {
    guildMessages += guildId to message
  }

  override fun isAlive(entityId: EntityId): Boolean = true

  /** What [isFallen] answers yes for. */
  val fallen = mutableSetOf<EntityId>()

  override fun isFallen(entityId: EntityId): Boolean = entityId in fallen

  override fun positionOf(entityId: EntityId): Vec3L? = positions[entityId]

  override fun masterIdOf(entityId: EntityId): Long? = masterIds[entityId]

  override fun accountIdOf(entityId: EntityId): Long? = accountIds[entityId]

  override fun entitiesInCube(centre: Vec3L, edge: Long, layers: Set<AoiLayer>): Set<EntityId> = entitiesNearby

  override fun stationNear(around: Vec3L, kind: StaticEntityKind): EntityId? = stations[kind]

  override fun spawnAreaEffect(centre: Vec3L, visualId: Long, effect: AreaEffect): EntityId {
    spawnedAreaEffects += SpawnedAreaEffect(centre, visualId, effect)

    return spawnedAreaEffects.size.toLong()
  }

  /** What a script asked to set light to, so `Ember`'s two separate effects can be told apart in a test. */
  val ignitions = mutableListOf<Ignition>()

  data class Ignition(val centre: Vec3L, val radiusTiles: Long)

  /** True, because a fake ground has no reason to refuse. A test wanting refusal overrides this. */
  override fun igniteGroundFire(centre: Vec3L, radiusTiles: Long): Boolean {
    ignitions += Ignition(centre, radiusTiles)

    return true
  }

  val spoken = mutableListOf<String>()

  override fun say(text: String) {
    spoken += text
  }

  val droppedItems = mutableListOf<DroppedItem>()

  data class DroppedItem(val itemId: Long, val amount: Int, val at: Vec3L)

  override fun dropItem(itemId: Long, amount: Int, at: Vec3L) {
    droppedItems += DroppedItem(itemId, amount, at)
  }

  override fun apply(targetEntityId: EntityId, damage: Damage, kind: HitKind?) {
    appliedDamage += targetEntityId to damage
    appliedKinds += kind
  }

  /** The kind each [appliedDamage] was landed as, null for the skill's own. */
  val appliedKinds = mutableListOf<HitKind?>()

  override fun applyStatusEffect(
    targetEntityId: EntityId,
    effectId: Long,
    level: Int,
    amount: Int,
    durationSeconds: Double?,
  ) {
    appliedEffects += AppliedEffect(targetEntityId, effectId, level, amount, durationSeconds)
  }

  /** Models the real atomic claim: the first caller wins the effect and every later one is refused. */
  override fun applyStatusEffectIfAbsent(targetEntityId: EntityId, effectId: Long, level: Int): Boolean {
    if (appliedEffects.any { it.targetEntityId == targetEntityId && it.effectId == effectId }) {
      return false
    }

    applyStatusEffect(targetEntityId, effectId, level)

    return true
  }

  /** What [strike] answers for every target; null means no fight resolves. */
  var strikeResult: Damage? = null
  val strikes = mutableListOf<Pair<EntityId, Int>>()

  /** The indices into [strikes] of the blows struck with the second weapon. */
  val offHandStrikes = mutableListOf<Int>()

  /** The attack each of [strikes] was struck with. */
  val strikeAttacks = mutableListOf<BattleAttack>()

  override fun strike(targetEntityId: EntityId, attack: BattleAttack, powerPercent: Int, offHand: Boolean): Damage? {
    strikeAttacks += attack
    if (offHand) offHandStrikes += strikes.size
    strikes += targetEntityId to powerPercent
    return strikeResult
  }

  /** What [drawAmmo] answers. */
  var ammo: Ammunition? = null

  override fun drawAmmo(): Ammunition? = ammo

  /** Targets the caster stands behind. */
  val behind = mutableSetOf<EntityId>()

  override fun isBehind(targetEntityId: EntityId): Boolean = targetEntityId in behind

  val spotsBehind = mutableMapOf<EntityId, Vec3L>()

  override fun spotBehind(targetEntityId: EntityId): Vec3L? = spotsBehind[targetEntityId]

  val replanted = mutableListOf<Triple<Vec3L, Int, Int>>()

  override fun replant(at: Vec3L, growthPercent: Int, bonusYield: Int): Boolean {
    replanted += Triple(at, growthPercent, bonusYield)
    return true
  }

  val camps = mutableListOf<Triple<Vec3L, Int, Long>>()

  /** Range and accuracy of each [senseResources]. */
  val resourceSenses = mutableListOf<Pair<Double, Double>>()

  override fun senseResources(rangeMetres: Double, accuracy: Double) {
    resourceSenses += rangeMetres to accuracy
  }

  /** Target and base chance of each [dispelAll]. */
  val fullDispels = mutableListOf<Pair<EntityId, Int>>()

  override fun dispelAll(entityId: EntityId, baseChancePercent: Int): Int {
    fullDispels += entityId to baseChancePercent
    return 0
  }

  /** Whether each [teleportCaster] went home. */
  val teleports = mutableListOf<Boolean>()

  override fun teleportCaster(toHome: Boolean) {
    teleports += toHome
  }

  /** What [warpMarkOf] answers, by index. */
  var warpMarks: List<Vec3L> = emptyList()

  override fun warpMarkOf(entityId: EntityId, index: Int): Vec3L? = warpMarks.getOrNull(index)

  /** Where each [openPortal] opened, to where, for how many passages and seconds. */
  val portals = mutableListOf<OpenedPortal>()

  override fun openPortal(at: Vec3L, to: Vec3L?, passages: Int, seconds: Int) {
    portals += OpenedPortal(at, to, passages, seconds)
  }

  data class OpenedPortal(val at: Vec3L, val to: Vec3L?, val passages: Int, val seconds: Int)

  /** What [takeSpellScroll] answers, by scroll id; a taken scroll is gone. */
  val spellScrolls = mutableMapOf<Long, InscribedSpell>()

  /** What [bestiaAttack] answers, by skill id. */
  val bestiaAttacks = mutableMapOf<Long, InscribedSpell>()

  /** What both refusal checks answer. */
  var spellRefusal: OpError? = null

  val taughtSpells = mutableListOf<Pair<EntityId, InscribedSpell>>()
  val inscribedSpells = mutableListOf<InscribedSpell>()
  val brokenMinds = mutableListOf<EntityId>()
  val toldCaster = mutableListOf<OpError>()

  override fun spellTrainingRefusal(bestiaId: EntityId, scrollId: Long): OpError? = spellRefusal

  override fun spellInscriptionRefusal(bestiaId: EntityId, skillId: Long): OpError? = spellRefusal

  override fun takeSpellScroll(scrollId: Long): InscribedSpell? = spellScrolls.remove(scrollId)

  override fun bestiaAttack(bestiaId: EntityId, skillId: Long): InscribedSpell? = bestiaAttacks[skillId]

  override fun teachSpell(bestiaId: EntityId, spell: InscribedSpell) {
    taughtSpells += bestiaId to spell
  }

  override fun inscribeSpell(spell: InscribedSpell) {
    inscribedSpells += spell
  }

  /** Every [bindSpell] call: the args and where. */
  val boundSpells = mutableListOf<Pair<List<Long>, Vec3L>>()

  /** What [dispelBinding] answers: no binding anywhere unless a test says so. */
  var bindingDispel: Boolean? = null

  override fun dispelBinding(entityId: EntityId, baseChancePercent: Int): Boolean? = bindingDispel

  override fun bindSpell(args: List<Long>, at: Vec3L): OpError? {
    boundSpells += args to at

    return null
  }

  override fun breakMind(bestiaId: EntityId) {
    brokenMinds += bestiaId
  }

  override fun tellCaster(error: OpError) {
    toldCaster += error
  }

  /** The level of each [senseMagic]. */
  val magicSenses = mutableListOf<Int>()

  override fun senseMagic(level: Int) {
    magicSenses += level
  }

  /** Who each [senseMonster] read. */
  val monsterSenses = mutableListOf<EntityId>()

  override fun senseMonster(targetId: EntityId): Boolean {
    monsterSenses += targetId
    return true
  }

  /** Listing places and seconds of each [pitchStall]. */
  val stalls = mutableListOf<Pair<Int, Int>>()

  override fun pitchStall(maxListings: Int, seconds: Int): OpError? {
    stalls += maxListings to seconds
    return null
  }

  /** Radius and depth reveal of each [soundForHollows]. */
  val soundings = mutableListOf<Pair<Int, Boolean>>()

  override fun soundForHollows(radiusMetres: Int, revealsDepth: Boolean) {
    soundings += radiusMetres to revealsDepth
  }

  override fun pitchCamp(at: Vec3L, level: Int, durationSeconds: Long): Boolean {
    camps += Triple(at, level, durationSeconds)
    return true
  }

  var calledBestias = 0

  override fun callOwnBestias(): Int = calledBestias

  val knockBacks = mutableListOf<KnockBack>()

  override fun knockBack(entityId: EntityId, from: Vec3L, tiles: Int): Int {
    knockBacks.add(KnockBack(entityId, from, tiles))
    return tiles
  }

  val givenItems = mutableListOf<Pair<Long, Int>>()

  override fun giveItem(itemId: Long, amount: Int) {
    givenItems += itemId to amount
  }

  val revived = mutableListOf<Pair<EntityId, Int>>()

  override fun revive(entityId: EntityId, healthPercent: Int): Boolean {
    revived += entityId to healthPercent
    return true
  }

  val blinks = mutableListOf<Pair<EntityId, Vec3L>>()

  override fun blink(entityId: EntityId, to: Vec3L): Boolean {
    blinks += entityId to to
    return true
  }

  /** What [partyNear] answers; the caster alone by default. */
  var party: List<EntityId> = emptyList()

  override fun partyNear(radiusMetres: Long): List<EntityId> = party

  override fun payCasterCost(mana: Int, stamina: Int): Boolean {
    if (!manaAvailable) {
      return false
    }

    manaSpent += mana
    staminaSpent += stamina

    return true
  }

  override fun offerRecipes(skillId: Long) {
    recipeOffers += skillId
  }

  var townsFounded = 0

  val structuresBegun = mutableListOf<Pair<StaticEntityKind, Vec3L>>()

  override fun beginStructure(kind: StaticEntityKind, at: Vec3L, buildSeconds: Float): Long? {
    structuresBegun += kind to at

    return structuresBegun.size.toLong()
  }

  override fun foundTown(): Boolean {
    townsFounded++

    return true
  }

  /** What the next [readTracks] answers with. Null - clean ground - is the useful default. */
  var tracks: TrackReading? = null

  val trackReads = mutableListOf<TrackRead>()

  override fun readTracks(centre: Vec3L, radiusTiles: Long): TrackReading? {
    trackReads += TrackRead(centre, radiusTiles)

    return tracks
  }

  data class SpawnedAreaEffect(val centre: Vec3L, val visualId: Long, val effect: AreaEffect)

  data class AppliedEffect(
    val targetEntityId: EntityId,
    val effectId: Long,
    val level: Int,
    val amount: Int = 0,
    val durationSeconds: Double? = null,
  )

  data class GuildEffect(val guildId: Long, val centre: Vec3L, val radiusMetres: Long, val effectId: Long, val level: Int)

  data class GuildRestoration(val guildId: Long, val centre: Vec3L, val radiusMetres: Long, val percent: Int)


  data class TrackRead(val centre: Vec3L, val radiusTiles: Long)
}
