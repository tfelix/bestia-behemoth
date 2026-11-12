package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.LineOfSightService
import org.springframework.stereotype.Component

/** Ice Bolt (`skills.yml` id 99). */
@Component
class IceBolt(losService: LineOfSightService) : MasterBolt(losService)
