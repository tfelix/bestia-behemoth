package net.bestia.zone.spellbinding

/** What a Spell Binding waits for. [id] is the number the client sends in the cast's args and gets back in lists. */
enum class BindingTrigger(val id: Long, val onItem: Boolean, val fromSkillLevel: Int) {
  STEPS_ON(1, onItem = false, fromSkillLevel = 1),
  COMES_NEAR(2, onItem = false, fromSkillLevel = 2),
  AFTER_DELAY(3, onItem = false, fromSkillLevel = 3),
  HOLDER_STRUCK(4, onItem = true, fromSkillLevel = 4),
  HOLDER_STRIKES(5, onItem = true, fromSkillLevel = 4),
  HOLDER_LOW_HP(6, onItem = true, fromSkillLevel = 5),
  STRANGER_PICKS_UP(7, onItem = true, fromSkillLevel = 5);

  fun isLearnedAt(skillLevel: Int): Boolean {
    return skillLevel >= fromSkillLevel
  }

  companion object {
    private val byId = entries.associateBy { it.id }

    fun findById(id: Long): BindingTrigger? {
      return byId[id]
    }
  }
}
