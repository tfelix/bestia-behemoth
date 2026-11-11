using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Stall
{
  /// <summary>The seller takes an offer back into their bag.</summary>
  public partial class StallUnlistCMSG : ICMSG
  {
    public ulong StallEntityId { get; set; }

    public ulong ListingId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        StallUnlist = new global::Bnet.StallUnlistCMSG { StallEntityId = StallEntityId, ListingId = ListingId }
      };
    }
  }
}
