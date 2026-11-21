using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  /// <summary>
  /// The player opened a storage that counts slots, such as one of their chests. Its goods follow as the storage
  /// entity's <c>InventoryComponentSMSG</c>, like an own bestia's bag. <c>Kind</c> is the proto's StorageKind.
  /// </summary>
  [GlobalClass]
  public partial class StorageOpenedSMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }

    [Export] public int Slots { get; set; }

    [Export] public int Kind { get; set; }

    public static StorageOpenedSMSG FromProto(global::Bnet.StorageOpenedSMSG proto)
    {
      return new StorageOpenedSMSG { EntityId = proto.EntityId, Slots = (int)proto.Slots, Kind = (int)proto.Kind };
    }
  }
}
