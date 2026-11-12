package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.LineOfSightService
import org.springframework.stereotype.Component

/** Thunder Bolt (`skills.yml` id 100). */
@Component
class ThunderBolt(losService: LineOfSightService) : MasterBolt(losService)
