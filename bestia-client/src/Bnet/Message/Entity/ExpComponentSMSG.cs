using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  [GlobalClass]
  public partial class ExpComponentSMSG : EntitySMSG
  {
    public uint Exp { get; set; }

    public static ExpComponentSMSG FromBnet(global::Bnet.ExpComponentSMSG msg)
    {
      return new ExpComponentSMSG
      {
        EntityId = msg.EntityId,
        Exp = msg.Exp
      };
    }
  }
}
