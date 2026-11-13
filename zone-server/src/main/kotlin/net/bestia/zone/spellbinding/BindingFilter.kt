package net.bestia.zone.spellbinding

/** Who may set a Spell Binding off and be its target, picked by the binder at the cast. */
enum class BindingFilter(val id: Long, val words: String) {
  FOES(1, "foes"),
  ALLIES(2, "allies"),
  ANYONE_BUT_BINDER(3, "anyone but the binder"),
  WILD_ONLY(4, "wild bestias only");

  companion object {
    private val byId = entries.associateBy { it.id }

    fun findById(id: Long): BindingFilter? {
      return byId[id]
    }
  }
}
