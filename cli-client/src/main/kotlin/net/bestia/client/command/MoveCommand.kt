package net.bestia.client.command

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.MoveActiveEntityProto
import net.bestia.bnet.proto.Vec3OuterClass.Vec3

class MoveCommand(
  private val session: Session
) : CliCommand {
  override val name = "move"
  override val usage = "move <x> <y> <z>"
  override val description = "Move current player entity on the map"

  override fun execute(tokens: List<String>) {
    if (tokens.size != 4 || tokens[0].lowercase() != "move") {
      session.print("Usage: move <x> <y> <z>")
      return
    }

    val target = try {
      Session.Data.Position(tokens[1].toLong(), tokens[2].toLong(), tokens[3].toLong())
    } catch (e: NumberFormatException) {
      session.print("Invalid coordinates. Use integers.")
      return
    }

    val path = generateManhattanPath(session.data.pos, target)

    val pathBnet = path.map { p ->
      Vec3.newBuilder()
        .setX(p.x)
        .setY(p.y)
        .setZ(p.z)
        .build()
    }

    val moveActive = MoveActiveEntityProto.MoveActiveEntity
      .newBuilder()
      .addAllPath(pathBnet)
      .build()

    val envelope = EnvelopeProto.Envelope.newBuilder()
      .setMoveActiveEntity(moveActive)
      .build()

    session.sendEnvelope(envelope)
  }

  private fun generateManhattanPath(
    start: Session.Data.Position,
    end: Session.Data.Position
  ): List<Session.Data.Position> {
    val path = mutableListOf<Session.Data.Position>()
    var x = start.x
    var y = start.y
    var z = start.z

    while (x != end.x) {
      x += if (x < end.x) 1 else -1
      path.add(Session.Data.Position(x, y, z))
    }
    while (y != end.y) {
      y += if (y < end.y) 1 else -1
      path.add(Session.Data.Position(x, y, z))
    }
    while (z != end.z) {
      z += if (z < end.z) 1 else -1
      path.add(Session.Data.Position(x, y, z))
    }

    return path
  }
}