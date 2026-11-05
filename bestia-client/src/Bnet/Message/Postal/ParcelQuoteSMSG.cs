using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>What a parcel costs and when it arrives at the latest.</summary>
  [GlobalClass]
  public partial class ParcelQuoteSMSG : ISMSG
  {
    [Export] public string RecipientName { get; set; } = "";
    [Export] public string DestinationName { get; set; } = "";
    [Export] public int WeightUnits { get; set; }
    [Export] public long Fee { get; set; }
    [Export] public long LatestArrivalEpochSeconds { get; set; }

    public static ParcelQuoteSMSG FromProto(global::Bnet.ParcelQuoteSMSG proto)
    {
      return new ParcelQuoteSMSG
      {
        RecipientName = proto.RecipientName,
        DestinationName = proto.DestinationName,
        WeightUnits = proto.WeightUnits,
        Fee = proto.Fee,
        LatestArrivalEpochSeconds = proto.LatestArrivalEpochSeconds
      };
    }
  }
}
