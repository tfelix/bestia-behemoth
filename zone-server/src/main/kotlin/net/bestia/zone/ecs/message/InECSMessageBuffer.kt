package net.bestia.zone.ecs.message

import net.bestia.zone.util.ConcurrentBuffer
import org.springframework.stereotype.Component

@Component
class InECSMessageBuffer : ConcurrentBuffer<InECSMessage>()

