using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  [GlobalClass]
  public partial class LevelComponentSMSG : EntitySMSG
  {
    public uint Level { get; set; }

    public static LevelComponentSMSG FromBnet(global::Bnet.LevelComponentSMSG msg)
    {
      return new LevelComponentSMSG
      {
        EntityId = msg.EntityId,
        Level = msg.Level
      };
    }
  }
}
