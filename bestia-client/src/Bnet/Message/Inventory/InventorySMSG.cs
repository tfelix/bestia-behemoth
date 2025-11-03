using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  /// <summary>
  /// Godot-friendly wrapper for Inventory protobuf data containing player items
  /// </summary>
  [GlobalClass]
  public partial class InventorySMSG : ISMSG
  {
    [Export]
    public ulong EntityId { get; set; }

    [Export]
    public Godot.Collections.Array<PlayerItem> Items { get; set; } = [];

    /// <summary>
    /// Creates an InventorySMSG message from protobuf data
    /// </summary>
    /// <param name="protoInventory">The protobuf InventorySMSG object</param>
    /// <returns>Godot-friendly InventorySMSG object</returns>
    public static InventorySMSG FromProto(global::Bnet.InventorySMSG protoInventory)
    {
      var inventory = new InventorySMSG
      {
        EntityId = protoInventory.EntityId
      };

      // Convert protobuf PlayerItem list to Godot array
      foreach (var protoItem in protoInventory.Items)
      {
        inventory.Items.Add(PlayerItem.FromProto(protoItem));
      }

      return inventory;
    }
  }
}
