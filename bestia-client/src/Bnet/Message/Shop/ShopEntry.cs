using Godot;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>
  /// One line of a shop window: what it is, how many are to be had, and the two prices.
  /// </summary>
  /// <remarks>
  /// Its own file rather than a companion inside <see cref="ShopOfferSMSG"/>, because Godot registers
  /// exactly one <c>[GlobalClass]</c> per C# file - the one named after the file. A second class in the
  /// same file compiles and is invisible to GDScript. Same reason as <c>TradeOfferItem</c>.
  /// </remarks>
  [GlobalClass]
  public partial class ShopEntry : GodotObject
  {
    [Export] public ulong ItemId { get; set; }

    /// <summary>Units the town will part with - what is on the shelves less what the locals keep back.</summary>
    [Export] public int Offered { get; set; }

    /// <summary>Coins for one unit. The gap between the two is the spread, and it is why a round trip loses.</summary>
    [Export] public long BuyPrice { get; set; }

    [Export] public long SellPrice { get; set; }

    public static ShopEntry FromProto(global::Bnet.ShopEntry proto)
    {
      return new ShopEntry
      {
        ItemId = proto.ItemId,
        Offered = proto.Offered,
        BuyPrice = proto.BuyPrice,
        SellPrice = proto.SellPrice
      };
    }
  }
}
