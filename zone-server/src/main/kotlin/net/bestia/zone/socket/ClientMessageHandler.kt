package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import net.bestia.zone.account.AccountConnectedEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.account.authentication.AuthenticationProcessor
import net.bestia.zone.message.MessageEnvelopeReceivedEvent
import net.bestia.bnet.proto.AuthenticationSuccessProto
import net.bestia.bnet.proto.DisconnectedProto
import net.bestia.bnet.proto.EnvelopeProto
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ClientMessageHandler(
  private val handlerCtx: ClientMessageHandlerContext,
  private val clock: Clock = Clock.systemUTC()
) : SimpleChannelInboundHandler<EnvelopeProto.Envelope>() {

  private val connectionUuid = UUID.randomUUID().toString()
  private val connectedAt = clock.instant()
  private var authTimeoutTask: ScheduledFuture<*>? = null
  private var accountId: Long? = null

  /**
   * Called when a new connection is opened.
   */
  override fun channelActive(ctx: ChannelHandlerContext) {
    // Schedule auth timeout for this specific connection
    authTimeoutTask = ctx.executor().schedule({
      if (accountId == null) {
        LOG.warn {
          "Client $connectionUuid - ${
            ctx.channel().remoteAddress()
          } authentication timeout after ${handlerCtx.socketConfig.authenticationTimeoutSeconds} seconds"
        }
        sendDisconnectMessageAndClose(ctx.channel())
      }
    }, handlerCtx.socketConfig.authenticationTimeoutSeconds, TimeUnit.SECONDS)

    LOG.debug { "Client $connectionUuid - ${ctx.channel().remoteAddress()} connected" }
  }

  /**
   * Called when a channel is closed or disconnected either by the client or because the
   * application explicitly closes the channel.
   */
  override fun channelInactive(ctx: ChannelHandlerContext) {
    authTimeoutTask?.cancel(false)

    LOG.debug { "Client $connectionUuid - ${ctx.channel().remoteAddress()} (player: $accountId) disconnected" }

    accountId?.let { id ->
      handlerCtx.channelRegistry.unregisterChannel(id)
      handlerCtx.applicationEventPublisher.publishEvent(AccountDisconnectedEvent(this, id))
    }
  }

  override fun channelRead0(ctx: ChannelHandlerContext, msg: EnvelopeProto.Envelope) {
    val currentAccountId = accountId
    if (currentAccountId != null) {
      LOG.debug { "RX player $currentAccountId: $msg" }
      val messageRx = MessageEnvelopeReceivedEvent(this, currentAccountId, msg)
      handlerCtx.applicationEventPublisher.publishEvent(messageRx)
    } else {
      LOG.debug { "RX client $connectionUuid - ${ctx.channel().remoteAddress()}: $msg" }
      authenticateChannel(ctx, msg)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    LOG.error(cause) { "Connection error" }

    // Cancel auth timeout task
    authTimeoutTask?.cancel(false)

    accountId?.let { id ->
      handlerCtx.channelRegistry.unregisterChannel(id)
      handlerCtx.applicationEventPublisher.publishEvent(AccountDisconnectedEvent(this, id))
    }

    ctx.close()
  }

  private fun authenticateChannel(
    ctx: ChannelHandlerContext,
    msg: EnvelopeProto.Envelope,
  ) {
    when (val result = handlerCtx.authProcessor.authenticate(msg)) {
      is AuthenticationProcessor.AuthenticationFailed -> handleAuthenticationFailed(ctx)
      is AuthenticationProcessor.AuthenticationSuccess -> handleAuthenticationSuccess(ctx, result)
    }
  }

  private fun handleAuthenticationSuccess(
    ctx: ChannelHandlerContext,
    result: AuthenticationProcessor.AuthenticationSuccess,
  ) {
    // Cancel auth timeout since authentication was successful
    authTimeoutTask?.cancel(false)

    accountId = result.accountId
    handlerCtx.channelRegistry.registerChannel(result.accountId, ctx.channel())

    LOG.debug { "Client ${ctx.channel().remoteAddress()} authed as player ${result.accountId}" }

    val authSuccess = AuthenticationSuccessProto.AuthenticationSuccess
      .newBuilder()
      .setServerVersion("behemoth/${handlerCtx.version}")
    // TODO Send the available permissions to the client
    // .setPermissions(AuthenticationSuccessProto.ClientPermissions.REGULAR)

    val envelope = EnvelopeProto.Envelope.newBuilder()
      .setAuthenticationSuccess(authSuccess)
      .build()

    ctx.channel().writeAndFlush(envelope)

    handlerCtx.applicationEventPublisher.publishEvent(
      AccountConnectedEvent(
        source = this,
        accountId = result.accountId,
      )
    )
  }

  private fun handleAuthenticationFailed(ctx: ChannelHandlerContext) {
    LOG.warn { "Client $connectionUuid - ${ctx.channel().remoteAddress()} auth handshake failed" }
    sendDisconnectMessageAndClose(ctx.channel())
  }

  private fun sendDisconnectMessageAndClose(channel: io.netty.channel.Channel) {
    try {
      if (channel.isActive) {
        val disconnected = DisconnectedProto.Disconnected
          .newBuilder()
          .setReason("AUTH_FAILED")

        val envelope = EnvelopeProto.Envelope.newBuilder()
          .setDisconnected(disconnected)
          .build()

        channel.writeAndFlush(envelope).addListener { channel.close() }
      }
    } catch (e: Exception) {
      LOG.warn(e) { "Failed to send disconnect message, forcing close" }
      channel.close()
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
