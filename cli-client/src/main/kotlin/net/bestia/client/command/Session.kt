package net.bestia.client.command

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.client.BestiaSocketClient
import net.bestia.client.CLI
import net.bestia.client.CLIException
import net.bestia.client.formatter.ChatFormatter
import java.io.Closeable

typealias EnvelopeHandlerFn = (envelope: EnvelopeProto.Envelope) -> Unit

class Session : Closeable {

  data class Data(
    var pos: Position = Position()
  ) {
    data class Position(
      var x: Long = 0,
      var y: Long = 0,
      var z: Long = 0
    )
  }

  var client: BestiaSocketClient? = null

  val data = Data()

  private var envelopeRedirectionFn: EnvelopeHandlerFn? = null

  private val formatter = listOf(
    ChatFormatter()
  )

  private val cli = CLI(
    commands = listOf(
      ConnectCommand(this),
      AuthCommand(this),
      PingCommand(this),
      ChatCommand(this),
      StartCommand(this),
      ListCommand(this),
      SelectCommand(this)
    )
  )

  fun print(text: String) {
    cli.printText(text)
  }

  fun start() {
    cli.start()
  }

  fun receiveEnvelope(envelope: EnvelopeProto.Envelope) {
    if (envelopeRedirectionFn == null) {
      val text = formatter
        .firstOrNull { it.canHandle(envelope) }
        ?.format(envelope)
        ?: envelope.toString()

      cli.printText(text)
    } else {
      envelopeRedirectionFn?.let { fn ->
        fn(envelope)
      }
    }
  }

  fun sendEnvelope(envelope: EnvelopeProto.Envelope) {
    return client?.sendEnvelope(envelope)
      ?: throw CLIException("Client not connected, please call 'connect' first")
  }

  override fun close() {
    client?.close()
  }
}
