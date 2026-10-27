using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// What an entity visibly wears, sent to everyone who sees it. The owner's full equipment details
  /// arrive separately in <see cref="EquipmentComponentSMSG"/>.
  /// </summary>
  [GlobalClass]
  public partial class WornGearComponentSMSG : EntitySMSG
  {
    [Export] public Godot.Collections.Array<int> ItemIds { get; set; } = [];

    /// <summary>The item worn in the light-source slot burns. Decided by the server, which burns it down.</summary>
    [Export] public bool LightLit { get; set; }

    public static WornGearComponentSMSG FromProto(ulong entityId, global::Bnet.WornGearComponentSMSG protoWornGear)
    {
      var wornGear = new WornGearComponentSMSG { EntityId = entityId, LightLit = protoWornGear.LightLit };
      foreach (var item in protoWornGear.Items)
      {
        wornGear.ItemIds.Add((int)item.ItemId);
      }
      return wornGear;
    }

    public override string ToString()
    {
      return $"WornGearComponentSMSG(EntityId={EntityId}, ItemIds=[{string.Join(", ", ItemIds)}], LightLit={LightLit})";
    }
  }
}
