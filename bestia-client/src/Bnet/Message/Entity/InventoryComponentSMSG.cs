using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Inventory component message from server containing entity inventory information.
  /// </summary>
  [GlobalClass]
  public partial class InventoryComponentSMSG : EntitySMSG
  {
    [Export] public Godot.Collections.Array<InventoryItem> Items { get; set; } = [];

    public InventoryComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create InventoryComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoInventory">The protobuf InventoryComponentSMSG message from the server</param>
    /// <returns>A new InventoryComponentSMSG instance</returns>
    public static InventoryComponentSMSG FromProto(global::Bnet.InventoryComponentSMSG protoInventory)
    {
      var inventory = new InventoryComponentSMSG()
      {
        EntityId = protoInventory.EntityId
      };

      // Convert protobuf InventoryItem list to Godot array
      foreach (var protoItem in protoInventory.Items)
      {
        inventory.Items.Add(InventoryItem.FromProto(protoItem));
      }

      return inventory;
    }

    public override string ToString()
    {
      return $"InventoryComponentSMSG(EntityId={EntityId}, ItemCount={Items.Count})";
    }
  }

  /// <summary>
  /// Represents a single item in an entity's inventory
  /// </summary>
  [GlobalClass]
  public partial class InventoryItem : GodotObject
  {
    [Export] public uint ItemId { get; set; }
    [Export] public ulong UniqueId { get; set; }
    [Export] public uint Amount { get; set; }
    [Export] public bool Equipped { get; set; }

    /// <summary>
    /// Wear on the backing item instance. Both zero for a plain stack and for gear with no durability
    /// at all, so a wear bar is drawn only when <see cref="MaxDurability"/> is greater than zero.
    /// </summary>
    [Export] public uint Durability { get; set; }
    [Export] public uint MaxDurability { get; set; }

    /// <summary>
    /// Rune slots cut into the backing instance by Item Customization. Nothing can fill one yet.
    /// </summary>
    [Export] public uint Slots { get; set; }

    /// <summary>
    /// Creates an InventoryItem from protobuf data
    /// </summary>
    public static InventoryItem FromProto(global::Bnet.InventoryItem protoItem)
    {
      return new InventoryItem
      {
        ItemId = protoItem.ItemId,
        UniqueId = protoItem.UniqueId,
        Amount = protoItem.Amount,
        Equipped = protoItem.Equipped,
        Durability = protoItem.Durability,
        MaxDurability = protoItem.MaxDurability,
        Slots = protoItem.Slots
      };
    }
  }
}

