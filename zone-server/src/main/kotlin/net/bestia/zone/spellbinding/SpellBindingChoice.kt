package net.bestia.zone.spellbinding

/**
 * What the binder asked for in the cast's args. User input, so [parse] returns null for anything malformed, and the
 * script answers that with `SKILL_INVALID_CHOICE`.
 *
 * `[1, scroll, trigger, filter, extra]` binds: `extra` is the delay in seconds for [BindingTrigger.AFTER_DELAY], the
 * item's unique id for an item trigger, and 0 otherwise. `[2, binding]` releases one of the binder's own bindings.
 */
sealed interface SpellBindingChoice {

  data class Bind(
    val scrollId: Long,
    val trigger: BindingTrigger,
    val filter: BindingFilter,
    val delaySeconds: Long,
    val itemUniqueId: Long?,
  ) : SpellBindingChoice

  data class Release(val bindingId: Long) : SpellBindingChoice

  companion object {
    const val BIND = 1L
    const val RELEASE = 2L

    fun parse(args: List<Long>): SpellBindingChoice? {
      return when (args.firstOrNull()) {
        BIND -> parseBind(args)
        RELEASE -> args.getOrNull(1)?.let(::Release)
        else -> null
      }
    }

    private fun parseBind(args: List<Long>): Bind? {
      if (args.size != 5) {
        return null
      }
      val trigger = BindingTrigger.findById(args[2]) ?: return null
      val filter = BindingFilter.findById(args[3]) ?: return null
      val extra = args[4]
      val delayIsValid = extra in SpellBindingRules.MIN_DELAY_SECONDS..SpellBindingRules.MAX_DELAY_SECONDS
      if (trigger == BindingTrigger.AFTER_DELAY && !delayIsValid) {
        return null
      }

      return Bind(
        scrollId = args[1],
        trigger = trigger,
        filter = filter,
        delaySeconds = if (trigger == BindingTrigger.AFTER_DELAY) extra else 0,
        itemUniqueId = if (trigger.onItem) extra else null,
      )
    }
  }
}
