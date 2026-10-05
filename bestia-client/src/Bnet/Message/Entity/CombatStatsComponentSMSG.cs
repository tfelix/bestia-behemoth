using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Combat stats component message from server: what an entity's attributes, level and worn gear add up to
  /// in a fight - ATK, MATK, HIT, FLEE, CRIT, DEF and ASPD, as the status window shows them.
  ///
  /// Resolved by the server rather than computed here, so the numbers are the ones a swing actually spends.
  /// Sent to the owner only. ATK and MATK come split into the part the attributes give and the part the
  /// weapon adds; DEF into the flat soft part and the worn hard part (percentage points of damage removed).
  /// </summary>
  [GlobalClass]
  public partial class CombatStatsComponentSMSG : EntitySMSG
  {
    [Export] public int Atk { get; set; }
    [Export] public int WeaponAtk { get; set; }
    [Export] public int Matk { get; set; }
    [Export] public int WeaponMatk { get; set; }
    [Export] public int Hit { get; set; }
    [Export] public int Flee { get; set; }

    /// <summary>Percent, before the target's own CRIT shields against it.</summary>
    [Export] public int Crit { get; set; }

    [Export] public int SoftDefense { get; set; }
    [Export] public int HardDefense { get; set; }

    /// <summary>Attack speed as displayed, 0-190. Higher is faster.</summary>
    [Export] public int Aspd { get; set; }

    public CombatStatsComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create CombatStatsComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoCombatStats">The protobuf CombatStatsSMSG message from the server</param>
    /// <returns>A new CombatStatsComponentSMSG instance</returns>
    public static CombatStatsComponentSMSG FromProto(ulong entityId, global::Bnet.CombatStatsSMSG protoCombatStats)
    {
      return new CombatStatsComponentSMSG()
      {
        EntityId = entityId,
        Atk = protoCombatStats.Atk,
        WeaponAtk = protoCombatStats.WeaponAtk,
        Matk = protoCombatStats.Matk,
        WeaponMatk = protoCombatStats.WeaponMatk,
        Hit = protoCombatStats.Hit,
        Flee = protoCombatStats.Flee,
        Crit = protoCombatStats.Crit,
        SoftDefense = protoCombatStats.SoftDefense,
        HardDefense = protoCombatStats.HardDefense,
        Aspd = protoCombatStats.Aspd
      };
    }

    public override string ToString()
    {
      return $"CombatStatsComponentSMSG(EntityId={EntityId}, Atk={Atk}+{WeaponAtk}, Matk={Matk}+{WeaponMatk}, Hit={Hit}, Flee={Flee}, Crit={Crit}, Def={SoftDefense}+{HardDefense}%, Aspd={Aspd})";
    }
  }
}
