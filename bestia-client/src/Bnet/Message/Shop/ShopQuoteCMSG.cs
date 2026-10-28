using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>Asks what a basket would come to, without trading. Answered by a <see cref="ShopQuoteSMSG"/>.</summary>
  public partial class ShopQuoteCMSG : ICMSG
  {
    public ulong MerchantEntityId { get; set; }

    public Godot.Collections.Array<ShopLine> Lines { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      var quote = new global::Bnet.ShopQuoteCMSG { MerchantEntityId = MerchantEntityId };
      foreach (var line in Lines)
      {
        quote.Lines.Add(line.ToProto());
      }

      return new Envelope { ShopQuote = quote };
    }
  }
}
