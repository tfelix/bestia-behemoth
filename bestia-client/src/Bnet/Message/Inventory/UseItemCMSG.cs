using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class UseItemCMSG : ICMSG
  {
    public ulong ItemId { get; set; }

    public override Envelope ToEnvelope()
    {
      var useItem = new global::Bnet.UseItemCMSG
      {
        ItemId = ItemId
      };

      return new Envelope
      {
        UseItem = useItem
      };
    }
  }
}
