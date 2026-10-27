using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class MoonCycleTest
  {
    private const double SecondsPerDay = 24 * 3600.0;

    private static readonly MoonCycle Shipped = new(hoursPerDay: 24, daysPerMonth: 30);

    [Fact]
    public void AWorldStartsAtNewMoon()
    {
      Assert.Equal(0.0, Shipped.PhaseAt(0.0), 6);
      Assert.Equal(0.0, MoonCycle.IlluminationOf(Shipped.PhaseAt(0.0)), 6);
    }

    [Fact]
    public void TheMoonIsFullHalfwayThroughTheMonth()
    {
      var phase = Shipped.PhaseAt(15 * SecondsPerDay);

      Assert.Equal(0.5, phase, 6);
      Assert.Equal(1.0, MoonCycle.IlluminationOf(phase), 6);
    }

    [Fact]
    public void TheCycleRepeatsEveryMonth()
    {
      var early = Shipped.PhaseAt(4.5 * SecondsPerDay);
      var monthLater = Shipped.PhaseAt(34.5 * SecondsPerDay);

      Assert.Equal(early, monthLater, 6);
    }

    [Fact]
    public void WaxingAndWaningHalvesMirrorEachOther()
    {
      Assert.Equal(MoonCycle.IlluminationOf(0.2), MoonCycle.IlluminationOf(0.8), 6);
    }

    /// <summary>Before the world info arrives the moon shows full, the look the sky had before phases.</summary>
    [Fact]
    public void AnUnsetCalendarShowsAFullMoon()
    {
      Assert.Equal(0.5, default(MoonCycle).PhaseAt(12345.0), 6);
    }
  }
}
