using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Stall
{
  /// <summary>Buys a whole offer off a stall at its price.</summary>
  public partial class StallBuyCMSG : ICMSG
  {
    public ulong StallEntityId { get; set; }

    public ulong ListingId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        StallBuy = new global::Bnet.StallBuyCMSG { StallEntityId = StallEntityId, ListingId = ListingId }
      };
    }
  }
}
