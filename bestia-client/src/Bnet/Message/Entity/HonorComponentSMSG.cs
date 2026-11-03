using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  [GlobalClass]
  public partial class HonorComponentSMSG : EntitySMSG
  {
    public int GlobalHonor { get; set; }

    public static HonorComponentSMSG FromProto(ulong entityId, global::Bnet.HonorComponentSMSG msg)
    {
      return new HonorComponentSMSG
      {
        EntityId = entityId,
        GlobalHonor = msg.GlobalHonor
      };
    }
  }
}
