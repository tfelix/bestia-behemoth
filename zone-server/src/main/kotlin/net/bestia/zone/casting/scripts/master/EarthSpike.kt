package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.LineOfSightService
import org.springframework.stereotype.Component

/** Earth Spike (`skills.yml` id 101). */
@Component
class EarthSpike(losService: LineOfSightService) : MasterBolt(losService)
