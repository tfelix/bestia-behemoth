using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Takes a parcel's goods into the bag.</summary>
  public partial class CollectParcelCMSG : ICMSG
  {
    public ulong PostmasterEntityId { get; set; }
    public ulong ParcelId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CollectParcel = new global::Bnet.CollectParcelCMSG { PostmasterEntityId = PostmasterEntityId, ParcelId = ParcelId }
      };
    }
  }
}
