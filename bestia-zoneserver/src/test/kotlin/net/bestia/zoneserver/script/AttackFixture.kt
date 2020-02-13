package net.bestia.zoneserver.script

import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackTarget
import net.bestia.model.battle.AttackType
import net.bestia.model.battle.Element

object AttackFixture {
  val EMBER = Attack(
      databaseName = "ember",
      cooldown = 5000,
      hasScript = true,
      manaCost = 10,
      needsLineOfSight = true,
      range = 10,
      target = AttackTarget.GROUND,
      element = Element.FIRE,
      strength = 10,
      type = AttackType.RANGED_MAGIC,
      casttime = 3000
  )
}