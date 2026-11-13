package net.bestia.zone.spellbinding

/** Who may set a Spell Binding off and be its target, picked by the binder at the cast. */
enum class BindingFilter(val id: Long) {
  FOES(1),
  ALLIES(2),
  ANYONE_BUT_BINDER(3),
  WILD_ONLY(4);

  companion object {
    private val byId = entries.associateBy { it.id }

    fun findById(id: Long): BindingFilter? {
      return byId[id]
    }
  }
}
