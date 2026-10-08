using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  [GlobalClass]
  public partial class ExpComponentSMSG : EntitySMSG
  {
    public uint Exp { get; set; }
    public uint RequiredExpNextLevel { get; set; }

    public static ExpComponentSMSG FromBnet(ulong entityId, global::Bnet.ExpComponentSMSG msg)
    {
      return new ExpComponentSMSG
      {
        EntityId = entityId,
        Exp = msg.Exp,
        RequiredExpNextLevel = msg.RequiredExpNextLevel
      };
    }
  }
}
