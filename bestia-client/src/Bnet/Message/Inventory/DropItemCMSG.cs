using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  public partial class DropItemCMSG : ICMSG
  {
    public ulong ItemId { get; set; }
    public uint Amount { get; set; }

    public override Envelope ToEnvelope()
    {
      var dropItem = new global::Bnet.DropItemCMSG
      {
        ItemId = ItemId,
        Amount = Amount
      };

      return new Envelope
      {
        DropItem = dropItem
      };
    }
  }
}
