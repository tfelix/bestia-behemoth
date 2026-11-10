using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// A bestia's nine individual values as this master may read them, in the order STR, INT, VIT, DEX, WIL, AGI, HP,
  /// MANA, STAMINA. <see cref="Exact"/> is empty unless the master has Bloodline Study.
  /// </summary>
  [GlobalClass]
  public partial class IvReadingInfo : GodotObject
  {
    /// <summary>The <c>IvGrade</c> ordinals: 0 Poor up to 5 Perfect.</summary>
    [Export] public Godot.Collections.Array<int> Grades { get; set; } = [];

    [Export] public Godot.Collections.Array<int> Exact { get; set; } = [];

    public static IvReadingInfo FromProto(global::Bnet.IvReading proto)
    {
      var reading = new IvReadingInfo();
      if (proto == null)
      {
        return reading;
      }

      foreach (var grade in proto.Grades)
      {
        reading.Grades.Add((int)grade);
      }

      foreach (var value in proto.Exact)
      {
        reading.Exact.Add((int)value);
      }

      return reading;
    }
  }
}
