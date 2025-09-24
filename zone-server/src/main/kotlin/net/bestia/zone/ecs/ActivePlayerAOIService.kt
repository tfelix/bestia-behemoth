package net.bestia.zone.ecs

import org.springframework.stereotype.Service

@ZoneInjectable
@Service
class ActivePlayerAOIService: AreaOfInterestService<Long>()

