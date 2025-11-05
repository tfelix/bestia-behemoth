using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class UseItemCMSG : ICMSG
  {
    public ulong UniqueId { get; set; }

    public override Envelope ToEnvelope()
    {
      var useItem = new global::Bnet.UseItemCMSG
      {
        UniqueId = UniqueId
      };

      return new Envelope
      {
        UseItem = useItem
      };
    }
  }
}

