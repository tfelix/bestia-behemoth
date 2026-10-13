using System.IO;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The rules message as the client reads it: the numbers it lays footprints, wear and blood by.
  /// </summary>
  /// <remarks>
  /// Typed in as the server's defaults rather than taken from the server, so a field read from the wrong place
  /// or into the wrong unit shows up here instead of as marks that are slightly off everywhere.
  /// </remarks>
  public class GroundRulesTest
  {
    private static global::Bnet.GroundMarkRulesSMSG Message()
    {
      var message = new global::Bnet.GroundMarkRulesSMSG
      {
        StampLifetimeMillis = 1_200_000,
        StampRingCapacity = 48,
        Wear = new global::Bnet.GroundWearRules
        {
          FadeSeconds = 259_200, VisibleThreshold = 32, StepWeight = 6, NeighbourWeight = 2
        },
        Blood = new global::Bnet.GroundBloodRules
        {
          FadeSeconds = 432_000, VisibleThreshold = 16, SpillWeight = 220, SpillRadiusTiles = 2
        }
      };

      message.StampBlocks.Add(13);
      message.StampBlocks.Add(15);
      message.Wear.BlockWeights.Add(new global::Bnet.GroundBlockWeight { BlockId = 13, Weight = 1.0 });
      message.Wear.BlockWeights.Add(new global::Bnet.GroundBlockWeight { BlockId = 14, Weight = 0.8 });

      return message;
    }

    [Fact]
    public void TheStampSettingsAreRead()
    {
      var rules = GroundRules.FromProto(Message());

      Assert.Equal(1_200_000, rules.StampLifetimeMillis);
      Assert.Equal(48, rules.StampRingCapacity);
      Assert.True(rules.TakesPrint(13));
      Assert.True(rules.TakesPrint(15));
      Assert.False(rules.TakesPrint(14));
    }

    [Fact]
    public void TheWearSettingsAreRead()
    {
      var wear = GroundRules.FromProto(Message()).Wear;

      Assert.Equal(259_200, wear.FadeSeconds);
      Assert.Equal(32, wear.VisibleThreshold);
      Assert.Equal(6, wear.StepWeight);
      Assert.Equal(2, wear.NeighbourWeight);
      Assert.Equal(1.0, wear.WeightOf(13));
      Assert.Equal(0.8, wear.WeightOf(14));
    }

    [Fact]
    public void ABlockNobodyListedDoesNotWear()
    {
      Assert.Equal(0.0, GroundRules.FromProto(Message()).Wear.WeightOf(15));
    }

    [Fact]
    public void TheBloodSettingsAreRead()
    {
      var blood = GroundRules.FromProto(Message()).Blood;

      Assert.Equal(432_000, blood.FadeSeconds);
      Assert.Equal(16, blood.VisibleThreshold);
      Assert.Equal(220, blood.SpillWeight);
      Assert.Equal(2, blood.SpillRadiusTiles);
    }

    [Fact]
    public void AMessageWithoutItsWearSectionIsRefused()
    {
      var message = Message();
      message.Wear = null;

      Assert.Throws<InvalidDataException>(() => GroundRules.FromProto(message));
    }

    /// <summary>A zero fade divides by zero in the decay, and a zero lifetime ages every print out at once.</summary>
    [Fact]
    public void NumbersNoRuleCanUseAreRefused()
    {
      var noLifetime = Message();
      noLifetime.StampLifetimeMillis = 0;

      var noFade = Message();
      noFade.Blood.FadeSeconds = 0;

      Assert.Throws<InvalidDataException>(() => GroundRules.FromProto(noLifetime));
      Assert.Throws<InvalidDataException>(() => GroundRules.FromProto(noFade));
    }
  }
}
