package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import net.bestia.zone.account.AccountConnectedEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.account.authentication.AuthenticationProcessor
import net.bestia.zone.message.MessageEnvelopeReceivedEvent
import net.bestia.zone.message.MessageHandlingFailedException
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

    releaseAccount(ctx)
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
    // MessageHandlingFailedException already logged the original exception with this same code;
    // anything else reaching here (codec errors, raw IO failures, ...) hasn't been logged yet, so
    // mint a fresh code to still give the client-facing error something to reference in a report.
    val errorCode = (cause as? MessageHandlingFailedException)?.errorCode ?: UUID.randomUUID().toString()
    LOG.error(cause) { "Connection error [errorCode=$errorCode], closing connection" }

    // Cancel auth timeout task
    authTimeoutTask?.cancel(false)

    releaseAccount(ctx)

    // This is the connection's HTTP-500 equivalent: don't leak internals to the client, but keep
    // the errorCode traceable back to the server log entry above for debugging.
    sendDisconnectMessageAndClose(ctx.channel(), reason = "INTERNAL_SERVER_ERROR:$errorCode")
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

    // Gate: refuse logins until the zone has finished loading (entity reload, world gen, ...).
    if (!handlerCtx.zoneReadinessService.isReady()) {
      LOG.info { "Zone not ready yet, rejecting login for account ${result.accountId}" }
      sendDisconnectMessageAndClose(ctx.channel(), reason = "SERVER_NOT_READY")
      return
    }

    accountId = result.accountId
    takeOverAccount(ctx, result.accountId)

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
        authorities = result.authorities,
      )
    )
  }

  /**
   * Claims the account for this connection, terminating whatever connection already held it.
   *
   * An account must only ever have one live connection: the session state and the master entity in the
   * world are keyed by account, so two sockets acting as one account would fight over the same entity.
   * The newcomer wins rather than being refused, because the common cause is a client whose old socket
   * is already dead in a way the server has not noticed yet — refusing would lock the player out until
   * the stale connection timed out.
   *
   * The displaced connection's teardown is published from here, synchronously, rather than left to its
   * own [channelInactive]: its session must be gone before [AccountConnectedEvent] below announces
   * ours, and `channelInactive` runs later on a different event loop with no ordering guarantee at all.
   */
  private fun takeOverAccount(ctx: ChannelHandlerContext, accountId: Long) {
    val displaced = handlerCtx.channelRegistry.registerChannel(accountId, ctx.channel())

    if (displaced == null || displaced === ctx.channel()) {
      return
    }

    LOG.info {
      "Account $accountId logged in from ${ctx.channel().remoteAddress()} while already connected from " +
              "${displaced.remoteAddress()} - terminating the older connection"
    }

    handlerCtx.applicationEventPublisher.publishEvent(AccountDisconnectedEvent(this, accountId))
    sendDisconnectMessageAndClose(displaced, reason = "OTHER_CONNECTION")
  }

  /**
   * Runs the account teardown for this connection, if it still owns the account.
   *
   * A connection that a newer login already displaced (see [takeOverAccount]) must stay silent here:
   * its teardown has already been published on its behalf, and publishing a second
   * [AccountDisconnectedEvent] would deactivate the session of the connection that replaced it.
   */
  private fun releaseAccount(ctx: ChannelHandlerContext) {
    val id = accountId ?: return

    if (handlerCtx.channelRegistry.unregisterChannel(id, ctx.channel())) {
      handlerCtx.applicationEventPublisher.publishEvent(AccountDisconnectedEvent(this, id))
    }
  }

  private fun handleAuthenticationFailed(ctx: ChannelHandlerContext) {
    LOG.warn { "Client $connectionUuid - ${ctx.channel().remoteAddress()} auth handshake failed" }
    sendDisconnectMessageAndClose(ctx.channel())
  }

  private fun sendDisconnectMessageAndClose(channel: io.netty.channel.Channel, reason: String = "AUTH_FAILED") {
    try {
      if (channel.isActive) {
        val disconnected = DisconnectedProto.Disconnected
          .newBuilder()
          .setReason(reason)

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
