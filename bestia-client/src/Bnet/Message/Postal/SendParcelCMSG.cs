using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Sends the items as one parcel.</summary>
  public partial class SendParcelCMSG : ICMSG
  {
    public ulong PostmasterEntityId { get; set; }
    public string RecipientName { get; set; } = "";

    /// <summary>The bag's player item ids, each with its amount at the same index in <see cref="Amounts"/>.</summary>
    public Godot.Collections.Array<long> PlayerItemIds { get; set; } = [];
    public Godot.Collections.Array<int> Amounts { get; set; } = [];

    /// <summary>Never with a player courier, so it cannot be robbed.</summary>
    public bool CoachOnly { get; set; }

    public override Envelope ToEnvelope()
    {
      var send = new global::Bnet.SendParcelCMSG
      {
        PostmasterEntityId = PostmasterEntityId,
        RecipientName = RecipientName,
        CoachOnly = CoachOnly
      };
      send.Items.AddRange(ParcelItems.Of(PlayerItemIds, Amounts));
      return new Envelope { SendParcel = send };
    }
  }
}
