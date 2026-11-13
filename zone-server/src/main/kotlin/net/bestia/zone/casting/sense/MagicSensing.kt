package net.bestia.zone.casting.sense

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.ItemEnchantments
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Magic Sense: mana at work around the caster. Spell fields, anything under a buff or debuff, enchanted items on the
 * ground, and mana-bearing crystals. Each level tells more about each source: that it is there, where and how strong,
 * its element, the exact spell, and for a Spell Binding who bound it and what it waits for.
 */
@Component
class MagicSensing(
  private val worldService: WorldService,
  private val mana: ManaConcentrationService,
  private val skills: SkillRepository,
  private val statusDefinitions: StatusEffectDefinitionRegistry,
  private val statusScripts: StatusEffectScriptRegistry,
  private val messages: OutMessageProcessor,
  private val bindings: SensedBindings? = null,
) {

  private val skillNames: Map<Long, String> by lazy { skills.findAll().associate { it.id to it.identifier } }

  /** Before the tick runs: a sweep reads the names on the tick, where SQL is refused. */
  @EventListener(ApplicationReadyEvent::class)
  fun loadSkillNames() {
    skillNames.size
  }

  /**
   * One mana source, before the level decides how much of it the caster learns. [reading] is what level 5 adds about
   * a Spell Binding.
   */
  data class Source(val at: Vec3L, val potency: Int, val element: Element, val name: String, val reading: String = "")

  fun sense(world: World, casterId: EntityId, level: Int, potency: Int = 0) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return
    // Strong mana carries the sense further, drained land muffles it.
    val range = (RANGE_PER_LEVEL * level * mana.factorAt(at)).toLong()

    val sensedBindings = bindings?.around(world, at, range, potency).orEmpty()
    if (level >= BINDING_LEVEL) {
      val viewers = partyOf(world, casterId) + accountId
      sensedBindings.mapNotNull { it.glyphId }.forEach { bindings?.reveal(world, it, viewers, VISIBLE_SECONDS) }
    }

    val found = sourcesAround(world, casterId, at, range) +
      sensedBindings.map { Source(it.at, it.potency, it.element, it.spellName, it.reading) }
    val voxel = worldService.config.voxelSize
    val marks = found.map { source ->
      SenseMark(
        kind = SenseKind.MANA,
        eastMetres = source.at.x * voxel,
        northMetres = source.at.y * voxel,
        label = labelOf(source, level),
        vague = level < POSITION_LEVEL,
      )
    }

    messages.sendToPlayer(accountId, SenseMarksSMSG(marks, VISIBLE_SECONDS))
  }

  fun sourcesAround(world: World, casterId: EntityId, at: Vec3L, range: Long): List<Source> {
    val sources = ArrayList<Source>()
    fun near(position: Vec3L) = position.distance(at) <= range

    world.query(AreaEffect::class, Position::class).each { _ ->
      val field = get<AreaEffect>()
      val position = get<Position>().toVec3L()
      if (near(position)) sources += Source(position, field.potency, field.element, nameOf(skillNames[field.skillId]))
    }

    world.query(StatusEffects::class, Position::class).each { id ->
      if (id == casterId) return@each
      val position = get<Position>().toVec3L()
      if (!near(position)) return@each
      val strongest = get<StatusEffects>().activeEffects
        .filter { it.polarity != StatusEffectPolarity.NEUTRAL }
        .maxByOrNull { it.potency }
        ?: return@each
      val definition = statusDefinitions.findById(strongest.definitionId) ?: return@each
      val element = statusScripts.get(definition.script)?.element ?: Element.NORMAL
      sources += Source(position, strongest.potency, element, nameOf(definition.identifier))
    }

    world.query(GroundItemStack::class, ItemEnchantments::class, Position::class).each { _ ->
      val position = get<Position>().toVec3L()
      val strongest = get<ItemEnchantments>().enchantments.maxByOrNull { it.strength } ?: return@each
      if (near(position)) sources += Source(position, strongest.strength, Element.NORMAL, nameOf(strongest.enchantment.name))
    }

    world.query(StaticVisual::class, PropPose::class).each { _ ->
      val kind = get<StaticVisual>().kind
      val strength = MANA_PROPS[kind] ?: return@each
      val position = get<PropPose>().position
      if (near(position)) sources += Source(position, strength, Element.NORMAL, nameOf(kind.name))
    }

    return sources
  }

  private fun labelOf(source: Source, level: Int): String {
    if (level < POSITION_LEVEL) return ""
    val strength = when {
      source.potency < FAINT_BELOW -> "faint"
      source.potency < STRONG_BELOW -> "strong"
      else -> "overwhelming"
    }
    if (level < ELEMENT_LEVEL) return strength
    val element = nameOf(source.element.name.substringBefore('_').let { if (it == "NORMAL") "NEUTRAL" else it })
      .lowercase()
    if (level < SPELL_LEVEL) return "$strength $element"

    if (level < BINDING_LEVEL || source.reading.isEmpty()) return "$strength $element: ${source.name}"

    return "$strength $element: ${source.name}, ${source.reading}"
  }

  private fun partyOf(world: World, casterId: EntityId): Set<AccountId> {
    return world.get(casterId, PartyMembership::class)?.members?.mapTo(HashSet()) { it.accountId }.orEmpty()
  }

  private fun nameOf(identifier: String?): String {
    return identifier.orEmpty().removePrefix("M_").split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
  }

  private companion object {
    const val RANGE_PER_LEVEL = 10.0
    const val POSITION_LEVEL = 2
    const val ELEMENT_LEVEL = 3
    const val SPELL_LEVEL = 4
    const val BINDING_LEVEL = 5

    /** Potency bands: a player's cast sits near 100, a ward stone far above. */
    const val FAINT_BELOW = 50
    const val STRONG_BELOW = 150

    /** Long enough to walk a few steps towards a source, as short as the skill's own 15 s. */
    const val VISIBLE_SECONDS = 15

    /** Props that carry mana, and how strong they read. */
    val MANA_PROPS = mapOf(
      StaticEntityKind.MANA_CRYSTAL_SMALL to 40,
      StaticEntityKind.MANA_CRYSTAL_LARGE to 90,
      StaticEntityKind.AETHERITE_SHARD_SMALL to 60,
      StaticEntityKind.AETHERITE_SHARD_LARGE to 120,
      StaticEntityKind.MANA_CIRCLE to 100,
    )
  }
}
