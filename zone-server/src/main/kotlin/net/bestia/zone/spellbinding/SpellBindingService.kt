package net.bestia.zone.spellbinding

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.casting.SpellBindings
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.spellscroll.SpellScrollService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock

/** Makes and ends Spell Bindings: the scroll, the limit, the glyph, the row and the binder's list. On the tick. */
@Service
class SpellBindingService(
  private val scrolls: SpellScrollService,
  private val book: SpellBindingBook,
  private val store: SpellBindingStore,
  private val glyphs: SpellBindingGlyphs,
  private val notices: SpellBindingNotices,
  private val clock: Clock = Clock.systemUTC(),
) : SpellBindings {

  override fun bindRefusal(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val choice = SpellBindingChoice.parse(args) as? SpellBindingChoice.Bind ?: return OpError.SKILL_INVALID_CHOICE

    return refusal(world, casterId, skillLevel, choice)
  }

  override fun bind(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>, at: Vec3L, potency: Int): OpError? {
    val choice = SpellBindingChoice.parse(args) as? SpellBindingChoice.Bind ?: return OpError.SKILL_INVALID_CHOICE
    refusal(world, casterId, skillLevel, choice)?.let { return it }
    val owner = ownerOf(world, casterId) ?: return OpError.REQUEST_REFUSED
    val binder = binderOf(world, casterId) ?: return OpError.REQUEST_REFUSED
    val spell = scrolls.takeScroll(world, casterId, choice.scrollId) ?: return OpError.SKILL_INVALID_CHOICE

    val now = clock.instant()
    val bound = BoundSpell(
      id = book.newId(),
      owner = owner,
      spell = spell,
      trigger = choice.trigger,
      filter = choice.filter,
      potency = potency,
      binder = binder,
      boundAt = now,
      firesAt = if (choice.trigger == BindingTrigger.AFTER_DELAY) now.plusSeconds(choice.delaySeconds) else null,
    )
    book.add(SpellBindingBook.Entry(bound, glyphId = glyphs.spawn(world, bound, at), position = at))
    store.save(bound, at, itemInstanceId = null)
    replaceOldestBeyond(world, owner.masterId, SpellBindingRules.maxBindings(skillLevel))
    notices.sendList(owner)

    return null
  }

  override fun release(world: World, casterId: EntityId, args: List<Long>): Boolean {
    val choice = SpellBindingChoice.parse(args) as? SpellBindingChoice.Release ?: return false
    val masterId = world.get(casterId, Master::class)?.masterId ?: return true
    book.find(masterId, choice.bindingId)?.let { forget(world, it) }

    return true
  }

  /**
   * Ends [entry] for good, fired or not: its glyph, its row, its place in the binder's list. False when it had
   * already ended, so a binding found twice fires once.
   */
  fun forget(world: World, entry: SpellBindingBook.Entry): Boolean {
    val bound = entry.bound
    book.remove(bound.owner.masterId, bound.id) ?: return false
    entry.glyphId?.takeIf { world.isAlive(it) }?.let(world::destroy)
    store.delete(bound)
    notices.sendList(bound.owner)

    return true
  }

  private fun refusal(world: World, casterId: EntityId, skillLevel: Int, choice: SpellBindingChoice.Bind): OpError? {
    // Item bindings come with their own triggers in a later step.
    if (choice.trigger.onItem) {
      return OpError.SKILL_INVALID_CHOICE
    }
    if (!choice.trigger.isLearnedAt(skillLevel)) {
      return OpError.SPELL_BINDING_TRIGGER_LOCKED
    }
    val spell = scrolls.heldSpell(world, casterId, choice.scrollId) ?: return OpError.SKILL_INVALID_CHOICE

    return if (spell.attackLevel > SpellBindingRules.maxAttackLevel(skillLevel)) OpError.SPELL_BINDING_TOO_STRONG else null
  }

  private fun replaceOldestBeyond(world: World, masterId: Long, limit: Int) {
    book.of(masterId).dropLast(limit).forEach { forget(world, it) }
  }

  private fun ownerOf(world: World, casterId: EntityId): SpellBindingOwner? {
    val master = world.get(casterId, Master::class) ?: return null
    val accountId = world.get(casterId, Account::class)?.accountId ?: return null

    return SpellBindingOwner(accountId, master.masterId, master.name)
  }

  private fun binderOf(world: World, casterId: EntityId): BinderSnapshot? {
    val values = world.get(casterId, CombatStats::class)?.values ?: return null

    return BinderSnapshot.of(world.get(casterId, Level::class)?.level ?: 1, values)
  }
}
