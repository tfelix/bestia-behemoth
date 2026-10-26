using BestiaBehemothClient.Bnet.Message.Entity;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>One listing on the board, with the copy's own state so a tooltip shows exactly what is sold.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class AuctionListingEntry : GodotObject
  {
    [Export] public ulong ListingId { get; set; }
    [Export] public ulong ItemId { get; set; }
    [Export] public int Amount { get; set; }

    /// <summary>Coins for the whole lot.</summary>
    [Export] public long Price { get; set; }

    [Export] public string SellerName { get; set; } = "";

    /// <summary>Where it was put up; linked houses share listings, so this may be another town.</summary>
    [Export] public string TownName { get; set; } = "";

    [Export] public long ExpiresAtEpochSeconds { get; set; }
    [Export] public int ItemLevel { get; set; }
    [Export] public int UpgradeLevel { get; set; }
    [Export] public int Durability { get; set; }
    [Export] public int MaxDurability { get; set; }
    [Export] public int Slots { get; set; }
    [Export] public Godot.Collections.Array<ItemStatValue> RolledStats { get; set; } = [];

    public static AuctionListingEntry FromProto(global::Bnet.AuctionListing proto)
    {
      return new AuctionListingEntry
      {
        ListingId = proto.ListingId,
        ItemId = proto.ItemId,
        Amount = proto.Amount,
        Price = proto.Price,
        SellerName = proto.SellerName,
        TownName = proto.TownName,
        ExpiresAtEpochSeconds = proto.ExpiresAtEpochSeconds,
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
