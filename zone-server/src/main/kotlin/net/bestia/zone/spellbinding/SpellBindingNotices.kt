package net.bestia.zone.spellbinding

import net.bestia.zone.casting.sense.SenseKind
import net.bestia.zone.casting.sense.SenseMark
import net.bestia.zone.casting.sense.SenseMarksSMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.world.WorldService
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** What a binder hears about their bindings: the list after each change, and a report when one fires. */
@Component
class SpellBindingNotices(
  private val book: SpellBindingBook,
  private val out: OutMessageProcessor,
  private val worldService: WorldService,
  private val skills: SkillRepository,
) {

  /** Loaded before the tick runs: a report is sent on the tick, where SQL is refused. */
  private var skillNames: Map<Long, String> = emptyMap()

  @EventListener(ApplicationReadyEvent::class)
  fun loadSkillNames() {
    skillNames = skills.findAll().associate { it.id to it.identifier }
  }

  fun sendList(owner: SpellBindingOwner) {
    out.sendToPlayer(owner.accountId, SpellBindingsSMSG(book.of(owner.masterId)))
  }

  /** A chat line and a compass mark. Sent whether or not the binder is online; an offline one simply misses it. */
  fun reportFired(bound: BoundSpell, at: Vec3L, setOffBy: String) {
    val voxel = worldService.config.voxelSize
    val mark = SenseMark(
      kind = SenseKind.BINDING_FIRED,
      eastMetres = at.x * voxel,
      northMetres = at.y * voxel,
      label = "${spellName(bound.spell.skillId)}, set off by $setOffBy",
      refId = bound.id,
    )
    out.sendToPlayer(bound.owner.accountId, SenseMarksSMSG(listOf(mark), REPORT_SECONDS))
  }

  fun spellName(skillId: Long): String {
    return skillNames[skillId].orEmpty().removePrefix("M_").split('_')
      .joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
  }

  private companion object {
    /** Long enough to turn and look, like a Magic Sense mark. */
    const val REPORT_SECONDS = 15
  }
}
