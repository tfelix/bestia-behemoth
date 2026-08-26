using BestiaBehemothClient.Game.World;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The grass field's level of detail: how much of a cell is drawn, how large, and inside what budget.
  /// </summary>
  /// <remarks>
  /// Written against a bug that had already shipped. The field measured its distance from the *camera*, and the
  /// spring arm puts the camera 8 m to 36 m from the player while the fade band spanned 12 m to 40 m - so the
  /// zoom and the band were the same size and zooming out consumed the whole of it. At full zoom the ground the
  /// player was standing on drew about a fiftieth of its grass.
  ///
  /// <para>
  /// The arithmetic lives in <see cref="GrassLod"/> rather than in <c>TerrainGrass</c> precisely so it can be
  /// pinned here: <c>TerrainGrass</c> is a <c>Node3D</c> and this project runs without an engine, so anything
  /// left inside the node can only be judged by eye.
  /// </para>
  /// </remarks>
  public class GrassLodTest
  {
    private const float Full = 15.0f;
    private const float Fade = 60.0f;

    /// <summary>
    /// Zooming out never thins a cell, at any distance and any response.
    /// </summary>
    /// <remarks>
    /// <b>The bug, stated as a property.</b> How much grass stands at a point on the ground is a fact about that
    /// ground and about where the player is; it cannot be a fact about how far back the camera is sitting. Since
    /// the band only ever widens, a fraction at a fixed distance can only rise with the zoom - so this fails if
    /// anyone reintroduces a term that shrinks the band, whatever else they change.
    /// </remarks>
    [Fact]
    public void ZoomingOutNeverThinsACell()
    {
      foreach (var distance in new[] { 0.0f, 5.0f, 14.9f, 20.0f, 35.0f, 55.0f, 90.0f })
      {
        var previous = 0.0f;

        // The spring arm's own range, min_cam_distance to max_cam_distance.
        for (var zoom = 8.0f; zoom <= 36.0f; zoom += 1.0f)
        {
          var band = GrassLod.BandScale(zoom, 10.0f, 0.5f, 2.5f);
          var fraction = GrassLod.FractionAt(distance, Full * band, Fade * band);

          Assert.True(
            fraction >= previous,
            $"at {distance} m the fraction fell from {previous} to {fraction} on zooming out to {zoom} m");

          previous = fraction;
        }
      }
    }

    /// <summary>
    /// The measurement from the commit message: what the old anchor did to grass under the player's feet.
    /// </summary>
    /// <remarks>
    /// Kept as a test rather than only in prose because it is the number that justifies the whole change, and
    /// because it is the one thing a future refactor could quietly undo. Measured from the camera the field is
    /// all but gone; measured from the player it is untouched.
    /// </remarks>
    [Fact]
    public void GrassUnderThePlayerSurvivesFullZoomOnlyWhenMeasuredFromThem()
    {
      // Eye to the ground at the player's feet at maximum zoom and the shallowest pitch the camera allows.
      const float EyeToFeetAtFullZoom = 36.5f;

      // The band the field shipped with, named here rather than reusing the current one: this half of the test
      // is a statement about what the old code did, and it would quietly stop being one if it drifted with the
      // tuning above.
      const float ShippedFull = 12.0f;
      const float ShippedFade = 40.0f;

      var fromTheEye = GrassLod.FractionAt(EyeToFeetAtFullZoom, ShippedFull, ShippedFade);
      Assert.InRange(fromTheEye, 0.0f, 0.02f);

      var band = GrassLod.BandScale(EyeToFeetAtFullZoom, 10.0f, 0.5f, 2.5f);
      Assert.Equal(1.0f, GrassLod.FractionAt(0.0f, Full * band, Fade * band));
    }

    /// <summary>A response of zero pins the band, which is how the feature is turned off.</summary>
    [Fact]
    public void AZeroResponsePinsTheBand()
    {
      for (var zoom = 1.0f; zoom <= 100.0f; zoom += 7.0f)
      {
        Assert.Equal(1.0f, GrassLod.BandScale(zoom, 10.0f, 0.0f, 2.5f));
      }
    }

    /// <summary>Zooming in past the reference does not shrink the field the player is standing in.</summary>
    [Fact]
    public void ZoomingInsideTheReferenceLeavesTheBandAlone()
    {
      Assert.Equal(1.0f, GrassLod.BandScale(8.0f, 10.0f, 0.5f, 2.5f));
      Assert.Equal(1.0f, GrassLod.BandScale(0.0f, 10.0f, 0.5f, 2.5f));
    }

    [Fact]
    public void TheBandGrowsWithTheZoomUpToItsCeiling()
    {
      // Four times the reference at a response of a half is twice the band.
      Assert.Equal(2.0f, GrassLod.BandScale(40.0f, 10.0f, 0.5f, 4.0f), 4);

      Assert.Equal(1.5f, GrassLod.BandScale(40.0f, 10.0f, 0.5f, 1.5f));
    }

    /// <summary>A reference of zero cannot divide, and answers the neutral band rather than throwing.</summary>
    [Fact]
    public void ADegenerateReferenceIsNeutral()
    {
      Assert.Equal(1.0f, GrassLod.BandScale(20.0f, 0.0f, 0.5f, 2.5f));
    }

    [Fact]
    public void TheFractionIsWholeInsideTheFullBandAndGoneOutsideTheFade()
    {
      Assert.Equal(1.0f, GrassLod.FractionAt(0.0f, Full, Fade));
      Assert.Equal(1.0f, GrassLod.FractionAt(Full, Full, Fade));

      Assert.Equal(0.0f, GrassLod.FractionAt(Fade, Full, Fade));
      Assert.Equal(0.0f, GrassLod.FractionAt(Fade + 100.0f, Full, Fade));
    }

    [Fact]
    public void TheFractionFallsAcrossTheTaper()
    {
      var previous = 1.0f;

      for (var distance = Full; distance <= Fade; distance += 0.5f)
      {
        var fraction = GrassLod.FractionAt(distance, Full, Fade);

        Assert.InRange(fraction, 0.0f, previous);
        previous = fraction;
      }
    }

    /// <summary>
    /// Coverage is held while the scale has headroom, which is the identity the compensation rests on.
    /// </summary>
    /// <remarks>
    /// How much ground a cell hides is <c>count * footprint</c> and a footprint goes with the square of the
    /// scale, so thinning to <c>f</c> and scaling by <c>1/sqrt(f)</c> has to leave the product alone. If this
    /// fails, distant grass bares the ground - which is the symptom the compensation exists to remove, and one
    /// that is easy to mistake for the fade being tuned wrong.
    /// </remarks>
    [Fact]
    public void CoverageIsHeldWhileTheScaleHasHeadroom()
    {
      const double Total = 1000.0;
      const float MaxScale = 2.5f;

      // Above 1/MaxScale² = 0.16, where the clamp has not bitten yet.
      foreach (var fraction in new[] { 1.0f, 0.9f, 0.6f, 0.4f, 0.25f, 0.17f })
      {
        var scale = GrassLod.CoverageScale(fraction, 1.0f, MaxScale);
        var coverage = Total * fraction * scale * scale;

        Assert.Equal(Total, coverage, 1);
      }
    }

    /// <summary>
    /// Past the ceiling the field goes back to fading, which is wanted rather than tolerated.
    /// </summary>
    /// <remarks>
    /// Compensating the whole way down would end the field in clumps the size of bushes. Saturating means the
    /// far edge still fades out, only much further away than the uncompensated one did.
    /// </remarks>
    [Fact]
    public void TheCompensationSaturatesRatherThanGrowingWithoutBound()
    {
      Assert.Equal(2.5f, GrassLod.CoverageScale(0.01f, 1.0f, 2.5f));
      Assert.Equal(2.5f, GrassLod.CoverageScale(0.0001f, 1.0f, 2.5f));
    }

    /// <summary>A cell drawing nothing is not thin, and <c>1/sqrt(0)</c> is not a number.</summary>
    /// <remarks>
    /// It matters that this is exactly 1: a hidden cell settling on one value is what stops
    /// <c>TerrainGrass.Retune</c> pushing a uniform every frame for the several hundred cells past the fade
    /// radius, which is most of what the terrain holds.
    /// </remarks>
    [Fact]
    public void AnEmptyOrWholeCellTakesTheNeutralScale()
    {
      Assert.Equal(1.0f, GrassLod.CoverageScale(0.0f, 1.0f, 2.5f));
      Assert.Equal(1.0f, GrassLod.CoverageScale(-1.0f, 1.0f, 2.5f));
      Assert.Equal(1.0f, GrassLod.CoverageScale(1.0f, 1.0f, 2.5f));
    }

    /// <summary>Zero strength is the uncompensated field, which is the point of the export.</summary>
    [Fact]
    public void ZeroStrengthLeavesTheClumpsAlone()
    {
      foreach (var fraction in new[] { 0.75f, 0.5f, 0.2f, 0.05f })
      {
        Assert.Equal(1.0f, GrassLod.CoverageScale(fraction, 0.0f, 2.5f));
      }
    }

    [Fact]
    public void HalfStrengthSitsBetweenTheTwo()
    {
      var full = GrassLod.CoverageScale(0.25f, 1.0f, 2.5f);
      var half = GrassLod.CoverageScale(0.25f, 0.5f, 2.5f);

      Assert.Equal((1.0f + full) / 2.0f, half, 4);
    }

    [Fact]
    public void AFieldInsideItsBudgetIsNotTrimmed()
    {
      Assert.Equal(1.0f, GrassLod.BudgetTrim(0, 40_000));
      Assert.Equal(1.0f, GrassLod.BudgetTrim(39_999, 40_000));
      Assert.Equal(1.0f, GrassLod.BudgetTrim(40_000, 40_000));
    }

    /// <summary>A budget of zero or less is no budget, not a field of no grass.</summary>
    [Fact]
    public void AnUnsetBudgetDoesNotTrim()
    {
      Assert.Equal(1.0f, GrassLod.BudgetTrim(500_000, 0));
      Assert.Equal(1.0f, GrassLod.BudgetTrim(500_000, -1));
    }

    /// <summary>Over budget, the trim brings the field to the cap and never raises a cell.</summary>
    [Fact]
    public void TheTrimBringsTheFieldInsideItsBudget()
    {
      const int Budget = 40_000;

      foreach (var wanted in new[] { 40_001, 60_000, 120_000, 900_000 })
      {
        var trim = GrassLod.BudgetTrim(wanted, Budget);

        Assert.InRange(trim, 0.0f, 1.0f);
        Assert.True(wanted * trim <= Budget + 1, $"{wanted} trimmed to {wanted * trim}, over a {Budget} budget");
      }
    }

    [Fact]
    public void APointInsideTheBoxIsAtNoDistanceFromIt()
    {
      var min = new Vector3(0.0f, 0.0f, 0.0f);
      var max = new Vector3(8.0f, 2.0f, 8.0f);

      Assert.Equal(0.0f, GrassLod.DistanceToBox(new Vector3(4.0f, 1.0f, 4.0f), min, max));
      Assert.Equal(0.0f, GrassLod.DistanceToBox(min, min, max));
    }

    /// <summary>
    /// A point outside is measured to the nearest face, not to the middle.
    /// </summary>
    /// <remarks>
    /// The reason a cell the player is standing at the edge of draws at full density rather than at whatever a
    /// half-cell offset works out to.
    /// </remarks>
    [Fact]
    public void APointOutsideIsMeasuredToTheNearestFace()
    {
      var min = new Vector3(0.0f, 0.0f, 0.0f);
      var max = new Vector3(8.0f, 2.0f, 8.0f);

      Assert.Equal(2.0f, GrassLod.DistanceToBox(new Vector3(10.0f, 1.0f, 4.0f), min, max));

      // Diagonally off one corner: 3-4-5 in the horizontal plane, and between the faces vertically.
      Assert.Equal(5.0f, GrassLod.DistanceToBox(new Vector3(11.0f, 1.0f, 12.0f), min, max), 4);
    }
  }
}
