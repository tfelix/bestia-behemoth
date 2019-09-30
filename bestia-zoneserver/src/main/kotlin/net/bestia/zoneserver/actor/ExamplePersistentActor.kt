package net.bestia.zoneserver.actor

import akka.persistence.AbstractPersistentActor
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SaveSnapshotSuccess
import net.bestia.zoneserver.entity.component.Component

/**
 * Snapshot every 30s
 *
 */
@Actor
class ExamplePersistentActor(
    component: Component
) : AbstractPersistentActor() {

  private var component = component
    set(value) {
      persist(value) { e: Component ->
        context.system.eventStream.publish(e)
        if (lastSequenceNr() % SNAP_SHOT_INTERVAL == 0L && lastSequenceNr() != 0L) {
          // IMPORTANT: create a copy of snapshot because ExampleState is mutable
          // saveSnapshot(state.copy())
        }
        field = value
      }
    }

  override fun persistenceId(): String {
    return ""
  }

  override fun createReceiveRecover(): Receive {
    return receiveBuilder()
        .match(Component::class.java, this::recoverComponent)
        .match(SaveSnapshotSuccess::class.java, this::snapshotSuccess)
        .match(SaveSnapshotFailure::class.java, this::snapshotFailure)
        .build()
  }

  private fun snapshotFailure(recoveredComponent: SaveSnapshotFailure) {
    println(recoveredComponent)
  }

  private fun snapshotSuccess(recoveredComponent: SaveSnapshotSuccess) {
    println(recoveredComponent)
  }

  private fun recoverComponent(recoveredComponent: Component) {
    println(recoveredComponent)
  }

  override fun createReceive(): Receive {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  companion object {
    private const val SNAP_SHOT_INTERVAL = 100
  }
}