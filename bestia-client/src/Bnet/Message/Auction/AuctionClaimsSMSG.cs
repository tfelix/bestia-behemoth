using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>What the market owes this master, oldest first.</summary>
  [GlobalClass]
  public partial class AuctionClaimsSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<AuctionClaimEntry> Claims { get; set; } = [];

    public static AuctionClaimsSMSG FromProto(global::Bnet.AuctionClaimsSMSG proto)
    {
      var claims = new Godot.Collections.Array<AuctionClaimEntry>();
      foreach (var claim in proto.Claims)
      {
        claims.Add(AuctionClaimEntry.FromProto(claim));
      }

      return new AuctionClaimsSMSG { Claims = claims };
    }
  }
}
