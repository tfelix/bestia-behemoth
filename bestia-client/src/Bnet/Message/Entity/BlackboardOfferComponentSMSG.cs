using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// The suggested levels of the quests open on the boards, as masks: bit n of <see cref="LevelsLow"/> is level
  /// n + 1, bit n of <see cref="LevelsHigh"/> level n + 65. The same on every board.
  /// </summary>
  [GlobalClass]
  public partial class BlackboardOfferComponentSMSG : EntitySMSG
  {
    [Export] public long LevelsLow { get; set; }
    [Export] public long LevelsHigh { get; set; }

    public static BlackboardOfferComponentSMSG FromProto(ulong entityId, global::Bnet.BlackboardOfferComponentSMSG proto)
    {
      return new BlackboardOfferComponentSMSG
      {
        EntityId = entityId,
        LevelsLow = unchecked((long)proto.LevelsLow),
        LevelsHigh = unchecked((long)proto.LevelsHigh)
      };
    }
  }
}
