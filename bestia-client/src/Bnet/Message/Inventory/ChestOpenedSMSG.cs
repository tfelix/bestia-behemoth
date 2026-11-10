using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  /// <summary>
  /// The player opened one of their chests. Its goods follow as the chest entity's
  /// <c>InventoryComponentSMSG</c>, like an own bestia's bag.
  /// </summary>
  [GlobalClass]
  public partial class ChestOpenedSMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }

    [Export] public int Slots { get; set; }

    public static ChestOpenedSMSG FromProto(global::Bnet.ChestOpenedSMSG proto)
    {
      return new ChestOpenedSMSG { EntityId = proto.EntityId, Slots = (int)proto.Slots };
    }
  }
}
