using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// How deeply the ground near the camera has been pressed in, as a height field the terrain shader reads.
  /// </summary>
  /// <remarks>
  /// <b>Where a print gets its shape.</b> <see cref="GroundMarkTexture"/> is one texel per square metre, which
  /// is the right resolution for "how worn is this ground" and far too coarse for an outline - a print there
  /// would be a single texel, which is a stain rather than a track. This is sixteen texels to the metre over a
  /// much smaller piece of world, which is the other half of the same trade.
  ///
  /// <para>
  /// <b>Near the camera only, and that is what keeps it cheap.</b> Print shape is legible from a few metres and
  /// gone by thirty, so covering the whole streamed view would spend a hundred times the memory on detail
  /// nobody can resolve. <see cref="Size"/> squared at one byte is a megabyte, fixed, however far anyone walks.
  /// </para>
  ///
  /// <para>
  /// <b>Normals, not geometry.</b> Surface nets emits about one vertex per voxel and Godot 4 exposes no
  /// tessellation, so displacing the terrain mesh by this would move vertices a metre away from the print. The
  /// shader perturbs its normal from the gradient here instead, which reads as a depression at the distance any
  /// of this is seen from - and the field is the same one a parallax or shell pass would later read, so
  /// nothing here has to change to go further.
  /// </para>
  ///
  /// <para>
  /// <b>Addressing is toroidal, as the mark texture's is</b>, but with a consequence that one does not have:
  /// the window is smaller than the view, so ground outside it aliases onto ground inside it. The shader fades
  /// this out past <see cref="RadiusMetres"/>, which is both what hides the aliasing and the distance ramp the
  /// detail deserves anyway. <see cref="Recentre"/> is what keeps the window under the camera, and it clears
  /// rather than scrolls: rebuilding from the stamp records costs a memset and a few hundred small brushes,
  /// where tracking exposed bands would be the same work plus the bookkeeping.
  /// </para>
  /// </remarks>
  public sealed class GroundDisturbanceTexture
  {
    /// <summary>Texels across. One byte each, so the whole field is a megabyte.</summary>
    public const int Size = 1024;

    /// <summary>
    /// Texels to the metre. A 25 cm print is four of them across, which under linear filtering is a dent
    /// rather than a tread - see the class note on what this is and is not for. Raising it and
    /// <see cref="Size"/> together buys shape detail at four times the memory a doubling.
    /// </summary>
    /// <remarks>
    /// A whole number, so a tile's corner falls exactly on a texel and the rasteriser can work in whole
    /// texels from there. That is what keeps a print's shape exact 128 km from the origin, where a float
    /// difference has centimetres of error against something 16 cm long.
    /// </remarks>
    public const int TexelsPerMetre = 16;

    public const float MetresPerTexel = 1.0f / TexelsPerMetre;

    /// <summary>The span of world covered before the addressing repeats.</summary>
    public const float ExtentMetres = Size * MetresPerTexel;

    /// <summary>
    /// How far from the centre the shader may trust this, which must leave room for the camera to wander.
    /// </summary>
    /// <remarks>
    /// The window is <see cref="ExtentMetres"/> across and recentres every <see cref="AnchorStepMetres"/>, so
    /// the camera is at worst a diagonal half-step from the middle. Everything inside this radius is really
    /// this ground's own disturbance; everything beyond it is the wrap showing through.
    /// </remarks>
    public const float RadiusMetres = 26.0f;

    /// <summary>
    /// How far the camera moves before the window follows.
    /// </summary>
    /// <remarks>
    /// A rebuild is a memset and the stamps in range, so this is cheap enough to do often and pointless to do
    /// every frame. Eight metres is about two seconds of running.
    /// </remarks>
    private const float AnchorStepMetres = 8.0f;

    private readonly byte[] _pixels = new byte[Size * Size];

    private readonly ImageTexture _texture;
    private bool _dirty;

    private Vector2 _centre;
    private bool _centred;

    public GroundDisturbanceTexture()
    {
      _texture = ImageTexture.CreateFromImage(
        Image.CreateFromData(Size, Size, false, Image.Format.R8, _pixels));
    }

    public Texture2D Texture => _texture;

    /// <summary>Where the window sits, which the shader needs to know how far away it may still be trusted.</summary>
    public Vector2 Centre => _centre;

    /// <summary>
    /// A whole number of window spans near the camera, for the shader to measure world position from.
    /// </summary>
    /// <remarks>
    /// Purely a floating-point measure, with no visual intent - the same problem <c>slot_uv_origin</c> exists
    /// for, one step further along. A UV of <c>world / 64</c> reaches 2000 at the edge of a 128 km world, where
    /// a float has about four values per texel of this grid and the print bands. Rebasing by a multiple of the
    /// span lands on exactly the same texels, because the addressing wraps at that span, and does the division
    /// on a small number instead.
    /// </remarks>
    public Vector2 Origin => new(
      Mathf.Round(_centre.X / ExtentMetres) * ExtentMetres,
      Mathf.Round(_centre.Y / ExtentMetres) * ExtentMetres);

    /// <summary>
    /// Moves the window under the camera if it has wandered far enough.
    /// </summary>
    /// <returns>
    /// whether the window moved, which means everything in it was cleared and the caller must stamp its
    /// records back in. False on almost every frame.
    /// </returns>
    public bool Recentre(float worldX, float worldZ)
    {
      var moved = new Vector2(
        Mathf.Round(worldX / AnchorStepMetres) * AnchorStepMetres,
        Mathf.Round(worldZ / AnchorStepMetres) * AnchorStepMetres);

      if (_centred && moved.IsEqualApprox(_centre))
      {
        return false;
      }

      _centre = moved;
      _centred = true;
      Clear();

      return true;
    }

    /// <summary>Whether a point is inside the window at all, and therefore worth stamping.</summary>
    public bool Covers(double worldX, double worldZ)
    {
      var half = ExtentMetres * 0.5f;

      return System.Math.Abs(worldX - _centre.X) <= half && System.Math.Abs(worldZ - _centre.Y) <= half;
    }

    /// <summary>
    /// Presses one print into the field.
    /// </summary>
    /// <remarks>
    /// The shape is rasterised in metres rather than in texels, so raising <see cref="Size"/> sharpens the
    /// print instead of only making it bigger.
    ///
    /// <para>
    /// <paramref name="seed"/> moves it off the tile centre. Steps land on a one metre lattice, so prints
    /// stamped dead centre would be a dotted line down the middle of it - and two creatures following the same
    /// route would leave one line of tracks walked twice rather than two lines.
    /// </para>
    /// </remarks>
    /// <param name="worldX">the tile's world x; the print is placed within it, not on its corner</param>
    public void Stamp(long worldX, long worldY, int octant, int seed, byte strength)
    {
      if (strength == 0)
      {
        return;
      }

      var angle = octant * Mathf.Pi / 4.0f;
      var along = new Vector2(Mathf.Cos(angle), Mathf.Sin(angle));
      var across = new Vector2(-along.Y, along.X);

      // Within the tile, never outside it: a print that wandered into the next one would show tracks on
      // ground the server never said anything about.
      var jitter = new Vector2(
        (seed & 0x0F) / 15.0f - 0.5f,
        ((seed >> 4) & 0x0F) / 15.0f - 0.5f) * (JitterMetres * TexelsPerMetre);

      // Everything below is in texels measured from this tile's own corner, which is exactly on a texel. The
      // tile's world position only ever appears as whole texels in `long` arithmetic, so nothing large is
      // ever subtracted from anything large - see TexelsPerMetre.
      var originX = worldX * TexelsPerMetre;
      var originY = worldY * TexelsPerMetre;

      var centreU = TexelsPerMetre * 0.5f + jitter.X;
      var centreV = TexelsPerMetre * 0.5f + jitter.Y;

      var radius = Mathf.CeilToInt(Mathf.Max(PrintLengthMetres, PrintWidthMetres) * TexelsPerMetre);

      var fromU = Mathf.FloorToInt(centreU) - radius;
      var fromV = Mathf.FloorToInt(centreV) - radius;

      for (var v = fromV; v <= fromV + 2 * radius; v++)
      {
        for (var u = fromU; u <= fromU + 2 * radius; u++)
        {
          // The texel's own centre, so the shape is sampled where it is drawn rather than at its corner.
          var offset = new Vector2(u + 0.5f - centreU, v + 0.5f - centreV) * MetresPerTexel;

          var lengthwise = offset.Dot(along) / PrintLengthMetres;
          var crosswise = offset.Dot(across) / PrintWidthMetres;

          var depth = 1.0f - (lengthwise * lengthwise + crosswise * crosswise);
          if (depth <= 0.0f)
          {
            continue;
          }

          var at = TexelAt(originX + u, originY + v);
          var pressed = (byte)(depth * strength);

          // Deepest wins rather than summing: two prints overlapping make one print, not a crater.
          if (pressed > _pixels[at])
          {
            _pixels[at] = pressed;
          }
        }
      }

      _dirty = true;
    }

    /// <summary>Uploads if anything changed. Cheap and idempotent when nothing did.</summary>
    /// <returns>whether an upload actually happened, which is what a caller logs rather than guesses at</returns>
    public bool Flush()
    {
      if (!_dirty)
      {
        return false;
      }

      _texture.Update(Image.CreateFromData(Size, Size, false, Image.Format.R8, _pixels));
      _dirty = false;

      return true;
    }

    public void Clear()
    {
      System.Array.Clear(_pixels);
      _dirty = true;
    }

    /// <summary>How far off its tile's centre a print may be placed, in metres.</summary>
    private const float JitterMetres = 0.45f;

    /// <summary>Half the length and half the width of a print, in metres. A boot, roughly.</summary>
    private const float PrintLengthMetres = 0.16f;

    private const float PrintWidthMetres = 0.07f;

    /// <summary>
    /// Where one texel of the world lives in <see cref="_pixels"/>.
    /// </summary>
    /// <remarks>
    /// Modulo rather than an offset, because the addressing is toroidal - see the class note. Kept positive by
    /// hand: C# <c>%</c> keeps the sign of the dividend, and half this world has negative coordinates.
    /// </remarks>
    private static int TexelAt(long texelX, long texelY)
    {
      var x = (int)((texelX % Size + Size) % Size);
      var y = (int)((texelY % Size + Size) % Size);

      return y * Size + x;
    }
  }
}
