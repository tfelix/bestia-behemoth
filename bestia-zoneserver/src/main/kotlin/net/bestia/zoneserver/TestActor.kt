package net.bestia.zoneserver

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive


class TestActor(
    context: ActorContext<Greet>
) : AbstractBehavior<TestActor.Greet>(context) {

    data class Greet(
        val whom: String,
        val replyTo: ActorRef<Greeted>
    )

    data class Greeted(
        val whom: String,
        val from: ActorRef<Greet>
    )

    fun create(): Behavior<Greet?>? {
        return Behaviors.setup(::TestActor)
    }

    override fun createReceive(): Receive<Greet> {
        return newReceiveBuilder().onMessage(Greet::class.java, this::onGreet).build()
    }

    private fun onGreet(command: Greet): Behavior<Greet> {
        context.log.info("Hello {}!", command.whom)
        command.replyTo.tell(Greeted(command.whom, context.self))
        return this
    }
}