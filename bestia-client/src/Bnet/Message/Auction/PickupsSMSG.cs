using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>The Won &amp; Bought window: what waits for this master at linked houses.</summary>
  [GlobalClass]
  public partial class PickupsSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<PickupEntry> Pickups { get; set; } = [];

    public static PickupsSMSG FromProto(global::Bnet.PickupsSMSG proto)
    {
      var pickups = new Godot.Collections.Array<PickupEntry>();
      foreach (var pickup in proto.Pickups)
      {
        pickups.Add(PickupEntry.FromProto(pickup));
      }

      return new PickupsSMSG { Pickups = pickups };
    }
  }
}
