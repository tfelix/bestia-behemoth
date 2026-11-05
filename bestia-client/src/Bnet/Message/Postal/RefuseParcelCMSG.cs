using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Sends a parcel back to its sender, at no cost.</summary>
  public partial class RefuseParcelCMSG : ICMSG
  {
    public ulong PostmasterEntityId { get; set; }
    public ulong ParcelId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RefuseParcel = new global::Bnet.RefuseParcelCMSG { PostmasterEntityId = PostmasterEntityId, ParcelId = ParcelId }
      };
    }
  }
}
