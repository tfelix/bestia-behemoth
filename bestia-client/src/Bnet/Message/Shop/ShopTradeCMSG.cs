using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>
  /// Buys and sells a whole basket with the settlement we are standing in, as one trade.
  /// </summary>
  /// <remarks>
  /// No prices on the wire. The server quotes against the live market and refuses the trade if it comes
  /// out worse than <see cref="ExpectedBalance"/>, the balance we were last quoted.
  /// </remarks>
  public partial class ShopTradeCMSG : ICMSG
  {
    public ulong MerchantEntityId { get; set; }

    public Godot.Collections.Array<ShopLine> Lines { get; set; } = [];

    /// <summary>Positive when the town pays us, negative when we pay.</summary>
    public long ExpectedBalance { get; set; }

    public override Envelope ToEnvelope()
    {
      var trade = new global::Bnet.ShopTradeCMSG
      {
        MerchantEntityId = MerchantEntityId,
        ExpectedBalance = ExpectedBalance
      };
      foreach (var line in Lines)
      {
        trade.Lines.Add(line.ToProto());
      }

      return new Envelope { ShopTrade = trade };
    }
  }
}
