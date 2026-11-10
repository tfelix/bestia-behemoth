using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Pays the parcel fee and has a waiting purchase sent home.</summary>
  public partial class PostPickupCMSG : ICMSG
  {
    public ulong PickupId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        PostPickup = new global::Bnet.PostPickupCMSG
        {
          PickupId = PickupId
        }
      };
    }
  }
}
