using Godot;

namespace BestiaBehemothClient.Bnet.Message.Shop
{
  /// <summary>What the basket of the last <see cref="ShopQuoteCMSG"/> comes to right now.</summary>
  [GlobalClass]
  public partial class ShopQuoteSMSG : ISMSG
  {
    [Export] public ulong MerchantEntityId { get; set; }

    /// <summary>Coins for each line, in the order the basket listed them.</summary>
    [Export] public Godot.Collections.Array<long> LineCoins { get; set; } = [];

    /// <summary>Positive when the town pays us, negative when we pay.</summary>
    [Export] public long Balance { get; set; }

    /// <summary>
    /// Empty when the town would trade; otherwise the refusal as a lowercase enum name, as
    /// <see cref="OperationError.CodeName"/> carries one.
    /// </summary>
    [Export] public string RefusalName { get; set; } = "";

    /// <summary>The line at fault, or -1 when the basket as a whole is refused.</summary>
    [Export] public int RefusedLine { get; set; } = -1;

    public static ShopQuoteSMSG FromProto(global::Bnet.ShopQuoteSMSG proto)
    {
      var lineCoins = new Godot.Collections.Array<long>();
      foreach (var coins in proto.LineCoins)
      {
        lineCoins.Add(coins);
      }

      return new ShopQuoteSMSG
      {
        MerchantEntityId = proto.MerchantEntityId,
        LineCoins = lineCoins,
        Balance = proto.Balance,
        RefusalName = proto.HasRefusal ? EnumName.Of(proto.Refusal) : "",
        RefusedLine = proto.RefusedLine
      };
    }
  }
}
