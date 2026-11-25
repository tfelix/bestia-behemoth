using System.Linq;
using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>What each step of the mana ladder looks like, judged by eye and pinned here.</summary>
  public class ManaLookTest
  {
    private static readonly ManaLook[] Ladder =
      Enumerable.Range(0, ManaLook.HighestTier + 1).Select(ManaLook.For).ToArray();

    [Fact]
    public void ACalmPlaceShowsNothing()
    {
      var calm = ManaLook.For(0);

      Assert.Equal(0.0f, calm.MoodStrength);
      Assert.Equal(0.0f, calm.MoteRate);
      Assert.Equal(0.0f, calm.Hum);
    }

    [Fact]
    public void EveryStepUpFeelsAtLeastAsStrong()
    {
      for (var tier = 1; tier < Ladder.Length; tier++)
      {
        Assert.True(Ladder[tier].MoodStrength > Ladder[tier - 1].MoodStrength, $"mood at tier {tier}");
        Assert.True(Ladder[tier].MoteRate >= Ladder[tier - 1].MoteRate, $"motes at tier {tier}");
        Assert.True(Ladder[tier].Hum > Ladder[tier - 1].Hum, $"hum at tier {tier}");
      }
    }

    [Fact]
    public void ATierThisBuildDoesNotKnowReadsAsBurn()
    {
      Assert.Equal(ManaLook.For(ManaLook.HighestTier), ManaLook.For(ManaLook.HighestTier + 3));
      Assert.Equal(ManaLook.For(0), ManaLook.For(-1));
    }

    [Fact]
    public void TheProtocolStillHasEightTiers()
    {
      var tiers = System.Enum.GetValues(typeof(global::Bnet.ManaTier)).Length;

      Assert.Equal(ManaLook.HighestTier + 1, tiers);
    }

    [Fact]
    public void OnlyAManaStormCarriesMotesOfItsOwn()
    {
      var kinds = System.Enum.GetValues(typeof(WeatherSMSG.Kind)).Cast<WeatherSMSG.Kind>();

      foreach (var kind in kinds)
      {
        var motes = WeatherLook.For(kind, 0.5f, 0.5f).MoteRate;
        Assert.True(kind == WeatherSMSG.Kind.ManaStorm ? motes > 0.0f : motes == 0.0f, kind.ToString());
      }
    }
  }
}
