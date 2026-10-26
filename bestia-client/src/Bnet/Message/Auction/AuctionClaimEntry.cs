using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Coins or goods the market owes, waiting in the Collect tab.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class AuctionClaimEntry : GodotObject
  {
    [Export] public ulong ClaimId { get; set; }

    /// <summary>True for the proceeds of a sale, false for goods that came back unsold.</summary>
    [Export] public bool Sold { get; set; }

    [Export] public long Coins { get; set; }
    [Export] public ulong ItemId { get; set; }
    [Export] public int Amount { get; set; }

    public static AuctionClaimEntry FromProto(global::Bnet.AuctionClaim proto)
    {
      return new AuctionClaimEntry
      {
        ClaimId = proto.ClaimId,
        Sold = proto.Reason == global::Bnet.AuctionClaim.Types.Reason.Sold,
        Coins = proto.Coins,
        ItemId = proto.ItemId,
        Amount = proto.Amount
      };
    }
  }
}
