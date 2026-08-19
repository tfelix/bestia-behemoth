using Godot;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>
  /// One line of a trade offer. Mirrors <c>InventoryItem</c> field for field, plus the id of the line
  /// itself, so the window draws it with the same item row the inventory grid already uses.
  /// </summary>
  /// <remarks>
  /// Its own file rather than a companion inside <see cref="TradeStateSMSG"/>, because Godot registers
  /// exactly one <c>[GlobalClass]</c> per C# file - the one named after the file. A second class in the
  /// same file compiles and is invisible to GDScript.
  /// </remarks>
  [GlobalClass]
  public partial class TradeOfferItem : GodotObject
  {
    /// <summary>
    /// Names this line for a retraction. Two lines of the same template are distinct offers, so this is
    /// what a drag-out has to send back - never the item id.
    /// </summary>
    [Export] public ulong OfferSlotId { get; set; }

    [Export] public uint ItemId { get; set; }

    /// <summary>The backing instance, or 0 for a plain stack.</summary>
    [Export] public ulong UniqueId { get; set; }

    [Export] public uint Amount { get; set; }

    /// <summary>Both 0 for a plain stack and for gear nobody gave a durability.</summary>
    [Export] public uint Durability { get; set; }

    [Export] public uint MaxDurability { get; set; }

    [Export] public uint Slots { get; set; }

    [Export] public uint UpgradeLevel { get; set; }

    public static TradeOfferItem FromProto(global::Bnet.TradeOfferItem proto)
    {
      return new TradeOfferItem
      {
        OfferSlotId = proto.OfferSlotId,
        ItemId = proto.ItemId,
        UniqueId = proto.UniqueId,
        Amount = proto.Amount,
        Durability = proto.Durability,
        MaxDurability = proto.MaxDurability,
        Slots = proto.Slots,
        UpgradeLevel = proto.UpgradeLevel
      };
    }
  }
}
