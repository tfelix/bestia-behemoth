using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  [GlobalClass]
  public partial class LevelComponentSMSG : EntitySMSG
  {
    public uint Level { get; set; }

    public static LevelComponentSMSG FromBnet(ulong entityId, global::Bnet.LevelComponentSMSG msg)
    {
      return new LevelComponentSMSG
      {
        EntityId = entityId,
        Level = msg.Level
      };
    }
  }
}
