using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Asks for the Won &amp; Bought window: everything waiting at linked houses.</summary>
  public partial class RequestPickupsCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope { RequestPickups = new global::Bnet.RequestPickupsCMSG() };
    }
  }
}
