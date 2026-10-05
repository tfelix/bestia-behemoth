using System.Collections.Generic;
using System.Linq;
using Bnet;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Which envelope cases the server sends, read off the proto descriptor the way zone-server's
  /// <c>BnetMessageProcessorAdapter</c> reads the cases a client sends: a message type named <c>…SMSG</c>, plus a
  /// few named otherwise.
  /// </summary>
  public static class EnvelopeCases
  {
    private static readonly HashSet<Envelope.MessageOneofCase> SentUnderOtherNames = new()
    {
      Envelope.MessageOneofCase.OperationSuccess,
      Envelope.MessageOneofCase.OperationError,
      Envelope.MessageOneofCase.Disconnected,
      Envelope.MessageOneofCase.AuthenticationSuccess,
      Envelope.MessageOneofCase.Pong,
      Envelope.MessageOneofCase.Master,
    };

    /// <summary>Sent by the server, and deliberately not acted on by this client yet.</summary>
    public static readonly IReadOnlyCollection<Envelope.MessageOneofCase> NotUsedYet =
      new HashSet<Envelope.MessageOneofCase> { Envelope.MessageOneofCase.BestiaAiConfig };

    public static IEnumerable<Envelope.MessageOneofCase> SentByServer()
    {
      return Envelope.Descriptor.Fields.InFieldNumberOrder()
        .Where(f => f.MessageType.Name.EndsWith("SMSG") || SentUnderOtherNames.Contains((Envelope.MessageOneofCase)f.FieldNumber))
        .Select(f => (Envelope.MessageOneofCase)f.FieldNumber);
    }

    /// <summary>
    /// What the server sends that neither <paramref name="routed"/> nor <see cref="NotUsedYet"/> covers.
    /// <c>Disconnected</c> and <c>StateBatch</c> never need a route: <c>BnetSocket</c> acts on them itself.
    /// </summary>
    public static IEnumerable<Envelope.MessageOneofCase> Unrouted(IEnumerable<Envelope.MessageOneofCase> routed)
    {
      return SentByServer()
        .Except(routed)
        .Except(NotUsedYet)
        .Where(c => c != Envelope.MessageOneofCase.Disconnected && c != Envelope.MessageOneofCase.StateBatch);
    }
  }
}
