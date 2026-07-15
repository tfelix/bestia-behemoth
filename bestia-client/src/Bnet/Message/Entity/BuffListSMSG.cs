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
    [Export] public Godot.Collections.Array<StatusEffectListEntry> Effects { get; set; } = [];

    public BuffListSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create BuffListSMSG from protobuf message
    /// </summary>
    /// <param name="protoBuffList">The protobuf BuffListSMSG message from the server</param>
    /// <returns>A new BuffListSMSG instance</returns>
    public static BuffListSMSG FromProto(StatusEffectListSMSG protoBuffList)
    {
      var buffList = new BuffListSMSG()
      {
        EntityId = protoBuffList.EntityId
      };

      foreach (var protoBuff in protoBuffList.Effects)
      {
        buffList.Effects.Add(StatusEffectListEntry.FromProto(protoBuff));
      }

      return buffList;
    }

    public override string ToString()
    {
      return $"BuffListSMSG(EntityId={EntityId}, BuffCount={Effects.Count})";
    }
  }
}
