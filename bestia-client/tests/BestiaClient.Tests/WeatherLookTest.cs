using System;
using System.Linq;
using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// That every sky the server can send renders as something, and that the something is the right shape.
  /// </summary>
  /// <remarks>
  /// Written against a bug that had already shipped rather than a hypothetical one. The switch this replaced
  /// answered six of the eleven <see cref="WeatherSMSG.Kind"/> values and let the other five fall through to
  /// zero, so a sandstorm rendered as a clear day - and a clear day is exactly what a clear day looks like,
  /// so there was nothing on screen to report. A kind added to the protocol later would have joined them
  /// silently.
  ///
  /// <para>
  /// The curves are here for the same reason <c>DayCycleTest</c>'s are: they are judged by eye and can only
  /// be regressed by eye, unless something writes down what they were supposed to do.
  /// </para>
  /// </remarks>
  public class WeatherLookTest
  {
    private static readonly WeatherSMSG.Kind[] AllKinds =
      Enum.GetValues(typeof(WeatherSMSG.Kind)).Cast<WeatherSMSG.Kind>().ToArray();

    [Fact]
    public void TheProtocolStillHasElevenKinds()
    {
      // Not a tautology: it is the tripwire that sends whoever adds a twelfth kind to WeatherLook.For, where
      // the compiler will not send them - a switch expression over an enum needs no arm per value.
      Assert.Equal(11, AllKinds.Length);
    }

    /// <summary>
    /// Every kind but Clear changes something. This is the test the old switch would have failed.
    /// </summary>
    [Fact]
    public void NoKindRendersAsAClearDay()
    {
      foreach (var kind in AllKinds.Where(k => k != WeatherSMSG.Kind.Clear))
      {
        // A flat reading with no cover reported, so anything visible has to come from the kind itself.
        var look = WeatherLook.For(kind, 0.5f, 0.0f);

        var visible = look.IsPrecipitating ||
                      look.Overcast > 0.0f ||
                      look.Visibility < 1.0f ||
                      look.HasLightning;

        Assert.True(visible, $"{kind} renders as a clear day");
      }
    }

    /// <summary>
    /// One sky does one thing. Rain and snow are the same cloud deciding which it is, not two effects that
    /// can overlap, and a frame with both running is a mapping bug rather than sleet.
    /// </summary>
    [Fact]
    public void NothingFallsAsTwoThingsAtOnce()
    {
      foreach (var kind in AllKinds)
      {
        var look = WeatherLook.For(kind, 1.0f, 1.0f);
        var falling = new[] { look.RainRate, look.SnowRate, look.DustRate }.Count(rate => rate > 0.0f);

        Assert.True(falling <= 1, $"{kind} falls as {falling} things at once");
      }
    }

    /// <summary>
    /// Rain does not fall out of a blue sky, whatever cover the server reported.
    /// </summary>
    [Fact]
    public void PrecipitationForcesASkyToFallOutOf()
    {
      foreach (var kind in AllKinds)
      {
        var look = WeatherLook.For(kind, 0.6f, 0.0f);

        if (look.IsPrecipitating)
        {
          Assert.True(look.Overcast > 0.4f, $"{kind} precipitates under {look.Overcast:F2} cover");
        }
      }
    }

    /// <summary>Reported cover can raise a floor but never lower it.</summary>
    [Fact]
    public void ReportedCoverOnlyEverAddsToTheFloor()
    {
      foreach (var kind in AllKinds)
      {
        var bare = WeatherLook.For(kind, 0.5f, 0.0f);
        var full = WeatherLook.For(kind, 0.5f, 1.0f);

        Assert.True(full.Overcast >= bare.Overcast, $"{kind} greys less under more cloud");
        Assert.Equal(1.0f, full.Overcast, 5);
      }
    }

    /// <summary>
    /// Cloud shadows peak under a broken sky and fade out again under a solid one.
    /// </summary>
    /// <remarks>
    /// The non-obvious half of the curve, and the whole reason it is written down. Distinct shadows need
    /// distinct clouds; full overcast has none and produces uniform gloom instead. A curve that only rose
    /// would put its strongest mottling on exactly the sky that has no mottling in it.
    /// </remarks>
    [Fact]
    public void CloudShadowsPeakUnderABrokenSky()
    {
      var clear = WeatherLook.For(WeatherSMSG.Kind.Clear, 0.0f, 0.0f).ShadowStrength;
      var broken = WeatherLook.For(WeatherSMSG.Kind.Clear, 0.0f, 0.5f).ShadowStrength;
      var solid = WeatherLook.For(WeatherSMSG.Kind.Clear, 0.0f, 1.0f).ShadowStrength;

      Assert.Equal(0.0f, clear, 5);
      Assert.True(broken > 0.9f, $"a broken sky casts only {broken:F2}");
      Assert.True(solid < broken, "a solid overcast casts the strongest shadows");

      // Small rather than zero: a perfectly even ground under a grey sky reads as a feature that failed to
      // load, not as weather.
      Assert.InRange(solid, 0.05f, 0.3f);
    }

    /// <summary>
    /// Snow lies, rain wets, and fog wets without anything falling at all.
    /// </summary>
    /// <remarks>
    /// The last of the three is what earned <see cref="WeatherLook.GroundWetRate"/> a field of its own rather
    /// than being read off <see cref="WeatherLook.RainRate"/> by the caller.
    /// </remarks>
    [Fact]
    public void TheGroundGetsWetFromRainAndFogButNeverFromSnow()
    {
      var rain = WeatherLook.For(WeatherSMSG.Kind.Rain, 0.8f, 0.8f);
      Assert.Equal(rain.RainRate, rain.GroundWetRate, 5);

      var snow = WeatherLook.For(WeatherSMSG.Kind.Snow, 0.8f, 0.8f);
      Assert.True(snow.SnowRate > 0.0f);
      Assert.Equal(0.0f, snow.GroundWetRate, 5);

      var fog = WeatherLook.For(WeatherSMSG.Kind.Fog, 0.8f, 0.8f);
      Assert.False(fog.IsPrecipitating);
      Assert.True(fog.GroundWetRate > 0.0f, "fog leaves the ground bone dry");
    }

    /// <summary>Only the storms flash, and every one of them does.</summary>
    [Fact]
    public void TheStormsAreTheOnesThatFlash()
    {
      var flashing = AllKinds.Where(k => WeatherLook.For(k, 0.5f, 0.5f).HasLightning).ToArray();

      Assert.Equal(
        new[] { WeatherSMSG.Kind.Thunderstorm, WeatherSMSG.Kind.ManaStorm, WeatherSMSG.Kind.Tornado }.OrderBy(k => k),
        flashing.OrderBy(k => k));
    }

    /// <summary>
    /// Harder weather is never easier to see through.
    /// </summary>
    [Fact]
    public void IntensityNeverImprovesVisibility()
    {
      foreach (var kind in AllKinds)
      {
        var light = WeatherLook.For(kind, 0.0f, 0.5f);
        var heavy = WeatherLook.For(kind, 1.0f, 0.5f);

        Assert.True(heavy.Visibility <= light.Visibility, $"{kind} clears up as it gets worse");
        Assert.InRange(heavy.Visibility, 0.0f, 1.0f);
      }
    }

    /// <summary>
    /// A reading outside 0..1 is clamped rather than propagated.
    /// </summary>
    /// <remarks>
    /// Cheap here and expensive everywhere else: these numbers end up as light energies and particle ratios,
    /// where a negative would be a black sun and a 4.0 an emitter asking for four times its own budget.
    /// </remarks>
    [Fact]
    public void ReadingsOutsideTheUnitRangeAreClamped()
    {
      var look = WeatherLook.For(WeatherSMSG.Kind.HeavyRain, 12.0f, -3.0f);

      Assert.InRange(look.RainRate, 0.0f, 1.0f);
      Assert.InRange(look.Overcast, 0.0f, 1.0f);
      Assert.InRange(look.Visibility, 0.0f, 1.0f);
      Assert.InRange(look.ShadowStrength, 0.0f, 1.0f);
    }

    /// <summary>
    /// A kind this build has never heard of is a clear sky, not a crash.
    /// </summary>
    /// <remarks>
    /// <c>weather.proto</c> states that appending kinds is safe, which is a promise about old clients: this
    /// is the client end of it. Reached by casting past the enum, which is exactly what the generated proto
    /// code does with an unknown wire value.
    /// </remarks>
    [Fact]
    public void AnUnknownKindDegradesToAClearSky()
    {
      var look = WeatherLook.For((WeatherSMSG.Kind)99, 1.0f, 0.0f);

      Assert.False(look.IsPrecipitating);
      Assert.False(look.HasLightning);
      Assert.Equal(1.0f, look.Visibility, 5);
    }

    /// <summary>
    /// The sun dims and softens together as cloud comes over, and the sky brightens to make up some of it.
    /// </summary>
    [Fact]
    public void CloudDimsTheSunAndBrightensTheSky()
    {
      var clear = WeatherLook.For(WeatherSMSG.Kind.Clear, 0.0f, 0.0f);
      var grey = WeatherLook.For(WeatherSMSG.Kind.Cloudy, 0.0f, 1.0f);

      Assert.Equal(1.0f, clear.SunEnergyScale, 5);
      Assert.True(grey.SunEnergyScale < clear.SunEnergyScale);

      // Softer, not merely darker: dimming alone gives sharp black shadows in a grey world, which is an
      // eclipse rather than a dull day.
      Assert.True(grey.SunAngularDegrees > clear.SunAngularDegrees);

      // Note what is not asserted here: the fill light that replaces the sun. Cloud moves light out of one
      // direction and into every direction, and that half is carried by environment.gd blending toward a
      // light grey sky - ambient is sampled from the sky dome, so brightening the dome is what raises it.

      Assert.True(grey.TwilightScale < clear.TwilightScale, "an overcast sunset is still orange");
    }
  }
}
