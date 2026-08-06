package net.bestia.zone.battle.status

/**
 * Every status effect the server can apply, by name. This is what call sites use -
 * `statusEffectService.applyEffect(world, targetId, StatusEffectId.BLESSING, level)` - so picking an
 * effect is a symbol lookup with autocompletion rather than a magic number scattered over script
 * companion objects.
 *
 * Must stay in lockstep with `status_effects.yml`, which is the source of truth for the id's
 * metadata (polarity, icon visibility, client sync, script name).
 * [StatusEffectCatalogBootValidator] fails the boot if an entry exists on only one side, so the
 * duplication cannot silently rot. Same arrangement as
 * [net.bestia.zone.dialog.DialogId] / `dialogs.yml`.
 *
 * Ids are not contiguous on purpose: 3 belonged to the removed THORNS effect and is not reused, so
 * an old client or a stale log line can never be mistaken for a live effect.
 */
enum class StatusEffectId(val id: Long) {
  SWIFTNESS(1),
  CRIPPLE(2),
  RESISTED_ONCE_MARKER(4),
  BLESSING(5),
  MASTER_INTRO_MARKER(6);

  companion object {
    private val byId = entries.associateBy { it.id }

    fun findById(id: Long): StatusEffectId? = byId[id]
  }
}
