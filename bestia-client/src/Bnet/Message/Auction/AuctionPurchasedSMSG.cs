using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>A listing this client bought. The goods are already in the inventory.</summary>
  [GlobalClass]
  public partial class AuctionPurchasedSMSG : ISMSG
  {
    [Export] public ulong ListingId { get; set; }
    [Export] public ulong ItemId { get; set; }
    [Export] public int Amount { get; set; }

    public static AuctionPurchasedSMSG FromProto(global::Bnet.AuctionPurchasedSMSG proto)
    {
      return new AuctionPurchasedSMSG
      {
        ListingId = proto.ListingId,
        ItemId = proto.ItemId,
        Amount = proto.Amount
      };
    }
  }
}
