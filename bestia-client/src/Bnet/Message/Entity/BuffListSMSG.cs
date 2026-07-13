using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Every currently visible buff/debuff active on an entity.
  /// </summary>
  [GlobalClass]
  public partial class BuffListSMSG : EntitySMSG
  {
    [Export] public Godot.Collections.Array<BuffListEntry> Buffs { get; set; } = [];

    public BuffListSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create BuffListSMSG from protobuf message
    /// </summary>
    /// <param name="protoBuffList">The protobuf BuffListSMSG message from the server</param>
    /// <returns>A new BuffListSMSG instance</returns>
    public static BuffListSMSG FromProto(global::Bnet.BuffListSMSG protoBuffList)
    {
      var buffList = new BuffListSMSG()
      {
        EntityId = protoBuffList.EntityId
      };

      foreach (var protoBuff in protoBuffList.Buffs)
      {
        buffList.Buffs.Add(BuffListEntry.FromProto(protoBuff));
      }

      return buffList;
    }

    public override string ToString()
    {
      return $"BuffListSMSG(EntityId={EntityId}, BuffCount={Buffs.Count})";
    }
  }
}
