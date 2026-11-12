package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.LineOfSightService
import org.springframework.stereotype.Component

/** Fire Bolt (`skills.yml` id 5). */
@Component
class Firebolt(losService: LineOfSightService) : MasterBolt(losService)
