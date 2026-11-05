using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Weighs a parcel and asks its fee and latest arrival time.</summary>
  public partial class QuoteParcelCMSG : ICMSG
  {
    public ulong PostmasterEntityId { get; set; }
    public string RecipientName { get; set; } = "";

    /// <summary>The bag's player item ids, each with its amount at the same index in <see cref="Amounts"/>.</summary>
    public Godot.Collections.Array<long> PlayerItemIds { get; set; } = [];
    public Godot.Collections.Array<int> Amounts { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      var quote = new global::Bnet.QuoteParcelCMSG { PostmasterEntityId = PostmasterEntityId, RecipientName = RecipientName };
      quote.Items.AddRange(ParcelItems.Of(PlayerItemIds, Amounts));
      return new Envelope { QuoteParcel = quote };
    }
  }
}
