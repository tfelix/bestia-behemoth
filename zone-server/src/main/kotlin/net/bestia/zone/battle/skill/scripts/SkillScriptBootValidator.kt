package net.bestia.zone.battle.skill.scripts

import jakarta.annotation.PostConstruct
import net.bestia.zone.battle.skill.SkillRepository
import org.springframework.stereotype.Component

@Component
class SkillScriptBootValidator(
  private val skillRepository: SkillRepository
) {

  @PostConstruct
  fun validateSkillScripts() {
    val skills = skillRepository.findAll()

    for (skill in skills) {
      val scriptName = skill.script

      if(scriptName != null) {
        val scriptClassName = "net.bestia.behemoth.battle.attack.scripts.${scriptName}"
        try {
          Class.forName(scriptClassName)
        } catch (e: ClassNotFoundException) {
          throw IllegalStateException("Missing script class ($scriptName) for skill: $skill")
        }
      }
    }
  }
}
