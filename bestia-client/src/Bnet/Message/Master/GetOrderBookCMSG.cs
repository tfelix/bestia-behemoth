using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Asks for the selected master's order presets and named places.</summary>
  public partial class GetOrderBookCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope { GetOrderBook = new global::Bnet.GetOrderBookCMSG() };
    }
  }
}
