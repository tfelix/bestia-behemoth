package net.bestia.entity.component

@ActorSync("net.bestia.zoneserver.actor.entity.component.TestComponentActor")
class TestComponent(
        componentId: Long,
        val content: String
) : Component(componentId)