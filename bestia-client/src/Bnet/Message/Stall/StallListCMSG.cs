using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Stall
{
  /// <summary>The seller puts goods from their bag on their own stall.</summary>
  public partial class StallListCMSG : ICMSG
  {
    public ulong StallEntityId { get; set; }

    public ulong PlayerItemId { get; set; }

    public int Amount { get; set; }

    /// <summary>Coins for the whole lot.</summary>
    public long Price { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        StallList = new global::Bnet.StallListCMSG
        {
          StallEntityId = StallEntityId,
          PlayerItemId = PlayerItemId,
          Amount = Amount,
          Price = Price
        }
      };
    }
  }
}
