using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  public enum DamageType
  {
    Miss = 0,
    Normal = 1,
    Crit = 2,
    Dodge = 3,
    Heal = 4
  }

  /// <summary>
  /// Message to send damage information to the server.
  /// </summary>
  [GlobalClass]
  public partial class DamageEntityCMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }
    [Export] public ulong SourceEntityId { get; set; }
    [Export] public int AttackId { get; set; }
    [Export] public uint Damage { get; set; }
    [Export] public uint Div { get; set; }
    [Export] public uint SkillLevel { get; set; }
    [Export] public DamageType Type { get; set; }

    public DamageEntityCMSG()
    {
    }

    /// <summary>
    /// Static factory method to create DamageEntityCMSG from protobuf message
    /// </summary>
    /// <param name="protoDamageEntity">The protobuf DamageEntitySMSG message from the server</param>
    /// <returns>A new DamageEntityCMSG instance</returns>
    public static DamageEntityCMSG FromProto(global::Bnet.DamageEntitySMSG protoDamageEntity)
    {
      return new DamageEntityCMSG()
      {
        EntityId = protoDamageEntity.EntityId,
        SourceEntityId = protoDamageEntity.SourceEntityId,
        AttackId = protoDamageEntity.AttackId,
        Damage = protoDamageEntity.Damage,
        Div = protoDamageEntity.Div,
        SkillLevel = protoDamageEntity.SkillLevel,
        Type = MapDamageTypeFromProto(protoDamageEntity.Type)
      };
    }

    /// <summary>
    /// Maps protobuf DamageType to local DamageType enum
    /// </summary>
    /// <param name="protoDamageType">The protobuf damage type</param>
    /// <returns>The local damage type</returns>
    private static DamageType MapDamageTypeFromProto(global::Bnet.DamageType protoDamageType)
    {
      return protoDamageType switch
      {
        global::Bnet.DamageType.Miss => DamageType.Miss,
        global::Bnet.DamageType.Normal => DamageType.Normal,
        global::Bnet.DamageType.Crit => DamageType.Crit,
        global::Bnet.DamageType.Dodge => DamageType.Dodge,
        global::Bnet.DamageType.Heal => DamageType.Heal,
        _ => DamageType.Miss
      };
    }

    public override string ToString()
    {
      return $"DamageEntityCMSG(EntityId={EntityId}, SourceEntityId={SourceEntityId}, AttackId={AttackId}, Damage={Damage}, Div={Div}, SkillLevel={SkillLevel}, Type={Type})";
    }
  }
}
