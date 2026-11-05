using BestiaBehemothClient.Bnet.Message.Entity;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>One pile or copy in a parcel, with the copy's own state for its tooltip.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class ParcelGoodsEntry : GodotObject
  {
    [Export] public ulong ItemId { get; set; }
    [Export] public int Amount { get; set; }
    [Export] public int ItemLevel { get; set; }
    [Export] public int UpgradeLevel { get; set; }
    [Export] public int Durability { get; set; }
    [Export] public int MaxDurability { get; set; }
    [Export] public int Slots { get; set; }
    [Export] public Godot.Collections.Array<ItemStatValue> RolledStats { get; set; } = [];

    public static ParcelGoodsEntry FromProto(global::Bnet.ParcelGoods proto)
    {
      return new ParcelGoodsEntry
      {
        ItemId = proto.ItemId,
        Amount = proto.Amount,
        ItemLevel = proto.ItemLevel,
        UpgradeLevel = proto.UpgradeLevel,
        Durability = proto.Durability,
        MaxDurability = proto.MaxDurability,
        Slots = proto.Slots,
        RolledStats = ItemStatValue.FromProto(proto.RolledStats)
      };
    }
  }
}
