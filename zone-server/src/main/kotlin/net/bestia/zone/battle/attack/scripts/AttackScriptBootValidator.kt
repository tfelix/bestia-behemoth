package net.bestia.zone.battle.attack.scripts

import jakarta.annotation.PostConstruct
import net.bestia.zone.battle.attack.AttackRepository
import org.springframework.stereotype.Component

@Component
class AttackScriptBootValidator(
  private val attackRepository: AttackRepository
) {

  @PostConstruct
  fun validateAttackScripts() {
    val attacks = attackRepository.findAll()

    for (attack in attacks) {
      val scriptName = attack.script

      if(scriptName != null) {
        val scriptClassName = "net.bestia.behemoth.battle.attack.scripts.${scriptName}"
        try {
          Class.forName(scriptClassName)
        } catch (e: ClassNotFoundException) {
          throw IllegalStateException("Missing script class ($scriptName) for attack: $attack")
        }
      }
    }
  }
}