using Godot;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>One line of a shop basket. Its own file because Godot registers one <c>[GlobalClass]</c> per file.</summary>
  [GlobalClass]
  public partial class ShopLine : GodotObject
  {
    [Export] public ulong ItemId { get; set; }

    [Export] public int Amount { get; set; }

    /// <summary>True when we are handing goods over, false when taking them.</summary>
    [Export] public bool Selling { get; set; }

    public global::Bnet.ShopLine ToProto()
    {
      return new global::Bnet.ShopLine { ItemId = ItemId, Amount = Amount, Selling = Selling };
    }
  }
}
