using BestiaBehemothClient.Bnet.Message;
using Bnet;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The client routes every message the server sends. A new server message without a route would otherwise
  /// only show up as an error line the first time a player received it.
  /// </summary>
  public class EnvelopeDecoderTest
  {
    [Fact]
    public void Every_case_the_server_sends_has_a_route()
    {
      Assert.Empty(EnvelopeCases.Unrouted(EnvelopeDecoder.RoutedCases));
    }

    [Fact]
    public void Only_what_the_server_sends_counts_as_sent_by_the_server()
    {
      var sent = EnvelopeCases.SentByServer();

      Assert.Contains(Envelope.MessageOneofCase.PartyInfo, sent);
      Assert.Contains(Envelope.MessageOneofCase.StateBatch, sent);
      Assert.DoesNotContain(Envelope.MessageOneofCase.CreateParty, sent);
      Assert.DoesNotContain(Envelope.MessageOneofCase.Authentication, sent);
    }
  }
}
