package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.ecs.core.update
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.net.EquipItemCMSG
import net.bestia.zone.spellbinding.StrangerPickup
import net.bestia.zone.spellbinding.SpellBindingGlyphs
import net.bestia.zone.spellbinding.SpellBindingSensing
import net.bestia.zone.casting.sense.MagicSensing
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.casting.InscribedSpell
import net.bestia.zone.casting.net.ActivateSkillCMSG
import net.bestia.zone.casting.sense.SenseKind
import net.bestia.zone.casting.sense.SenseMarksSMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.spellbinding.BindingFilter
import net.bestia.zone.spellbinding.BindingTrigger
import net.bestia.zone.spellbinding.GroundSpellBinding
import net.bestia.zone.spellbinding.SpellBindingBook
import net.bestia.zone.spellbinding.SpellBindingChoice
import net.bestia.zone.spellbinding.SpellBindingService
import net.bestia.zone.spellbinding.SpellBindingsSMSG
import net.bestia.zone.spellbinding.persistence.SpellBindingRepository
import net.bestia.zone.spellscroll.SpellScrollScript
import net.bestia.zone.spellscroll.SpellScrollService
import net.bestia.zone.spellscroll.persistence.SpellScrollInscriptionRepository
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Sage's Spell Bindings over the real services and the tick: refusals, a trap, a delay, the limit, a release. */
class SpellBindingScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var skills: SkillRepository

  @Autowired
  private lateinit var scrolls: SpellScrollService

  @Autowired
  private lateinit var inscriptions: SpellScrollInscriptionRepository

  @Autowired
  private lateinit var bindings: SpellBindingService

  @Autowired
  private lateinit var book: SpellBindingBook

  @Autowired
  private lateinit var rows: SpellBindingRepository

  @Autowired
  private lateinit var spawner: BestiaEntitySpawner

  @Autowired
  private lateinit var catalogue: BestiaCatalogue

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var lootItems: LootItemEntitySpawner

  @Autowired
  private lateinit var strangerPickup: StrangerPickup

  @Autowired
  private lateinit var glyphs: SpellBindingGlyphs

  @Autowired
  private lateinit var sensing: SpellBindingSensing

  @Autowired
  private lateinit var magicSensing: MagicSensing

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private val foes = mutableListOf<EntityId>()

  private val accountId get() = client.connectedPlayerId

  private val bindingSkillId by lazy { assertNotNull(skills.findByIdentifier("M_SPELL_BINDING")).id }
  private val tackleId by lazy { assertNotNull(skills.findByIdentifier("tackle")).id }

  @BeforeAll
  fun createSage() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "binder",
        hairColor = Color.GREEN,
        skinColor = Color.GREEN,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id

    // Before connecting: the live bag is read from the database when the master enters the world.
    inventoryService.addItem(masterId, "stone_knife", 1)
    inventoryService.addItem(masterId, "yellow_mana_crystal", 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
    client.connect(masterId)
    learnBinding(level = 1)
    // A recalc during the 10 s cast bar clamps mana to the level's own pool, so the Sage needs a real one.
    world.modify(sage()) { id ->
      repeat(SAGE_LEVEL - 1) { get(id, Level::class)!!.inc() }
      add(id, IsStatusValueDirty)
    }
    await { assertTrue(world.read { get(sage(), Mana::class)!!.max } >= MANA_NEEDED) }
  }

  /** Every Spring context shares one H2 database, see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    world.modify(sage()) { id -> book.of(masterId).forEach { bindings.forget(this, it) } }
    client.disconnect()
    removeFoes()
    TransactionTemplate(transactionManager).execute { inscriptions.deleteAll() }
  }

  @Test
  @Order(1)
  fun `a trigger above the skill level and a scroll too strong for it are refused before the cast bar`() {
    val tackle = inscribe(tackleId)
    client.clearMessages()

    cast(listOf(SpellBindingChoice.BIND, tackle, BindingTrigger.COMES_NEAR.id, BindingFilter.FOES.id, 0), level = 1)

    assertEquals(OpError.SPELL_BINDING_TRIGGER_LOCKED, awaitError())

    val strongSkill = strongAttack()
    val strong = inscribe(strongSkill)
    client.clearMessages()

    cast(listOf(SpellBindingChoice.BIND, strong, BindingTrigger.STEPS_ON.id, BindingFilter.FOES.id, 0), level = 1)

    assertEquals(OpError.SPELL_BINDING_TOO_STRONG, awaitError())
    assertTrue(book.of(masterId).isEmpty())
  }

  @Test
  @Order(2)
  fun `a cast binds the scroll to the ground, and a wild bestia stepping on it is struck and reported`() {
    learnBinding(level = 3)
    val tackle = heldScroll(tackleId)
    val spot = sageAt() + Vec3L(2, 0, 0)
    client.clearMessages()

    cast(listOf(SpellBindingChoice.BIND, tackle, BindingTrigger.STEPS_ON.id, BindingFilter.WILD_ONLY.id, 0), at = spot)

    val listed = awaitValue { client.tryGetLastReceived(SpellBindingsSMSG::class)?.bindings?.singleOrNull() }
    assertEquals(BindingTrigger.STEPS_ON, listed.bound.trigger)
    assertEquals(spot, listed.position)
    assertFalse(heldScrolls().contains(tackle))
    val glyph = assertNotNull(listed.glyphId)
    assertTrue(world.read { has(glyph, GroundSpellBinding::class) })
    await { assertTrue(rows.existsById(listed.bound.id)) }

    val blob = spawnBlobAt(spot)

    await {
      assertTrue(client.receivedAny(SenseMarksSMSG::class) { msg -> msg.marks.any { it.kind == SenseKind.BINDING_FIRED } })
    }
    await { assertTrue(world.read { get(blob, Health::class)!!.let { it.current < it.max } }) }
    assertFalse(world.read { isAlive(glyph) })
    assertTrue(book.of(masterId).isEmpty())
    await { assertFalse(rows.existsById(listed.bound.id)) }
  }

  @Test
  @Order(3)
  fun `a delayed binding with nobody near fizzles and is spent`() {
    removeFoes()
    val tackle = inscribe(tackleId)
    client.clearMessages()

    bindDirectly(listOf(SpellBindingChoice.BIND, tackle, BindingTrigger.AFTER_DELAY.id, BindingFilter.FOES.id, 5))
    assertEquals(1, book.of(masterId).size)

    await {
      val report = client.tryGetLastReceived(SenseMarksSMSG::class)?.marks?.singleOrNull()
      assertEquals(SenseKind.BINDING_FIRED, report?.kind)
      assertTrue(report!!.label.endsWith("nobody"))
    }
    assertTrue(book.of(masterId).isEmpty())
  }

  @Test
  @Order(4)
  fun `a binding beyond the limit replaces the oldest, and the binder releases one for free`() {
    val first = inscribe(tackleId)
    val second = inscribe(tackleId)
    val third = inscribe(tackleId)
    val fourth = inscribe(tackleId)
    for (scroll in listOf(first, second, third, fourth)) {
      bindDirectly(listOf(SpellBindingChoice.BIND, scroll, BindingTrigger.STEPS_ON.id, BindingFilter.FOES.id, 0))
    }

    val kept = book.of(masterId)
    assertEquals(3, kept.size)
    val oldest = kept.first()
    client.clearMessages()

    client.sendMessage(ActivateSkillCMSG(accountId, bindingSkillId, 3, Vec3L.ZERO, 0, listOf(SpellBindingChoice.RELEASE, oldest.bound.id)))

    await { assertEquals(2, client.tryGetLastReceived(SpellBindingsSMSG::class)?.bindings?.size) }
    assertFalse(world.read { isAlive(oldest.glyphId!!) })
    assertTrue(book.of(masterId).none { it.bound.id == oldest.bound.id })
  }

  @Test
  @Order(5)
  fun `a binding on a worn knife strikes back at whoever hits its holder`() {
    removeFoes()
    learnBinding(level = 5)
    val pile = world.read { get(sage(), Inventory::class)!!.getItems().first { it.itemId == STONE_KNIFE } }
    client.sendMessage(EquipItemCMSG(accountId, pile.playerItemId, EquipmentSlot.RIGHT_HAND))
    await { assertNotNull(world.read { get(sage(), Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) }) }
    bindDirectly(itemBinding(BindingTrigger.HOLDER_STRUCK, knife().uniqueId), level = 5)
    val blob = spawnBlobAt(sageAt() + Vec3L(1, 0, 0))
    client.clearMessages()

    strike(source = blob, target = sage())

    awaitFiredOn(blob)
  }

  @Test
  @Order(6)
  fun `a binding on a worn knife follows the holder's own blow`() {
    val blob = foes.single()
    bindDirectly(itemBinding(BindingTrigger.HOLDER_STRIKES, knife().uniqueId), level = 5)
    restoreHealth(blob)
    client.clearMessages()

    strike(source = sage(), target = blob)

    awaitFiredOn(blob)
  }

  @Test
  @Order(7)
  fun `a binding on a worn knife fires once its holder falls below 30 percent`() {
    val blob = foes.single()
    bindDirectly(itemBinding(BindingTrigger.HOLDER_LOW_HP, knife().uniqueId), level = 5)
    restoreHealth(blob)
    val before = world.modify(sage()) { id -> get(id, Health::class)!!.apply { current = max * 40 / 100 }.max }!!
    client.clearMessages()

    strike(source = blob, target = sage(), amount = before * 20 / 100)

    awaitFiredOn(blob)
    world.modify(sage()) { id -> get(id, Health::class)!!.apply { current = max } }
  }

  @Test
  @Order(8)
  fun `a stranger who picks the item up sets its binding off, the binder's own side does not`() {
    val blob = foes.single()
    val knife = knife()
    bindDirectly(itemBinding(BindingTrigger.STRANGER_PICKS_UP, knife.uniqueId), level = 5)
    restoreHealth(blob)
    val stack = world.read { lootItems.spawnLootItem(this, knife.itemId, 1, sageAt(), uniqueId = knife.uniqueId) }
    client.clearMessages()

    world.modify(sage()) { id -> strangerPickup.onPickUp(this, id, stack) }
    world.modify(blob) { id -> strangerPickup.onPickUp(this, id, stack) }

    awaitFiredOn(blob)
    world.modify(stack) { id -> destroy(id) }
  }

  @Test
  @Order(9)
  fun `Magic Sense Lv 5 names the binder and the trigger and shows the glyph, Lv 4 only the spell`() {
    removeFoes()
    val glyph = bindGlyph()
    val senseId = skillId("M_MAGIC_SENSE")
    world.modify(sage()) { id -> get(id, KnownSkills::class)!!.learnOrUpdate(senseId, 5) }
    client.clearMessages()

    world.modify(sage()) { id -> magicSensing.sense(this, id, level = 4, potency = 100) }
    val lv4 = awaitValue { client.tryGetLastReceived(SenseMarksSMSG::class) }.marks.filter { it.label.contains("Tackle") }
    assertTrue(lv4.isNotEmpty())
    assertTrue(lv4.none { it.label.contains("bound by") })
    assertTrue(glyphs.shownTo(glyph).isEmpty())
    client.clearMessages()

    client.sendMessage(ActivateSkillCMSG(accountId, senseId, 5, sageAt(), 0))

    await {
      val marks = assertNotNull(client.tryGetLastReceived(SenseMarksSMSG::class)).marks
      assertTrue(marks.any { it.label.endsWith("Tackle, steps on it, foes, bound by binder") })
    }
    assertTrue(accountId in glyphs.shownTo(glyph))
  }

  @Test
  @Order(10)
  fun `a binding stronger than the sweep is found only close by`() {
    val far = sageAt() + Vec3L(20, 0, 0)

    val weak = world.read { sensing.around(this, far, range = 50, potency = 0) }
    val strong = world.read { sensing.around(this, far, range = 50, potency = 100) }

    assertTrue(weak.none { it.potency == GLYPH_POTENCY })
    assertTrue(strong.any { it.potency == GLYPH_POTENCY })
  }

  @Test
  @Order(11)
  fun `Dispel aimed at a shown glyph lifts the binding`() {
    val glyph = book.of(masterId).single { it.glyphId != null && it.bound.potency == GLYPH_POTENCY }.glyphId!!
    val dispelId = skillId("M_DISPEL")
    world.modify(sage()) { id ->
      get(id, KnownSkills::class)!!.learnOrUpdate(dispelId, 5)
      get(id, Mana::class)!!.apply { current = max }
    }

    client.sendMessage(ActivateSkillCMSG(accountId, dispelId, 5, sageAt(), glyph))

    await { assertFalse(world.read { isAlive(glyph) }) }
    assertTrue(book.of(masterId).none { it.glyphId == glyph })
  }

  /** A glyph under the Sage of a known Potency, so the contest tests can reason about it. */
  private fun bindGlyph(): EntityId {
    val scroll = inscribe(tackleId)
    val args = listOf(SpellBindingChoice.BIND, scroll, BindingTrigger.STEPS_ON.id, BindingFilter.FOES.id, 0)
    world.modify(sage()) { id -> bindings.bind(this, id, 5, args, sageAt(this, id), potency = GLYPH_POTENCY) }

    return book.of(masterId).last().glyphId!!
  }

  private fun skillId(identifier: String): Long = assertNotNull(skills.findByIdentifier(identifier)).id

  private fun itemBinding(trigger: BindingTrigger, itemId: Long): List<Long> {
    return listOf(SpellBindingChoice.BIND, inscribe(tackleId), trigger.id, BindingFilter.FOES.id, itemId)
  }

  /** The worn knife: a stackable knife is worn as a copy split off its pile, which has an identity of its own. */
  private fun knife(): Equipment.EquippedItem {
    return assertNotNull(world.read { get(sage(), Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) })
  }

  private fun strike(source: EntityId, target: EntityId, amount: Int = 1) {
    world.modify(target) { id -> update(id, { IncomingDamage() }) { it.add(amount, source) } }
  }

  private fun restoreHealth(blob: EntityId) {
    world.modify(blob) { id -> get(id, Health::class)!!.apply { current = max } }
  }

  private fun awaitFiredOn(blob: EntityId) {
    await {
      assertTrue(client.receivedAny(SenseMarksSMSG::class) { msg -> msg.marks.any { it.kind == SenseKind.BINDING_FIRED } })
    }
    await { assertTrue(world.read { get(blob, Health::class)!!.let { it.current < it.max } }) }
    assertTrue(book.of(masterId).none { it.itemUniqueId != null })
  }

  private fun cast(args: List<Long>, level: Int = 3, at: Vec3L = sageAt()) {
    world.modify(sage()) { id -> get(id, Mana::class)!!.apply { current = max } }
    client.sendMessage(ActivateSkillCMSG(accountId, bindingSkillId, level, at, 0, args))
  }

  /** Past the cast bar, which the first trap test already sat through. */
  private fun bindDirectly(args: List<Long>, level: Int = 3) {
    val refusal = world.modify(sage()) { id -> bindings.bind(this, id, level, args, sageAt(this, id), potency = 50) }
    assertEquals(null, refusal)
  }

  private fun learnBinding(level: Int) {
    // Looked up first: SQL is refused inside the lease.
    val skillId = bindingSkillId
    world.modify(sage()) { id -> get(id, KnownSkills::class)!!.learnOrUpdate(skillId, level) }
  }

  private fun awaitError(): OpError {
    return awaitValue { client.tryGetLastReceived(OperationErrorSMSG::class) }.code
  }

  /** An attack the blob learns, whose attack level is above what Spell Binding Lv. 1 may bind. */
  private fun strongAttack(): Long {
    val learnset = catalogue.learnset(BLOB)

    return learnset.map { it.skillId }.first { (catalogue.attackLevelOf(it) ?: 0) > 20 }
  }

  /** @return the new scroll's unique id, once it is in the bag */
  private fun inscribe(skillId: Long): Long {
    val before = heldScrolls()
    world.modify(sage()) { id -> scrolls.inscribe(this, id, InscribedSpell(skillId, level = 1, attackLevel = 1)) }

    return awaitValue { (heldScrolls() - before.toSet()).singleOrNull() }
  }

  private fun heldScroll(skillId: Long): Long {
    return heldScrolls().first { scrolls.spellOn(it)?.skillId == skillId }
  }

  private fun heldScrolls(): List<Long> {
    return world.read {
      get(sage(), Inventory::class)!!.getItems().filter { it.itemId == SpellScrollScript.ITEM_ID }.map { it.uniqueId }
    }
  }

  private fun spawnBlobAt(at: Vec3L): EntityId {
    // Loads the learnset now: the spawner reads it inside the lease, where SQL is refused.
    catalogue.learnset(BLOB)
    val blob = world.read { spawner.spawnMob(this, BLOB, at, persistent = false) }
    foes += blob
    world.modify(blob) { id ->
      remove(id, AiAgent::class)
      get(id, Health::class)!!.apply { max = BLOB_HEALTH; current = BLOB_HEALTH }
    }

    return blob
  }

  private fun removeFoes() {
    foes.forEach { foe -> world.modify(foe) { id -> if (isAlive(id)) destroy(id) } }
    foes.clear()
  }

  private fun sageAt(): Vec3L {
    return world.read { getOrThrow(sage(), Position::class).toVec3L() }
  }

  private fun sageAt(world: net.bestia.zone.ecs.core.World, id: EntityId): Vec3L {
    return world.get(id, Position::class)!!.toVec3L()
  }

  private fun sage(): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun <T : Any> awaitValue(fn: () -> T?): T {
    var result: T? = null
    await { result = assertNotNull(fn()) }
    return result!!
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 45L
    const val BLOB = 1L
    const val STONE_KNIFE = 103L
    /** Below any Sage's Dispel, so the Dispel always wins; far enough below a sweep of 100 to be found from afar. */
    const val GLYPH_POTENCY = 20
    const val SAGE_LEVEL = 60
    const val MANA_NEEDED = 45
    const val BLOB_HEALTH = 100_000
  }
}
