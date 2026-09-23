using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>
  /// Buys or sells one item with the settlement we are standing in.
  /// </summary>
  /// <remarks>
  /// No price on the wire in either direction. We say how much of what, and the server quotes - so a
  /// window that has gone stale pays the current price rather than the one it is showing.
  /// </remarks>
  public partial class ShopTradeCMSG : ICMSG
  {
    public ulong ItemId { get; set; }

    public int Amount { get; set; }

    /// <summary>True when we are handing goods over, false when taking them.</summary>
    public bool Selling { get; set; }

    public ulong MerchantEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        ShopTrade = new global::Bnet.ShopTradeCMSG
        {
          ItemId = ItemId,
          Amount = Amount,
          Selling = Selling,
          MerchantEntityId = MerchantEntityId
        }
      };
    }
  }
}
