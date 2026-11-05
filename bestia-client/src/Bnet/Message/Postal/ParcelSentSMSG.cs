using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>The parcel is in the sack. The goods and the fee have left the bag.</summary>
  [GlobalClass]
  public partial class ParcelSentSMSG : ISMSG
  {
    [Export] public string RecipientName { get; set; } = "";
    [Export] public string DestinationName { get; set; } = "";
    [Export] public long LatestArrivalEpochSeconds { get; set; }

    public static ParcelSentSMSG FromProto(global::Bnet.ParcelSentSMSG proto)
    {
      return new ParcelSentSMSG
      {
        RecipientName = proto.RecipientName,
        DestinationName = proto.DestinationName,
        LatestArrivalEpochSeconds = proto.LatestArrivalEpochSeconds
      };
    }
  }
}
