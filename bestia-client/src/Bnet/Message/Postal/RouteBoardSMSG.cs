using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>The parcels that wait at this office for a courier, and what the courier just delivered here.</summary>
  [GlobalClass]
  public partial class RouteBoardSMSG : ISMSG
  {
    [Export] public ulong PostmasterEntityId { get; set; }
    [Export] public Godot.Collections.Array<RouteBoardEntry> Parcels { get; set; } = [];

    /// <summary>Zero unless the courier delivered sacks here as the window opened.</summary>
    [Export] public int DeliveredParcels { get; set; }
    [Export] public long DeliveredPay { get; set; }

    public static RouteBoardSMSG FromProto(global::Bnet.RouteBoardSMSG proto)
    {
      var parcels = new Godot.Collections.Array<RouteBoardEntry>();
      foreach (var parcel in proto.Parcels)
      {
        parcels.Add(RouteBoardEntry.FromProto(parcel));
      }

      return new RouteBoardSMSG
      {
        PostmasterEntityId = proto.PostmasterEntityId,
        Parcels = parcels,
        DeliveredParcels = proto.DeliveredParcels,
        DeliveredPay = proto.DeliveredPay
      };
    }
  }
}
