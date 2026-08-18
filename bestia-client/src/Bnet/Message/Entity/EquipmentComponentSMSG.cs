using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Equipment component message from server: what an entity currently wears, one entry per
  /// occupied slot. Carries no "available slots" mask - that is static content the client reads
  /// from its own bestia DB.
  /// </summary>
  [GlobalClass]
  public partial class EquipmentComponentSMSG : EntitySMSG
  {
    [Export] public Godot.Collections.Array<EquippedItem> Items { get; set; } = [];

    public EquipmentComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create EquipmentComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoEquipment">The protobuf EquipmentComponentSMSG message from the server</param>
    /// <returns>A new EquipmentComponentSMSG instance</returns>
    public static EquipmentComponentSMSG FromProto(global::Bnet.EquipmentComponentSMSG protoEquipment)
    {
      var equipment = new EquipmentComponentSMSG()
      {
        EntityId = protoEquipment.EntityId
      };

      foreach (var protoItem in protoEquipment.Items)
      {
        equipment.Items.Add(EquippedItem.FromProto(protoItem));
      }

      return equipment;
    }

    public override string ToString()
    {
      return $"EquipmentComponentSMSG(EntityId={EntityId}, ItemCount={Items.Count})";
    }
  }

  /// <summary>
  /// Represents a single worn item. Slot is an EquipmentSlot ordinal.
  /// </summary>
  [GlobalClass]
  public partial class EquippedItem : GodotObject
  {
    [Export] public uint Slot { get; set; }
    [Export] public uint ItemId { get; set; }
    [Export] public ulong UniqueId { get; set; }

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
    /// Upgrades on the backing instance, the same number the inventory draws as a +N suffix. Carried here so
    /// taking a piece off does not change what it says it is.
    /// </summary>
    [Export] public uint UpgradeLevel { get; set; }

    public static EquippedItem FromProto(global::Bnet.EquippedItem protoItem)
    {
      return new EquippedItem
      {
        Slot = protoItem.Slot,
        ItemId = protoItem.ItemId,
        UniqueId = protoItem.UniqueId,
        Durability = protoItem.Durability,
        MaxDurability = protoItem.MaxDurability,
        Slots = protoItem.Slots,
        UpgradeLevel = protoItem.UpgradeLevel
      };
    }
  }
}
