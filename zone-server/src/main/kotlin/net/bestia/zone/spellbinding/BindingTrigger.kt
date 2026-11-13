package net.bestia.zone.spellbinding

/**
 * What a Spell Binding waits for. [id] is the number the client sends in the cast's args and gets back in lists,
 * [words] what Magic Sense Lv. 5 reads.
 */
enum class BindingTrigger(val id: Long, val onItem: Boolean, val fromSkillLevel: Int, val words: String) {
  STEPS_ON(1, onItem = false, fromSkillLevel = 1, "steps on it"),
  COMES_NEAR(2, onItem = false, fromSkillLevel = 2, "comes near"),
  AFTER_DELAY(3, onItem = false, fromSkillLevel = 3, "after a delay"),
  HOLDER_STRUCK(4, onItem = true, fromSkillLevel = 4, "holder is struck"),
  HOLDER_STRIKES(5, onItem = true, fromSkillLevel = 4, "holder strikes"),
  HOLDER_LOW_HP(6, onItem = true, fromSkillLevel = 5, "holder's HP gets low"),
  STRANGER_PICKS_UP(7, onItem = true, fromSkillLevel = 5, "a stranger picks it up");

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
