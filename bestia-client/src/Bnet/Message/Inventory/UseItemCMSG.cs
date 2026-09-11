using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class UseItemCMSG : ICMSG
  {
    public ulong ItemId { get; set; }

    /// <summary>What the client gathered first, for an item whose script needs it. Null for a plain use.</summary>
    public Message.ScriptArgs Args { get; set; }

    public override Envelope ToEnvelope()
    {
      var useItem = new global::Bnet.UseItemCMSG
      {
        ItemId = ItemId,
        Args = Args?.ToProto()
      };

      return new Envelope
      {
        UseItem = useItem
      };
    }
  }
}
