using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class UseItemCMSG : ICMSG
  {
    /// <summary>The held item, as InventoryItem.PlayerItemId names it.</summary>
    public ulong PlayerItemId { get; set; }

    /// <summary>What the client gathered first, for an item whose script needs it. Null for a plain use.</summary>
    public Message.ScriptArgs Args { get; set; }

    public override Envelope ToEnvelope()
    {
      var useItem = new global::Bnet.UseItemCMSG
      {
        PlayerItemId = PlayerItemId,
        Args = Args?.ToProto()
      };

      return new Envelope
      {
        UseItem = useItem
      };
    }
  }
}
