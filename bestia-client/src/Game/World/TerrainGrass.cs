using System.Collections.Generic;
using System.Diagnostics;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Scatters decorative grass over the ground that is drawn as grass.
  /// </summary>
  /// <remarks>
  /// <b>Not the same thing as the ground cover, and the difference is who owns it.</b> A herb, a shrub and a
  /// reed are server entities with ids, health and a <c>collect</c> block - see <c>GroundCoverScatter</c> and
  /// <see cref="StaticEntityRenderer"/> - and they are scattered on a three-metre lattice because every one of
  /// them costs a <c>world.createEntity</c> per column a player holds. That budget is what
  /// <c>GroundCoverParams.litterGain</c> spends, and it is why a meadow of them reads as a dozen plants in a
  /// field rather than as a field.
  ///
  /// <para>
  /// This is the field. It is drawn and nothing else: no ids, no server, no clicks, no collision, and no
  /// message on the wire. So it can run at whatever density looks right, and the only thing it costs is
  /// triangles.
  /// </para>
  ///
  /// <para>
  /// <b>Scattered on the terrain mesh rather than on the voxels under it.</b> The mesher already decided where
  /// the ground is and what it is made of: <see cref="ChunkSurface.Vertices"/> is the drawn surface, and
  /// <see cref="ChunkSurface.SlotWeights0"/> carries how much of each vertex is
  /// <see cref="BlockAppearance.SurfaceSlot.Grass"/>. Sampling that is exact by construction - a blade cannot
  /// end up hovering over ground the mesher put somewhere else, and grass fades out exactly where the texture
  /// does, because it is reading the same number the terrain shader is.
  /// </para>
  ///
  /// <para>
  /// <b>Three tiers, and a cell is in exactly one of them.</b> <c>grass2</c>'s tuft near the player,
  /// <see cref="GrassTussock"/>'s crown standing in for a group of them through the middle distance, and past
  /// that no geometry at all - the terrain shader draws the canopy on the ground itself. Which tier a cell
  /// takes is its distance from the player, dithered per cell so the ring between two of them has no shape the
  /// eye can find. Both tiers draw from the one buffer the scatter built and share one material, so the mid
  /// tier costs no second node, no second upload and no colour of its own.
  ///
  /// <para>
  /// <c>grass.res</c> and <c>grass.tres</c> stay where they are: <see cref="PropAppearance"/> draws every shrub
  /// and reed in the world from them.
  /// </para>
  ///
  /// <para>
  /// <b>The unit of drawing is a cell, not a chunk.</b> Each chunk's grass is split across a world-aligned
  /// <see cref="CellMetres"/> grid, and each cell is one <see cref="MultiMesh"/> whose density is set from that
  /// cell's own distance to the player. Doing it per chunk instead is the obvious thing and it does not work:
  /// a chunk is 32 m across, so one whose near corner is under the player's feet also reaches past the fade
  /// radius, and measuring it by its nearest point draws the whole thousand square metres at full density.
  /// Measured on nine by nine grassy chunks, that was 2.1 M triangles where cells make it under a million.
  /// </para>
  ///
  /// <para>
  /// <b>To the player, and never to the camera.</b> How much grass stands on a patch of ground is a fact about
  /// that ground and about where the player is; it cannot be a fact about how far back the camera happens to be
  /// sitting. Measuring from the eye made zooming out empty the field - see <see cref="SetFocusAt"/> for the
  /// measurement. How much ground is *on screen* does depend on the zoom, and that is answered separately, by
  /// <see cref="ZoomResponse"/> widening the band.
  /// </para>
  ///
  /// <para>
  /// <b>Density falls off through <see cref="MultiMesh.VisibleInstanceCount"/>.</b> That property truncates the
  /// instance buffer, so a cell's grass is a prefix of its own transforms. The scatter shuffles each cell
  /// before uploading it - which is the entire reason it does - so a prefix is a uniform random subset of the
  /// cell rather than whichever triangles the mesher happened to emit first. Changing the count is one integer
  /// per cell per frame, with no rebuild and no reupload.
  /// </para>
  ///
  /// <para>
  /// <b>The budget is spent on the ground the camera is pointed at, and it is spent near-first.</b> Two things
  /// that used to be neither. The wedge - <see cref="GrassLod.InView"/>, sized by
  /// <see cref="ViewMarginDegrees"/> - stops the field paying for grass behind the player that Godot culls for
  /// nothing; roughly three quarters of the disc was being counted and never drawn. The exponent -
  /// <see cref="GrassLod.Sharpen"/>, bounded by <see cref="MaxExponent"/> - takes what is still over budget out
  /// of the far field only, because a fraction of 1 stays 1 at every power. Together they are what let
  /// <see cref="FadeOutMetres"/> go from sixty metres to the edge of the streamed terrain without
  /// <see cref="MaxVisibleTriangles"/> moving at all.
  /// </para>
  ///
  /// <para>
  /// <b>Thinning a cell does not have to bare its ground, and growing what is left is not how that is paid
  /// for.</b> Coverage is <c>count * footprint</c>, and the footprint of a plant goes with the square of its
  /// size - but its size is also a silhouette the player measures against their own character, so it can
  /// absorb almost nothing before the field is taller than they are. <see cref="GrassLod.CoverageScale"/> is
  /// therefore held near 1 and is fed a distance-only number no budget can reach. What each tier gives up is
  /// held by the next one instead, and the last of them is the ground: see <see cref="PublishField"/> and the
  /// canopy block in <c>terrain_common.gdshaderinc</c>.
  /// </para>
  ///
  /// <para>
  /// Shadows are off. A shadow pass over tens of thousands of instances costs about what the colour pass does,
  /// and grass is the one thing in the world whose shadow nobody can pick out from the grass next to it.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class TerrainGrass : Node3D
  {
    /// <summary>Tufts per square metre of fully grassy, flat ground.</summary>
    /// <remarks>
    /// A tuft at <see cref="Height"/> spans about 0.68 m of ground, and coverage is <c>count * footprint</c>
    /// with the footprint going as the square of the span.
    ///
    /// <para>
    /// <b>This does not hold coverage, and it is not meant to.</b> The field at 8.6 tufts of 1.26 m hid about
    /// three quarters of its ground; eleven of 0.68 m hide about half of it, because the height came down
    /// twice and the count only went up once. What is uncovered is the terrain's own grass texture, which is
    /// the right thing to be looking at - a shorter field is a thinner one, and pretending otherwise is what
    /// makes short grass read as moss. <b>Raise this first if it reads too sparse</b>, since it is the only
    /// number here that buys cover without making the field taller again.
    /// </para>
    ///
    /// <para>
    /// <b>This number was last judged against a field that was placing about two thirds of it.</b>
    /// <see cref="Scatter"/> weights a triangle by its grass slot weight, and the mesher used to hand it the
    /// soil under the turf rather than the turf, so ground that is entirely grass came through at about 0.64.
    /// Fixing that took every meadow to 1.0, which is half again as many tufts at the same setting - so if the
    /// field now reads too thick, this is the number that grew and not one that needs raising.
    /// </para>
    ///
    /// <para>
    /// Ground that is only partly grass gets proportionally less, because the weight it is multiplied by is the
    /// slot weight the terrain shader blends with - so a dune with green patches on it grows grass on the
    /// patches and nowhere else, without this having to know what a dune is.
    /// </para>
    ///
    /// <para>
    /// <b>Turning this up without <see cref="MaxVisibleTriangles"/> is a trade and not a gain.</b> The ceiling
    /// is on the whole field, so a higher density is met by a higher <see cref="GrassLod.Sharpen"/> exponent -
    /// which spends the extra tufts on the near field and takes them straight back out of the far one. That is
    /// sometimes the trade wanted. It is not the same as a denser field everywhere, and it is the opposite of
    /// what a horizon that looks thin is asking for.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,20,0.1")] public float Density { get; set; } = 11.0f;

    /// <summary>Height of a tuft in metres, before <see cref="HeightSpread"/>.</summary>
    /// <remarks>
    /// The mesh is scaled uniformly, so this is its whole scale and not only its height: at 0.80 m one spans
    /// about 0.68 m of ground.
    ///
    /// <para>
    /// <b>Read against the master, who stands at about 1.8 m.</b> With <see cref="HeightSpread"/> the tallest
    /// tufts reach 1.04 m, so the field comes to a player's waist and the whole upper body is clear of it
    /// wherever they stand. That last part is the test this is set by: at 1.26 m, and at 1.8 m before that, a
    /// player standing in a meadow was a hat in a lawn - findable only by moving it.
    /// </para>
    ///
    /// <para>
    /// It is still <b>taller than a herb</b>, which stands at 0.45 m, so the collectible ground cover is inside
    /// the field rather than above it. That is deliberate and known: the plants worth picking are to be told
    /// apart by having their own model, not by being the tallest thing around.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0.05,4,0.01")] public float Height { get; set; } = 0.8f;

    /// <summary>Half-width of the size spread around <see cref="Height"/>, as a share of it.</summary>
    /// <remarks>
    /// Three tenths, so tufts run 0.56 m to 1.04 m and the tallest of them still stops at a 1.8 m master's
    /// waist.
    ///
    /// <para>
    /// <b>Narrower than the half it carried at the taller <see cref="Height"/>, and it had to come down with
    /// it.</b> This is a share and not an absolute: half of 1.26 m reached 1.89 m, so shrinking the field
    /// without shrinking this would have left the top of the range exactly where the complaint was.
    /// </para>
    ///
    /// <para>
    /// It is still the only thing varying the silhouette. There is one mesh now - two side by side were what
    /// stopped the field reading as one model repeated - and the scatter randomises nothing but yaw, so this is
    /// kept as wide as the ceiling above allows rather than trimmed to taste.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.01")] public float HeightSpread { get; set; } = 0.3f;

    /// <summary>How far from the <b>player</b>, in metres, grass is drawn at its full <see cref="Density"/>.</summary>
    /// <remarks>
    /// From the player rather than from the camera, which is the whole of <see cref="SetFocusAt"/>'s reason -
    /// see it for why measuring from the eye made zooming out empty the field.
    ///
    /// <para>
    /// <b>This is also the ground the budget can never take anything from.</b>
    /// <see cref="GrassLod.FractionAt"/> returns exactly 1 inside this radius and
    /// <see cref="GrassLod.Sharpen"/> leaves a 1 alone at every exponent, so the grass the player is standing
    /// in holds its density however far <see cref="FadeOutMetres"/> is pushed. Its <i>size</i> is protected
    /// separately and more strongly - see <see cref="GrassLod.TierCoverage"/>, which the budget cannot reach at
    /// any distance.
    /// </para>
    ///
    /// <para>
    /// Both this and <see cref="FadeOutMetres"/> are the band at the reference zoom; the band actually used
    /// widens with the camera distance through <see cref="GrassLod.BandScale"/>.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,200,1")] public float FullDensityMetres { get; set; } = 15.0f;

    /// <summary>How far from the player, in metres, the tufts give way to tussocks.</summary>
    /// <remarks>
    /// The end of the tuft tier: full <see cref="Density"/> inside <see cref="FullDensityMetres"/>, tapering to
    /// nothing here, and <see cref="GrassTussock"/>'s crowns from here to <see cref="FadeOutMetres"/>.
    ///
    /// <para>
    /// <b>This is the one number that decides what the field costs.</b> Tufts are 72 triangles against a
    /// tussock's nineteen for twenty-odd times the ground, so the tuft disc is most of the budget at any
    /// setting and everything beyond it is nearly free. Twenty-six metres is a little past the spring arm's
    /// reach at its default zoom, so what the player is looking at is tufts.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,200,1")] public float TuftReachMetres { get; set; } = 26.0f;

    /// <summary>Tufts one tussock stands in for, and so how much the mid tier thins the scatter.</summary>
    /// <remarks>
    /// A tuft is mostly gaps and hides about a twentieth of a square metre; a crown is opaque and hides its
    /// whole footprint. Twenty-four holds roughly the same share of the ground at
    /// <see cref="GrassTussock"/>'s width - tune it by eye against the tuft band beside it, which is what it is
    /// exported for.
    /// </remarks>
    [Export(PropertyHint.Range, "1,80,1")] public int TuftsPerTussock { get; set; } = 24;

    /// <summary>How far from the player, in metres, the last tussock goes out - and so where the geometry ends.</summary>
    /// <remarks>
    /// Measured to the <i>nearest</i> point of a cell rather than to its middle, so a cell the player is
    /// standing at the edge of is at full density rather than at whatever a half-cell offset works out to.
    ///
    /// <para>
    /// The server offers an 11x11 view volume of 32 m chunks, so there is ground out to about 176 m and the fog
    /// does not finish closing until 220. Reaching past that costs nothing in build - the transforms already
    /// exist for every chunk the terrain holds - but it does spend budget on ground that is not streamed, so
    /// <see cref="_Process"/> clamps the band it actually uses.
    /// </para>
    ///
    /// <para>
    /// Past here the terrain shader draws the field on the ground itself, so this is where the geometry stops
    /// rather than where the grass does - see <see cref="PublishField"/>.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,400,1")] public float FadeOutMetres { get; set; } = 150.0f;

    /// <summary>How far the streamed terrain reaches, in metres. The band is clamped to it.</summary>
    /// <remarks>
    /// A fact about the server's <c>viewRadiusChunks</c> and not a preference: five chunks of 32 m is about
    /// 176. <see cref="ZoomResponse"/> widens the band with the camera distance and would otherwise take it
    /// half again past the last chunk that exists, spending budget counting cells that were never built.
    /// </remarks>
    [Export(PropertyHint.Range, "16,600,1")] public float ReachMetres { get; set; } = 176.0f;

    /// <summary>Camera distance, in metres, that <see cref="FullDensityMetres"/> was chosen against.</summary>
    /// <remarks>
    /// Ten, the spring arm's own default <c>spring_length</c>, so the band at the default zoom is exactly the
    /// band named above and the numbers in the inspector mean what they say.
    /// </remarks>
    [Export(PropertyHint.Range, "1,60,1")] public float ReferenceZoomMetres { get; set; } = 10.0f;

    /// <summary>How hard the fade band follows the camera distance. 0 pins it, 1 tracks it linearly.</summary>
    /// <remarks>
    /// The knob that turns this feature off: at 0 the band is fixed at the numbers above whatever the zoom is,
    /// which is what the field did before. Half by default - see <see cref="GrassLod.BandScale"/> for why
    /// tracking the zoom linearly costs more than it is worth.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.05")] public float ZoomResponse { get; set; } = 0.5f;

    /// <summary>Ceiling on how far <see cref="ZoomResponse"/> may widen the band.</summary>
    [Export(PropertyHint.Range, "1,6,0.1")] public float MaxZoomScale { get; set; } = 2.5f;

    /// <summary>
    /// How much of a thinned cell's lost coverage to give back by drawing what is left of it larger.
    /// </summary>
    /// <remarks>
    /// One is full compensation, zero is the uncompensated field. Exported so the two can be compared by eye in
    /// the running game, because which of them looks better at distance is a judgement and not a calculation.
    /// See <see cref="GrassLod.CoverageScale"/> for the identity it rests on.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.05")] public float CoverageCompensation { get; set; } = 1.0f;

    /// <summary>Ceiling on that compensation, past which the field goes back to fading out.</summary>
    /// <remarks>
    /// <b>A plant's size is a silhouette the player measures against their own character, so this has to stay
    /// small.</b> It multiplies the plant's own size, and the master stands 2.09 m: at 1.2 over a 0.80 m tuft
    /// spread to 1.04 the tallest blade in the field is 1.25 m, about waist height. At 2.6 it was 2.70 m, which
    /// is taller than the player and is what made zooming out look like the grass was growing.
    ///
    /// <para>
    /// Kept above 1 at all only to cover the reveal ramp's own shrinkage within a cell. <b>Holding coverage is
    /// not this node's job</b>: the tussock tier holds it through the middle distance and the terrain shader
    /// holds it past that - see <see cref="TuftReachMetres"/> and <see cref="PublishField"/>.
    /// </para>
    ///
    /// <para>
    /// It also sizes <see cref="MultiMeshInstance3D.ExtraCullMargin"/>, since a plant drawn larger than its
    /// transform stands outside the bounds Godot computed from it.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "1,4,0.1")] public float MaxCoverageScale { get; set; } = 1.2f;

    /// <summary>
    /// Triangles this may draw at once across the whole field, or 0 for no budget.
    /// </summary>
    /// <remarks>
    /// The draw-cost twin of <see cref="BuildBudgetMillis"/>, and what makes <see cref="FadeOutMetres"/> and
    /// <see cref="ZoomResponse"/> safe to turn up at all. Over budget, the excess is taken out of the far field
    /// by <see cref="GrassLod.Sharpen"/> rather than off every cell alike - see
    /// <see cref="FullDensityMetres"/> for what that protects.
    ///
    /// <para>
    /// <b>Triangles rather than instances, because the tiers do not cost alike.</b> A tussock stands in for
    /// <see cref="TuftsPerTussock"/> tufts at nineteen triangles against their 72 apiece, so an instance budget
    /// would price the cheap tier as dearly as the expensive one and spend itself on whichever the field
    /// happened to be drawing.
    /// </para>
    ///
    /// <para>
    /// <b>What it buys is a flat cost across the whole zoom range and the whole radius.</b> The count is the
    /// same whether the camera is at 8 m or at 36 m; what changes is the exponent, and so how quickly the field
    /// thins with distance. Bring this down first if the field costs more than it is worth.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,8000000,50000")] public int MaxVisibleTriangles { get; set; } = 1_200_000;

    /// <summary>
    /// How much wider than the camera's own field of view to count ground as visible, in degrees.
    /// </summary>
    /// <remarks>
    /// The margin on <see cref="GrassLod.InView"/>'s wedge. Wide enough that an ordinary turn does not move
    /// what the budget is spent on: a cell entering the wedge is a cell that was already tuned, since
    /// <see cref="Retune"/> runs over every cell whatever the bearing, but a wedge that hugged the frustum
    /// would make the <i>exponent</i> chase every mouse movement.
    ///
    /// <para>
    /// <b>180 turns the wedge off</b> and counts the whole disc, which is what the field did before there was
    /// one - see <see cref="GrassLod.HalfViewAngle"/> for the clamp that makes that exact. The same "set one
    /// number and the feature is gone" property <see cref="ZoomResponse"/> and
    /// <see cref="CoverageCompensation"/> have, and it is how the two halves of the budget work are told apart
    /// by eye.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,180,1")] public float ViewMarginDegrees { get; set; } = 20.0f;

    /// <summary>How steeply the field may be made to thin with distance to fit its budget.</summary>
    /// <remarks>
    /// The ceiling on <see cref="GrassLod.Sharpen"/>'s exponent. Six is well above what a grassy view volume
    /// asks for at the default settings - which settles near 2.3 - and exists so that a pathological view
    /// cannot drive the far field to nothing in one step.
    ///
    /// <para>
    /// <b>1 turns the distance weighting off</b> and gives back the flat <see cref="GrassLod.BudgetTrim"/>
    /// exactly, which is the other half of the A/B against <see cref="ViewMarginDegrees"/>.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "1,12,0.1")] public float MaxExponent { get; set; } = 6.0f;

    /// <summary>How fast the exponent chases the budget. 0 pins it, 1 corrects in one frame.</summary>
    /// <remarks>
    /// See <see cref="GrassLod.NextExponent"/>. A half settles within a few percent in three or four frames,
    /// which is fast enough to read as instant and damped enough not to ring. It is the only smoothing in the
    /// loop, so lowering it is the answer if the field pumps when the camera swings.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.05")] public float BudgetResponse { get; set; } = 0.5f;

    /// <summary>
    /// Edge of one LOD cell, in metres. The granularity the density can vary at, and one draw call each.
    /// </summary>
    /// <remarks>
    /// The trade is entirely between draw calls and wasted triangles, and it is not close at either end. Coarse
    /// cells draw a whole cell at the density of its nearest corner; fine cells draw a few hundred multimeshes.
    /// Eight metres puts about a hundred cells inside the fade radius and keeps the step between one cell's
    /// density and its neighbour's small enough not to read as a seam.
    ///
    /// <para>
    /// Aligned to the world rather than to each chunk, so a cell straddling a chunk boundary is drawn as two
    /// halves at the same density instead of as two cells offset from one another.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "2,64,1")] public float CellMetres { get; set; } = 8.0f;

    /// <summary>
    /// Ceiling on the instances built for one chunk, whatever <see cref="Density"/> asks for.
    /// </summary>
    /// <remarks>
    /// A memory bound rather than a rendering one - the buffers are built for every chunk the terrain holds,
    /// including the ones beyond <see cref="FadeOutMetres"/> that will draw none of them, because the
    /// <see cref="ChunkSurface"/> they are scattered from is handed over once at install and is not kept. Each
    /// buffer is sized to the count actually placed, so this is a ceiling and not a reservation.
    ///
    /// <para>
    /// A backstop rather than a knob. It is applied by scaling <see cref="Density"/> down for the chunk that
    /// would exceed it, so a capped chunk is uniformly thinner rather than bare on one side - but a chunk that
    /// hits it is drawn at a density its neighbours are not, which is a seam. The default is above what a
    /// wholly grassy 32 m chunk asks for - 1024 square metres at 11.0 is about 11,300 - so nothing reaches it
    /// in ordinary play. <b>It has to move whenever <see cref="Density"/> does</b>, or the greenest ground in
    /// the world is the only ground that gets thinned.
    ///
    /// <para>
    /// It very nearly did not move when it had to, twice over. The arithmetic here read 8.6 until well after
    /// <see cref="Density"/> was 11.0, and even then a wholly grassy chunk did not exist to test it against:
    /// the mesher named the soil under the turf rather than the turf, so the grass weight
    /// <see cref="Scatter"/> integrates averaged about 0.64 on ground that is entirely grass and the real ask
    /// was nearer 7,200. Fixing that in <c>SurfaceNets.AccumulateSurface</c> raised every meadow in the world
    /// to 1.0 in one step, which is a 1.56x rise in demand and would have walked straight into a 10,000
    /// ceiling that nothing had ever reached.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,40000,100")] public int MaxPerChunk { get; set; } = 13_000;

    /// <summary>
    /// How upright ground has to be to grow grass, as the vertical component of its normal.
    /// </summary>
    /// <remarks>
    /// 0.6 is about 53 degrees. Grass is drawn standing straight up whatever it grows on, which is right for a
    /// slope and absurd for a wall: on a cliff face the blades would stand out of it sideways, and the
    /// surface-nets mesh has plenty of near-vertical triangles where a terrace steps down.
    ///
    /// <para>
    /// Close to where <c>terrain_common.gdshaderinc</c>'s <c>cliff_start</c> sheds the loose cover off a slope,
    /// which is not a coincidence and is worth keeping that way: the far-field grass tint is applied to what is
    /// left after that shed, so the ground that stops being tinted is the ground that stops growing tufts.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.01")] public float MinUpright { get; set; } = 0.6f;

    /// <summary>
    /// How wide the band is over which a blade grows in at the fade front, as a share of its cell.
    /// </summary>
    /// <remarks>
    /// Without it a blade is switched on at full size the moment the count reaches it, and
    /// <see cref="CoverageCompensation"/> makes that worse rather than better: the blades appearing at the
    /// front are the ones it has grown by up to <see cref="MaxCoverageScale"/>. Walking towards a cell in the
    /// middle of the taper adds several of them a second.
    ///
    /// <para>
    /// A share of the cell rather than a count of blades, so a blade takes the same <i>time</i> to grow
    /// whether its cell holds thirty of them or three hundred - how fast the front moves is a property of
    /// the taper and of walking speed, not of the density. At 0.06 and four metres a second that is roughly
    /// half a second in the middle of the tuft band.
    /// </para>
    ///
    /// <para>
    /// <b>0 reproduces the old hard pop exactly</b>, the way <see cref="ZoomResponse"/> at 0 reproduces a
    /// fixed band - which is what makes "did the reveal cause this" answerable by setting one number to zero.
    /// It costs a little coverage in the taper and far less than its width suggests, because where the band
    /// is thinnest the whole ramp falls away with the cube - see <c>GrassLod.RevealedFraction</c>.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,0.2,0.005")] public float RevealSpan { get; set; } = 0.06f;

    /// <summary>
    /// How long, in seconds, a cell takes to grow to its full size after it is scattered.
    /// </summary>
    /// <remarks>
    /// <see cref="RevealSpan"/> answers the fade front, which is where grass is revealed while the player
    /// walks. It cannot answer a cell that arrives already inside the band: that one's front is at whatever
    /// its distance deserves on the very first frame, so every blade behind it is fully grown at once.
    ///
    /// <para>
    /// Ordinary walking never does this - the server streams at 176 m and the widest the tuft band ever gets
    /// is about 114 m, so a chunk is scattered long before it can be seen. Logging in, teleporting, and the
    /// re-mesh after a dig all do, and a dig happens under the player's feet.
    /// </para>
    ///
    /// <para>
    /// 0 is the sprout turned off, and a cell then appears the moment it is scattered.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0,3,0.05")] public float AppearSeconds { get; set; } = 0.4f;

    /// <summary>
    /// How long, in milliseconds, one frame may spend scattering newly arrived chunks.
    /// </summary>
    /// <remarks>
    /// Scattering a wholly grassy 32 m chunk at the default density measures at about 3 ms, and
    /// <c>TerrainRenderer.InstallsPerFrame</c> installs two chunks a frame - so without a budget, walking into
    /// a meadow would spend 6 ms a frame on grass alone, on top of the mesh upload that prompted it. This is
    /// what makes <see cref="Density"/> safe to turn up: raising it makes the field arrive over more frames
    /// rather than making those frames longer.
    ///
    /// <para>
    /// Checked between chunks and not within one, so a single chunk can overrun it. A chunk is the smallest
    /// thing that can be scattered at all - the alternative is a half-grassed chunk on screen - and the cap
    /// on <see cref="MaxPerChunk"/> is what bounds how far one can overrun by.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "0.1,16,0.1")] public float BuildBudgetMillis { get; set; } = 1.5f;

    /// <summary>The mesh the whole field is drawn with: <c>grass2</c>'s single tuft, 72 triangles.</summary>
    /// <remarks>
    /// <b>Its V runs the opposite way to <c>grass</c>'s</b>, which is the only reason the material below has to
    /// be duplicated rather than used as authored - see <see cref="UvVAtTip"/>.
    ///
    /// <para>
    /// <c>grass.res</c>, the twelve-blade clump this used to be layered with, is still loaded elsewhere:
    /// <see cref="PropAppearance"/> gives it to every shrub and reed, so the field and the plants standing in
    /// it remain the same art at different sizes.
    /// </para>
    /// </remarks>
    private const string MeshPath = "res://Game/Terrain/Grass/grass2.res";

    /// <summary>
    /// The material the field is drawn with, and the one <see cref="PropAppearance"/> gives the ground cover.
    /// </summary>
    /// <remarks>
    /// Never written to. <see cref="Load"/> takes a duplicate and sets <see cref="UvVAtTip"/> on that, because
    /// setting it here would flip the unwrap direction for every prop in the world along with the field.
    /// </remarks>
    private const string MaterialPath = "res://Game/Terrain/Grass/grass.tres";

    /// <summary>Height, in metres, that <see cref="MeshPath"/>'s mesh stands at above its own origin.</summary>
    /// <remarks><see cref="PropAppearance"/> carried the same number while the herbs were drawn from it.</remarks>
    private const float NaturalHeight = 0.7053f;

    /// <summary>
    /// <c>grass2</c> was unwrapped with V at the base where <c>grass</c> has it at the tip.
    /// </summary>
    /// <remarks>
    /// A fact about the mesh and a <i>material</i> uniform, not a per-instance one, which is why the field
    /// cannot simply use <c>grass.tres</c> as authored. Getting it backwards is visible twice over: the blade
    /// is dark at the tip and bright at the root, and it bends from the wrong end.
    /// </remarks>
    private static readonly StringName UvVAtTip = "uv_v_at_tip";

    /// <summary>The step the coverage scale is rounded to, and so one shared material's worth of it.</summary>
    /// <remarks>
    /// <b>Quantised because the scale is a material uniform now and no longer a per-instance one</b> - see
    /// <see cref="ScaledMaterial"/> for why it had to stop being one. A continuous per-cell scale would want a
    /// material per cell, which is thousands; rounding to a step wants one per step - thirty-two at
    /// <see cref="MaxCoverageScale"/>'s default of 2.6, and sixty at the top of its exported range.
    ///
    /// <para>
    /// A twentieth is the coarsest step that is still invisible where it is spent. The compensation only ever
    /// leaves 1 on a cell the distance has already thinned, so a step is at most 5% of the size of a plant that
    /// is by then tens of metres away - about two pixels at 40 m on a 1080p screen, and less further out. Up
    /// close, where the step would be visible, the scale is pinned at 1 and no rounding happens at all.
    /// </para>
    /// </remarks>
    private const float ScaleStep = 0.05f;

    /// <summary>How far the reveal front has to move before it is worth pushing to the shader.</summary>
    /// <remarks>
    /// A hundredth of the ramp, so no blade's size jumps by more than a percent between pushes. Unlike the
    /// coverage scale this one really does move every frame the player is walking, so the guard saves less -
    /// but a cell that has settled at either end of its taper still stops being written to.
    /// </remarks>
    private const float RevealEpsilon = 0.01f;

    /// <summary>Cull margin, in metres, covering the wind - which moves vertices Godot's bounds know nothing about.</summary>
    /// <remarks>The same margin <see cref="StaticEntityRenderer"/>'s batches carry, for the same reason.</remarks>
    private const float WindCullMargin = 1.2f;

    /// <summary>One cell's worth of grass: a multimesh, and the box its tufts stand in.</summary>
    private sealed class Patch
    {
      internal MultiMeshInstance3D Node;
      internal MultiMesh Multi;

      /// <summary>The box the LOD measures its distance to.</summary>
      internal Vector3 Min;
      internal Vector3 Max;

      internal int Total;

      /// <summary>What <see cref="MultiMesh.VisibleInstanceCount"/> was last set to. -1 until the first pass.</summary>
      internal int Visible = -1;

      /// <summary>
      /// Which <see cref="ScaleStep"/> of coverage compensation this cell's material is currently drawing at.
      /// </summary>
      /// <remarks>
      /// Zero is the neutral scale and is what <see cref="Install"/> assigns, so a cell that is never thinned -
      /// which is most of what the terrain holds - keeps the field's own shared material and is never written
      /// to. It cannot start below zero the way the float it replaces did: that was a sentinel meaning nothing
      /// had been pushed yet, and pushing on the first pass regardless is exactly what spent an instance-uniform
      /// slot on every cell in the view volume, including the ones past the fade radius that draw nothing.
      /// </remarks>
      internal int Level;

      /// <summary>What was last pushed as the reveal front. -1 until the first pass.</summary>
      internal float Front = -1.0f;

      /// <summary>
      /// How far this cell has grown since it was scattered, 0 to 1.
      /// </summary>
      /// <remarks>
      /// Multiplied into the coverage scale rather than into the reveal front, and the difference is what it
      /// looks like: sweeping the front would grow the cell one blade at a time in its own shuffled order,
      /// which reads as a wave crossing the ground. A cell that has just arrived should come up together.
      /// </remarks>
      internal float Appear;

      /// <summary>
      /// This frame's share of the cell, carried between the two passes of <see cref="_Process"/>.
      /// </summary>
      /// <remarks>
      /// Held here rather than in a list beside the patches so the second pass does not measure every cell's
      /// distance again, and so nothing has to be allocated per frame to pair the two up. Already sharpened by
      /// <see cref="GrassLod.Sharpen"/> when it is written, so the second pass has only the budget's own
      /// backstop left to apply.
      /// </remarks>
      internal float Fraction;

      /// <summary>
      /// How full this cell's own tier is at its distance, before any budget was taken out of it.
      /// </summary>
      /// <remarks>
      /// The one number <see cref="GrassLod.CoverageScale"/> is allowed to see. Separate from
      /// <see cref="Fraction"/> so that a frame the budget squeezes cannot change how big a plant is drawn,
      /// which is what made zooming out grow the grass past the player's own height.
      /// </remarks>
      internal float Coverage;

      /// <summary>Which representation this cell is drawing. Set on the first pass of every frame.</summary>
      internal GrassTier Tier = GrassTier.None;

      /// <summary>What <see cref="Tier"/> was when the mesh was last assigned, so it is assigned only on a change.</summary>
      internal GrassTier Drawn = GrassTier.None;

      /// <summary>
      /// Instances this cell's current tier may draw, which is the whole scatter only for the tufts.
      /// </summary>
      /// <remarks>
      /// One crown stands in for <see cref="TuftsPerTussock"/> tufts, so the mid tier draws a short prefix of
      /// the same shuffled order. Every share below is taken against this rather than against
      /// <see cref="Total"/>, which is what keeps the reveal ramp the width of the tier it is ramping.
      /// </remarks>
      internal int Pool;

      /// <summary>What <see cref="InstanceStepParameter"/> was last pushed as. -1 until the first pass.</summary>
      internal float Step = -1.0f;

      /// <summary>This cell's own offset to the tier edges, in cells - see <see cref="GrassLod.CellDither"/>.</summary>
      internal float Dither;
    }

    /// <summary>Chunk -> its cells, so a re-mesh or a withdrawal takes all of them together.</summary>
    private readonly Dictionary<ChunkKey, List<Patch>> _patches = new();

    /// <summary>Cells that have been withdrawn and are shrinking away before they are freed.</summary>
    /// <remarks>Empty almost always: see <see cref="Remove"/> for how little this is asked to do.</remarks>
    private readonly List<Patch> _dying = new();

    /// <summary>
    /// Chunks whose ground has arrived but whose grass has not been scattered yet.
    /// </summary>
    /// <remarks>
    /// Holds the <see cref="ChunkSurface"/> itself, which is the one real cost of deferring: those arrays are
    /// otherwise dropped the moment <see cref="TerrainRenderer"/> has uploaded them, and a login queues the
    /// whole view volume at once. It is transient - the queue drains at
    /// <see cref="BuildBudgetMillis"/> a frame and is empty within a second or two of the terrain settling.
    ///
    /// <para>
    /// A dictionary rather than a queue, so that a chunk re-meshed while still waiting replaces its own entry
    /// instead of being scattered twice from two versions of the same ground.
    /// </para>
    /// </remarks>
    private readonly Dictionary<ChunkKey, ChunkSurface> _pending = new();

    private Godot.Mesh _mesh;

    /// <summary>The mid-tier crown, built once - see <see cref="GrassTussock"/>.</summary>
    private Godot.Mesh _tussock;

    /// <summary>What each tier's mesh actually costs. Counted at load rather than named, so neither can go stale.</summary>
    private int _tuftTriangles = 1;

    private int _tussockTriangles = GrassTussock.Triangles;

    /// <summary>The duplicate of <see cref="MaterialPath"/> with <see cref="UvVAtTip"/> set for this mesh.</summary>
    private Material _material;

    /// <summary>The shared material for each <see cref="ScaleStep"/> above the neutral one.</summary>
    /// <remarks>
    /// Built on demand and then kept, because which steps a session asks for depends on the zoom and on how
    /// grassy the ground is. Bounded by <see cref="MaxCoverageScale"/> over <see cref="ScaleStep"/> -
    /// thirty-two at the default - so it cannot grow with the size of the world the way a material per cell
    /// would.
    /// </remarks>
    private readonly Dictionary<int, ShaderMaterial> _scaled = new();

    private bool _loaded;

    /// <summary>The ramp width the materials were last told about. NaN so the first push always happens.</summary>
    private float _pushedSpan = float.NaN;

    /// <summary>Where the camera is looking, which is where the player is. Null until told - see <see cref="SetFocusAt"/>.</summary>
    private Vector3? _focus;

    /// <summary>
    /// How steeply the field is currently thinning with distance, carried between frames.
    /// </summary>
    /// <remarks>
    /// One is the neutral exponent, which leaves <see cref="GrassLod.FractionAt"/>'s own taper alone. It is
    /// state rather than a per-frame solve because <see cref="GrassLod.NextExponent"/> is a controller - see
    /// there for why fitting the budget exactly has no closed form worth paying for.
    /// </remarks>
    private float _exponent = 1.0f;

    /// <summary>The shader's scale boost, which is a material uniform - see <see cref="ScaledMaterial"/>.</summary>
    private static readonly StringName ExtraScaleParameter = "grass_extra_scale";

    /// <summary>Where the field is centred, for the terrain shader - see <see cref="PublishField"/>.</summary>
    private static readonly StringName FieldFocusParameter = "grass_field_focus";

    /// <summary>Where the far-field ground tint starts ramping in, in metres from the focus.</summary>
    private static readonly StringName FieldBeginParameter = "grass_field_begin";

    /// <summary>Where that tint reaches full strength, which is where the geometry ends.</summary>
    private static readonly StringName FieldEndParameter = "grass_field_end";

    /// <summary>The exponent the field is thinning at, so the tint can give way at the same rate.</summary>
    private static readonly StringName FieldFalloffParameter = "grass_field_falloff";

    /// <summary>How far apart this cell's instances sit in its own order. Pushed once, at install.</summary>
    private static readonly StringName InstanceStepParameter = "grass_instance_step";

    /// <summary>Where this cell's reveal has reached. The one push that moves while the player walks.</summary>
    private static readonly StringName RevealParameter = "grass_reveal";

    /// <summary>The ramp's width. A material uniform, so it is set once for the whole field.</summary>
    private static readonly StringName RevealSpanParameter = "grass_reveal_span";

    /// <summary>
    /// Tells the field where the player is, so the level of detail can be measured from them.
    /// </summary>
    /// <remarks>
    /// <b>Measuring from the camera is what made zooming out empty the field.</b> The spring arm runs 8 m to
    /// 36 m from the player and the fade band spanned 12 m to 40 m, so the two were the same size: at full zoom
    /// the eye was 36 m from the ground under the player's feet, which put it in the last few metres of a fade
    /// that ended at 40 - about a fiftieth of the grass, for ground the player was standing on.
    ///
    /// <para>
    /// Density at a point on the ground is a property of that ground and of where the player is, never of how
    /// far back the camera happens to be sitting. How much ground is *on screen* does depend on the zoom, and
    /// that is answered separately by <see cref="ZoomResponse"/> widening the band.
    /// </para>
    ///
    /// <para>
    /// Called from <c>game.gd</c> beside <c>TerrainRenderer.SetCollisionAnchorAt</c>, off the same
    /// <c>get_owned_entity()</c> the collision anchor already resolves. The zoom is then derived here as the
    /// distance from this point to the camera rather than being plumbed through, so nothing needs to know that
    /// the camera is on a spring arm at all.
    /// </para>
    /// </remarks>
    public void SetFocusAt(Vector3 position) => _focus = position;

    /// <summary>
    /// Replaces this chunk's grass from the terrain surface that was just drawn for it.
    /// </summary>
    /// <remarks>
    /// Called by <see cref="TerrainRenderer"/> on every install, which means a re-mesh takes its grass with it.
    /// That is the opposite of how the props are wired - <c>game.gd</c> keeps those out of the terrain's
    /// lifecycle deliberately, because nothing standing on the ground should be torn down when the ground is
    /// patched - and it is right here for the mirror-image reason: this grass is not standing on the surface,
    /// it is a sample of it, so a surface that changed means a sample that is wrong.
    ///
    /// <para>
    /// The scatter is seeded from the chunk key alone, so a re-mesh puts every tuft back where it was. Only
    /// the ground that actually changed moves.
    /// </para>
    ///
    /// <para>
    /// The old grass goes at once and the new grass is queued - see <see cref="BuildBudgetMillis"/> - so a
    /// re-meshed chunk is briefly bare. That is the right way round: the alternative is grass left standing on
    /// ground that has moved out from under it, and what usually prompts a re-mesh is a few voxels being dug.
    /// </para>
    /// </remarks>
    public void Build(ChunkKey key, ChunkSurface terrain)
    {
      Remove(key, fade: false);

      if (terrain == null || terrain.IsEmpty || Density <= 0.0f || MaxPerChunk <= 0)
      {
        return;
      }

      var vertices = terrain.Vertices;
      var normals = terrain.Normals;
      var weights = terrain.SlotWeights0;

      // No weights, no idea which of this ground is grass. A surface built before the slot channels existed, or
      // by a test that did not fill them, gets no grass rather than grass everywhere.
      if (weights == null || vertices == null || normals == null ||
          weights.Length != vertices.Length * 4 || normals.Length != vertices.Length)
      {
        return;
      }

      if (!Load())
      {
        return;
      }

      // Queued rather than scattered here. See BuildBudgetMillis: this is called from the terrain's install
      // path, twice a frame, and a wholly grassy chunk is milliseconds of work.
      _pending[key] = terrain;
    }

    /// <summary>Scatters one queued chunk and puts its cells on screen.</summary>
    private void Raise(ChunkKey key, ChunkSurface terrain)
    {
      var cells = Scatter(key, terrain.Vertices, terrain.Normals, terrain.Indices, terrain.SlotWeights0);

      if (cells.Count == 0)
      {
        return;
      }

      var patches = new List<Patch>(cells.Count);

      foreach (var (cell, transforms) in cells)
      {
        patches.Add(Install(key, cell, transforms));
      }

      // Published before _Process's passes measure anything, because Drain is only ever called from the top of
      // _Process - so a chunk installed this frame is tuned this frame, and one streaming in a hundred metres
      // away never draws the frame of full-density grass that tuning it here used to prevent.
      _patches[key] = patches;
    }

    /// <summary>
    /// Scatters as many queued chunks as this frame's budget allows, nearest to the camera first.
    /// </summary>
    /// <remarks>
    /// Nearest first because the queue is longest at login, when the whole view volume arrives at once, and
    /// the order it drains in is the order the field appears in. Ground under the player's feet is what they
    /// are looking at; ground out at the fade radius is a handful of tufts whenever it gets there.
    /// </remarks>
    private void Drain(Vector3? eye)
    {
      if (_pending.Count == 0)
      {
        return;
      }

      var budget = Mathf.Max(BuildBudgetMillis, 0.1f);
      var watch = Stopwatch.StartNew();

      while (_pending.Count > 0 && watch.Elapsed.TotalMilliseconds < budget)
      {
        var next = Nearest(eye);
        var terrain = _pending[next];
        _pending.Remove(next);

        Raise(next, terrain);
      }
    }

    /// <summary>The queued chunk closest to the camera, or an arbitrary one when there is no camera.</summary>
    /// <remarks>
    /// A scan rather than a structure kept in order. The queue is at most the view volume - a couple of
    /// hundred entries at login and none once it has settled - and this runs a handful of times a frame, so a
    /// heap maintained across every install would cost more than this costs to walk.
    /// </remarks>
    private ChunkKey Nearest(Vector3? eye)
    {
      var best = default(ChunkKey);
      var bestDistance = float.MaxValue;
      var first = true;

      foreach (var (key, terrain) in _pending)
      {
        if (first)
        {
          best = key;
          first = false;
        }

        if (!eye.HasValue || terrain.Vertices.Length == 0)
        {
          break;
        }

        // The first vertex rather than the surface's middle: a chunk is 32 m across and this is ordering the
        // queue, not measuring it, so any point inside one separates the near from the far.
        var distance = terrain.Vertices[0].DistanceSquaredTo(eye.Value);
        if (distance < bestDistance)
        {
          bestDistance = distance;
          best = key;
        }
      }

      return best;
    }

    /// <summary>Turns one cell's transforms into a multimesh under this node.</summary>
    private Patch Install(ChunkKey key, long cell, List<Transform3D> transforms)
    {
      var multi = new MultiMesh
      {
        TransformFormat = MultiMesh.TransformFormatEnum.Transform3D,
        Mesh = _mesh,
        InstanceCount = transforms.Count
      };

      // Filled through the buffer rather than by SetInstanceTransform per instance, which is one call across
      // the managed boundary each: at a few thousand tufts a chunk and two chunks installed a frame, that was
      // the single most expensive thing about streaming into a meadow. Same data, one call.
      //
      // The layout is the 3x4 matrix in row-major order - three basis rows of three, each followed by one
      // component of the origin. `Basis`'s C# indexer addresses *columns*, so the row-major read is
      // transposed: element [row, column] is `basis[column][row]`.
      var buffer = new float[transforms.Count * 12];

      var min = transforms[0].Origin;
      var max = min;

      for (var i = 0; i < transforms.Count; i++)
      {
        var basis = transforms[i].Basis;
        var origin = transforms[i].Origin;
        var at = i * 12;

        buffer[at + 0] = basis[0].X;
        buffer[at + 1] = basis[1].X;
        buffer[at + 2] = basis[2].X;
        buffer[at + 3] = origin.X;
        buffer[at + 4] = basis[0].Y;
        buffer[at + 5] = basis[1].Y;
        buffer[at + 6] = basis[2].Y;
        buffer[at + 7] = origin.Y;
        buffer[at + 8] = basis[0].Z;
        buffer[at + 9] = basis[1].Z;
        buffer[at + 10] = basis[2].Z;
        buffer[at + 11] = origin.Z;

        min = min.Min(origin);
        max = max.Max(origin);
      }

      multi.Buffer = buffer;

      // The tallest this field's plants stand above the ground they were placed on. The mesh is authored
      // standing on y = 0, so this is the whole of what the transforms do not already record.
      var top = Height * (1.0f + HeightSpread);

      var node = new MultiMeshInstance3D
      {
        Name = $"Grass_{key.X}_{key.Y}_{cell}",
        Multimesh = multi,
        MaterialOverride = _material,
        CastShadow = GeometryInstance3D.ShadowCastingSetting.Off,

        // The wind's margin, plus however far a plant can stand outside the transforms Godot measured the
        // bounds from: the coverage compensation grows it, and a tussock is wider than the tuft the bounds were
        // taken for. Without them a cell pops out of view while its plants are still on screen.
        ExtraCullMargin = WindCullMargin
          + top * (Mathf.Max(MaxCoverageScale, 1.0f) - 1.0f)
          + top * GrassTussock.WidthOverHeight
      };

      AddChild(node);

      // The tuft tier's divisor, pushed here so the common case never pushes again - Retune only revisits it
      // when the cell changes tier. It is the shader's sentinel too: a node that never sets it draws at full
      // size, which is what every ground-cover prop StaticEntityRenderer puts on the ground with this same
      // shader relies on.
      var step = GrassLod.RevealStep(transforms.Count);

      node.SetInstanceShaderParameter(InstanceStepParameter, step);

      return new Patch
      {
        Node = node,
        Multi = multi,
        Min = min,
        Step = step,

        // Grown by the tallest plant, because the transforms only record where each one stands. A box that
        // stopped at the ground would call a cell below the camera further away than its blades are.
        Max = max + new Vector3(0.0f, top, 0.0f),
        Total = transforms.Count,
        Dither = GrassLod.CellDither(cell)
      };
    }

    /// <summary>
    /// Places tufts over the grassy part of one chunk's surface, grouped by cell and shuffled within each.
    /// </summary>
    /// <remarks>
    /// Area-weighted per triangle and slot-weighted per triangle, with the fraction left over carried into the
    /// next one. The carry is what makes a low density work at all: a triangle of a quarter of a square metre
    /// wants 0.6 of a tuft, and rounding that to zero everywhere would leave thin ground bare no matter how
    /// much of it there was.
    ///
    /// <para>
    /// <see cref="BlockAppearance.SurfaceSlot.DryGrass"/> is deliberately not included, though bunchgrass is
    /// still grass. It is a different colour, and this draws one material - so a dune would come out the green
    /// of a meadow. Giving it its own tint is a per-instance colour on the multimesh and a <c>COLOR</c> read in
    /// the shader, which is worth doing and is not done here. The terrain shader's far-field tint is keyed to
    /// the same slot for the same reason, so the two agree about where the field is without being told.
    /// </para>
    /// </remarks>
    private Dictionary<long, List<Transform3D>> Scatter(
      ChunkKey key, Vector3[] vertices, Vector3[] normals, int[] indices, byte[] weights)
    {
      var cells = new Dictionary<long, List<Transform3D>>();

      // Seeded from the chunk alone - not from the mesh - so that a re-mesh, a reconnect or a second client
      // standing in the same field all scatter the same grass. Mixed rather than concatenated because
      // neighbouring chunks differ in one low bit, and a xorshift fed adjacent seeds starts out correlated.
      var rng = new Rng(Mix((uint)(key.X * 73856093) ^ (uint)(key.Y * 19349663) ^ (uint)(key.Z * 83492791)));

      const int Grass = (int)BlockAppearance.SurfaceSlot.Grass;

      var edge = Mathf.Max(CellMetres, 0.5f);

      var carry = 0.0f;
      var density = Density;

      // Two passes, because the cap has to be applied as a density and not as a stopping point. Walking until
      // MaxPerChunk plants had been placed would fill the chunk in the order the mesher emitted its triangles
      // and then stop, which leaves one side of a capped chunk bare - a far more visible failure than the
      // uniformly thinner grass that scaling gives, and one that looks like a bug in the scatter rather than
      // like a budget.
      var grassy = GrassyArea(vertices, normals, indices, weights);

      if (grassy <= 0.0f)
      {
        return cells;
      }

      var asked = grassy * density;
      if (asked > MaxPerChunk)
      {
        density *= MaxPerChunk / asked;
      }

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        var ia = indices[t];
        var ib = indices[t + 1];
        var ic = indices[t + 2];

        // The vertex normals rather than the face's own cross product, because the sign of that depends on a
        // winding this code should not have to know, and getting it backwards would grow grass on the
        // undersides of overhangs and nowhere else.
        var upright = (normals[ia].Y + normals[ib].Y + normals[ic].Y) / 3.0f;
        if (upright < MinUpright)
        {
          continue;
        }

        var grass =
          (weights[ia * 4 + Grass] + weights[ib * 4 + Grass] + weights[ic * 4 + Grass]) / (3.0f * 255.0f);

        if (grass <= 0.0f)
        {
          continue;
        }

        var a = vertices[ia];
        var ab = vertices[ib] - a;
        var ac = vertices[ic] - a;

        var area = ab.Cross(ac).Length() * 0.5f;
        if (area <= 0.0f)
        {
          continue;
        }

        carry += area * grass * density;

        var wanted = (int)carry;
        carry -= wanted;

        for (var n = 0; n < wanted; n++)
        {
          // Uniform over the triangle: the two rolls address the enclosing parallelogram, and folding the
          // far half back over the diagonal is what keeps it uniform rather than piling points into one
          // corner.
          var u = rng.Float();
          var v = rng.Float();
          if (u + v > 1.0f)
          {
            u = 1.0f - u;
            v = 1.0f - v;
          }

          var at = a + ab * u + ac * v;

          var grown = Height * (1.0f + (rng.Float() - 0.5f) * 2.0f * HeightSpread);
          var scale = grown / NaturalHeight;

          // Uniform, so the basis stays a rotation times a scalar - which is the assumption grass.gdshader
          // inverts the model matrix under. Grass is drawn straight up whatever it grows on, rather than
          // along the ground's normal: a plant on a slope grows towards the sky, not out of the hill
          // sideways.
          var basis = new Basis(Vector3.Up, rng.Float() * Mathf.Tau).Scaled(new Vector3(scale, scale, scale));

          var cell = CellOf(at, edge);
          if (!cells.TryGetValue(cell, out var list))
          {
            list = new List<Transform3D>();
            cells[cell] = list;
          }

          list.Add(new Transform3D(basis, at));
        }
      }

      // Shuffled so that VisibleInstanceCount's prefix is a subset of the whole cell rather than of whichever
      // triangles the mesher emitted first. Without this, halving the count on a distant cell would empty one
      // side of it and leave the other at full density.
      foreach (var transforms in cells.Values)
      {
        for (var i = transforms.Count - 1; i > 0; i--)
        {
          var j = (int)(rng.Next() % (uint)(i + 1));
          (transforms[i], transforms[j]) = (transforms[j], transforms[i]);
        }
      }

      return cells;
    }

    /// <summary>
    /// Square metres of this surface that can carry grass, each weighted by how much grass it is.
    /// </summary>
    /// <remarks>
    /// Exactly the quantity <see cref="Scatter"/>'s main loop multiplies by the density, summed ahead of time
    /// so the cap can be turned into a density before any of it is spent. Another pass over the index buffer
    /// and nothing else - no allocation, no random numbers - against a loop that is already walking it.
    /// </remarks>
    private float GrassyArea(Vector3[] vertices, Vector3[] normals, int[] indices, byte[] weights)
    {
      const int Grass = (int)BlockAppearance.SurfaceSlot.Grass;

      var total = 0.0f;

      for (var t = 0; t + 2 < indices.Length; t += 3)
      {
        var ia = indices[t];
        var ib = indices[t + 1];
        var ic = indices[t + 2];

        if ((normals[ia].Y + normals[ib].Y + normals[ic].Y) / 3.0f < MinUpright)
        {
          continue;
        }

        var grass =
          (weights[ia * 4 + Grass] + weights[ib * 4 + Grass] + weights[ic * 4 + Grass]) / (3.0f * 255.0f);

        if (grass <= 0.0f)
        {
          continue;
        }

        var a = vertices[ia];
        total += (vertices[ib] - a).Cross(vertices[ic] - a).Length() * 0.5f * grass;
      }

      return total;
    }

    /// <summary>Which world-aligned cell a point falls in, packed into one key.</summary>
    private static long CellOf(Vector3 at, float edge)
    {
      var x = Mathf.FloorToInt(at.X / edge);
      var z = Mathf.FloorToInt(at.Z / edge);

      return ((long)x << 32) ^ (uint)z;
    }

    /// <summary>
    /// Retunes every cell's instance count and tuft size for where the player and the camera are now.
    /// </summary>
    /// <remarks>
    /// Two passes, and the second one is the work the single pass used to do. The first exists only to total
    /// what the field is asking for, because <see cref="MaxVisibleTriangles"/> is a budget across the whole
    /// field and cannot be spent until it is known how much is competing for it. Each cell's share is kept on
    /// its own <see cref="Patch.Fraction"/> in between, so no distance is measured twice and nothing is
    /// allocated to pair the passes up.
    ///
    /// <para>
    /// <b>Only the cells in the wedge are totalled, and every cell is retuned.</b> Godot culls an off-screen
    /// multimesh for the price of one bounds test, so grass behind the player costs no triangles - but it was
    /// still competing for the budget, which meant the grass on screen was thinned to pay for it. Excluding it
    /// from the sum and not from the retune is what makes turning free: a cell coming into view was tuned all
    /// along, and nothing pops.
    /// </para>
    ///
    /// <para>
    /// Cheap enough to run unconditionally: a subtraction, a power and a rounding per cell, with both
    /// assignments skipped unless the value actually changed - which, at a metre or so of movement, they mostly
    /// do not. A cell outside the fade radius settles on a count of zero and a scale of one and is then never
    /// written to again, which is most of what the terrain holds.
    /// </para>
    /// </remarks>
    public override void _Process(double delta)
    {
      var camera = GetViewport()?.GetCamera3D();
      var eye = camera == null ? (Vector3?)null : camera.GlobalPosition;

      Drain(eye);

      PushRevealSpan();
      Vanish(delta);

      if (_patches.Count == 0 || !eye.HasValue)
      {
        // No field for the ground to match. Published rather than left alone, so the terrain stops being
        // coloured as grass the moment there is no grass on it - on the way out of a scene, say.
        PublishField(Vector3.Zero, 0.0f, 0.0f, 1.0f);
        return;
      }

      // The eye is the fallback for the same reason the camera may be null at all: this node is built before
      // the scene has finished coming up, so the first frames can arrive with no player to measure from.
      // Falling back to the old behaviour draws the field slightly wrong for a frame; refusing to draw it does
      // not.
      var focus = _focus ?? eye.Value;

      var zoom = focus.DistanceTo(eye.Value);
      var band = GrassLod.BandScale(zoom, ReferenceZoomMetres, ZoomResponse, MaxZoomScale);

      var full = FullDensityMetres * band;
      var tuftEnd = Mathf.Max(TuftReachMetres * band, full);

      // Clamped to the terrain that exists. At full zoom the band alone reaches nearly 300 m, where the server
      // streams about 176 and the fog has closed by 220 - so past this the budget would be spent counting cells
      // that were never built.
      var fade = Mathf.Min(FadeOutMetres * band, ReachMetres);

      var perTussock = Mathf.Max(TuftsPerTussock, 1);

      // The band the geometry actually gives up over, and the exponent the passes below are about to thin with
      // rather than the one the controller will settle on: the ground has to match the field being drawn now.
      PublishField(focus, tuftEnd, fade, _exponent);

      var cosHalfAngle = Mathf.Cos(GrassLod.HalfViewAngle(camera.Fov, ViewAspect(camera), ViewMarginDegrees));

      // Godot's cameras look down their own -Z.
      var forward = -camera.GlobalTransform.Basis.Z;

      // The wedge's apex is at the eye, which sits `zoom` metres behind the player - so everything within that
      // radius is in front of the character and on screen whatever its bearing. A cell's width on top, because
      // the test below is against the cell's middle.
      var near = zoom + CellMetres;

      var wanted = 0;

      foreach (var patches in _patches.Values)
      {
        foreach (var patch in patches)
        {
          var distance = GrassLod.DistanceToBox(focus, patch.Min, patch.Max);

          // A cell's own edges, so the ring between two tiers is ragged rather than a circle the eye can find.
          var edge = patch.Dither * CellMetres;

          patch.Tier = GrassLod.TierAt(distance, tuftEnd + edge, fade + edge);

          // Distance only, and it takes no budget argument at all - which is what keeps the frame's budget away
          // from how big a plant is drawn. The count below is this same number thinned to fit.
          patch.Coverage = GrassLod.TierCoverage(patch.Tier, distance, full, tuftEnd + edge, fade + edge);

          patch.Fraction = GrassLod.Sharpen(patch.Coverage, _exponent);

          // One crown stands in for a group of tufts, so the mid tier is the same shuffled order taken as a
          // shorter prefix - no second buffer, and no second node to hold an instance-uniform block forever.
          patch.Pool = patch.Tier switch
          {
            GrassTier.Tuft => patch.Total,
            GrassTier.Tussock => Mathf.Max(patch.Total / perTussock, 1),
            _ => 0
          };

          // Advanced here because this pass already walks every cell, and because it is the one place the
          // frame's own delta is in scope. A cell scattered outside the band grows while it is hidden, so it
          // is already up by the time the player is near enough to see it - so this runs for every cell, and
          // not only for the ones the wedge below is counting.
          patch.Appear = GrassLod.Appear(patch.Appear, 1.0f, (float)delta, AppearSeconds);

          if (GrassLod.InView((patch.Min + patch.Max) * 0.5f, eye.Value, forward, cosHalfAngle, near))
          {
            // What the cell will *draw*, which the ramp puts slightly above its fraction - a plant grown to a
            // fiftieth of its size still costs a vertex shader, so the budget has to see it.
            var visible = Mathf.RoundToInt(
              patch.Pool * GrassLod.DrawnFraction(GrassLod.RevealFront(patch.Fraction, RevealSpan)));

            wanted += visible * TrianglesOf(patch.Tier);
          }
        }
      }

      var trim = GrassLod.BudgetTrim(wanted, MaxVisibleTriangles);

      foreach (var patches in _patches.Values)
      {
        foreach (var patch in patches)
        {
          Retune(patch, trim);
        }
      }

      // Last, and fed this frame's own answer: the exponent used above is what produced `wanted`, so the ratio
      // of the two is exactly the overshoot the controller has to correct. Moving this before the passes would
      // measure the field at one exponent and draw it at another.
      _exponent = GrassLod.NextExponent(_exponent, wanted, MaxVisibleTriangles, MaxExponent, BudgetResponse);
    }

    /// <summary>
    /// Tells the terrain shader where the field is and how far it reaches, so the ground past it can be drawn
    /// as grass.
    /// </summary>
    /// <remarks>
    /// <b>The third tier, and the one that reaches the horizon.</b> There is terrain out to about 176 m and fog
    /// does not finish closing until 220, and no amount of geometry is worth spending out there - so past
    /// <see cref="FadeOutMetres"/> the ground grows its own canopy: the tufts' colour, their grain and a normal
    /// broken up enough to catch the light unevenly. See the far-field block in
    /// <c>terrain_common.gdshaderinc</c>.
    ///
    /// <para>
    /// The band published is where the <i>geometry</i> gives up, which is <see cref="TuftReachMetres"/> to
    /// <see cref="FadeOutMetres"/> and not the tuft band. The exponent goes with it because the band only says
    /// where the geometry may reach; <see cref="GrassLod.Sharpen"/> decides how much of it gets there, and the
    /// shader raises its own ramp to the same power so the ground arrives exactly as fast as the plants leave.
    /// </para>
    ///
    /// <para>
    /// <b>Anchored on the player and not on the camera</b>, which is the whole reason these are pushed from
    /// here rather than read off <c>CAMERA_POSITION_WORLD</c> in the shader. The band published is the one the
    /// field is actually drawing at, zoom scaling included, so the tint and the geometry cannot drift apart
    /// when the spring arm moves.
    /// </para>
    ///
    /// <para>
    /// Globals rather than material uniforms, the arrangement <c>WeatherState</c> already uses: there is one
    /// player and one field, and a material uniform would have to be pushed to the terrain material - which the
    /// debug shader has a second copy of. Three calls a frame, against a walk over thousands of cells.
    /// </para>
    /// </remarks>
    private static void PublishField(Vector3 focus, float begin, float end, float falloff)
    {
      RenderingServer.GlobalShaderParameterSet(FieldFocusParameter, focus);
      RenderingServer.GlobalShaderParameterSet(FieldBeginParameter, begin);
      RenderingServer.GlobalShaderParameterSet(FieldEndParameter, end);
      RenderingServer.GlobalShaderParameterSet(FieldFalloffParameter, falloff);
    }

    /// <summary>
    /// How much wider than it is tall the viewport is, which is what turns a vertical field of view into a
    /// horizontal one.
    /// </summary>
    /// <remarks>
    /// <c>Camera3D.Fov</c> is the vertical angle only while <c>KeepAspect</c> is Keep Height, which is the
    /// default. Under Keep Width it is already the horizontal angle, and the right aspect to hand
    /// <see cref="GrassLod.HalfViewAngle"/> is then 1 - otherwise the wedge would be widened twice.
    /// </remarks>
    private float ViewAspect(Camera3D camera)
    {
      if (camera.KeepAspect == Camera3D.KeepAspectEnum.Width)
      {
        return 1.0f;
      }

      var size = GetViewport().GetVisibleRect().Size;

      return size.Y > 0.0f ? size.X / size.Y : 0.0f;
    }

    /// <summary>
    /// Keeps the shader's copy of the ramp width in step with <see cref="RevealSpan"/>.
    /// </summary>
    /// <remarks>
    /// A material uniform rather than a per-instance one, because it is one number for the whole field. Pushed
    /// from here rather than once at load so that the knob can be swept in the inspector while the game runs -
    /// which is the point of it, since 0 reproduces the old hard pop and makes "did the reveal cause this"
    /// answerable by setting one number to zero. A float compare a frame, and nothing pushed once it settles.
    /// </remarks>
    private void PushRevealSpan()
    {
      if (_material is not ShaderMaterial shader || Mathf.IsEqualApprox(RevealSpan, _pushedSpan))
      {
        return;
      }

      _pushedSpan = RevealSpan;

      shader.SetShaderParameter(RevealSpanParameter, RevealSpan);

      // The compensation levels are duplicates of the material above, so one made after this push already
      // carries the new width - but the ones already cached do not, and a cell drawing at any of them would
      // otherwise keep revealing at whatever the width was when its level was first needed.
      foreach (var scaled in _scaled.Values)
      {
        scaled.SetShaderParameter(RevealSpanParameter, RevealSpan);
      }
    }

    /// <summary>Sets one cell's mesh, instance count and plant size from the share it was given this frame.</summary>
    private void Retune(Patch patch, float trim)
    {
      var fraction = patch.Fraction * trim;

      // Assigned only on a change, and never for a cell drawing nothing: a multimesh re-reads its bounds when
      // its mesh is swapped, and a cell sitting on a tier edge would otherwise do it on every frame the player
      // is moving. A cell going out of range keeps whatever it last drew, so coming back is free.
      if (patch.Tier != patch.Drawn && patch.Tier != GrassTier.None)
      {
        patch.Drawn = patch.Tier;

        var mesh = patch.Tier == GrassTier.Tussock ? _tussock : _mesh;

        if (mesh != null && patch.Multi.Mesh != mesh)
        {
          patch.Multi.Mesh = mesh;
        }
      }

      // Taken from the front rather than from the fraction, so the frontmost blade drawn is the one the ramp
      // has shrunk to nothing. Truncating at the fraction would cut the ramp off halfway down and leave a
      // blade of real size winking out - the pop this is here to remove, only smaller.
      var front = GrassLod.RevealFront(fraction, RevealSpan);
      var visible = Mathf.RoundToInt(patch.Pool * GrassLod.DrawnFraction(front));

      if (visible != patch.Visible)
      {
        patch.Visible = visible;
        patch.Multi.VisibleInstanceCount = visible;

        // Hidden rather than merely emptied. A multimesh with zero visible instances still costs its place in
        // the culling pass, and beyond the fade radius that is most of what the terrain holds.
        patch.Node.Visible = visible > 0;
      }

      // A cell drawing nothing has nobody to read what it would be told. This is most of what the terrain
      // holds - everything past the fade radius - and Patch.Scale starting at -1 means that without this,
      // every one of those cells still pushed once on the frame its chunk arrived.
      if (visible == 0)
      {
        return;
      }

      // The ramp is a width in the cell's own 0-to-1 order, so it has to be told which order that is: the mid
      // tier draws a twenty-fourth of the instances, and a step left at the tuft tier's would put the whole of
      // its prefix inside the ramp and draw every crown stunted.
      var step = GrassLod.RevealStep(patch.Pool);

      if (!Mathf.IsEqualApprox(step, patch.Step))
      {
        patch.Step = step;
        patch.Node.SetInstanceShaderParameter(InstanceStepParameter, step);
      }

      // Compared against a threshold for the same reason the scale below is, and it matters more here: the
      // front follows the player's distance directly, so an exact compare would push for every drawn cell on
      // every frame they are moving.
      if (Mathf.Abs(front - patch.Front) > RevealEpsilon * Mathf.Max(RevealSpan, 0.001f))
      {
        patch.Front = front;
        patch.Node.SetInstanceShaderParameter(RevealParameter, front);
      }

      // Fed the cell's distance-only coverage, never the sharpened-and-trimmed fraction above. A plant's size
      // is a silhouette measured against the player's own character, so it must not move with the frame's
      // budget - which is what made zooming out grow the field past the player's height.
      //
      // Run through the ramp all the same, because the compensation rests on coverage being count times
      // footprint and a blade at a third of its size hides a ninth of the ground.
      var revealed = GrassLod.RevealedFraction(
        GrassLod.RevealFront(patch.Coverage, RevealSpan), RevealSpan);

      var scale = GrassLod.CoverageScale(revealed, CoverageCompensation, MaxCoverageScale) * patch.Appear;

      // Three quarters of a step of dead band around what this cell is already drawing at, rather than a plain
      // round to the nearest step. A cell whose distance leaves it sitting on a step boundary would otherwise
      // flip between two materials on the sub-millimetre jitter the spring arm puts into the band scale, and a
      // whole cell of grass changing size twice a second is far more visible than the 5% a step is worth.
      // Walking crosses the band properly and the level follows.
      if (Mathf.Abs(scale - (1.0f + patch.Level * ScaleStep)) <= ScaleStep * 0.75f)
      {
        return;
      }

      patch.Level = Mathf.RoundToInt((scale - 1.0f) / ScaleStep);
      patch.Node.MaterialOverride = ScaledMaterial(patch.Level);
    }

    /// <summary>
    /// The shared material that draws the field at <paramref name="level"/> steps of compensation.
    /// </summary>
    /// <remarks>
    /// <b>This is where the scale stopped being an <c>instance uniform</c>, and the reason is an engine limit
    /// rather than a preference.</b> Godot gives every instance that has ever been handed an instance uniform a
    /// fixed sixteen-slot block of the one buffer <c>rendering/limits/global_shader_variables/buffer_size</c>
    /// sizes - 65536 entries by default, so 4096 instances for the whole game - and it does not take the block
    /// back until the instance itself is freed. This field is one multimesh per <see cref="CellMetres"/> cell,
    /// so sixteen per chunk, and the server offers an 11x11 chunk view volume: about two thousand nodes before
    /// a single prop, decal or character has asked for one. <see cref="Retune"/> pushed a scale to every one of
    /// them on its first pass - including the cells past the fade radius, whose scale is the neutral 1 - so the
    /// buffer ran dry within seconds of login, and from then on
    /// <c>global_shader_parameters_instance_allocate</c> returned -1 and every newly raised cell drew at its
    /// authored size for the rest of the session.
    ///
    /// <para>
    /// A material uniform costs nothing from that buffer, and the price of the swap is that the scale has to be
    /// shared: one material per <see cref="ScaleStep"/> rather than one float per cell. Thirty-two at the
    /// default ceiling, against four thousand slots - and see <see cref="ScaleStep"/> for why the rounding is
    /// invisible where it is spent.
    /// </para>
    ///
    /// <para>
    /// Level zero is the field's own material rather than a copy of it, so the common case allocates nothing
    /// and <see cref="Install"/> can hand out the neutral material without asking. The copies are taken from
    /// that material and never write to it, which matters because it is itself a duplicate of
    /// <see cref="MaterialPath"/> - the resource <see cref="PropAppearance"/> gives every shrub and reed.
    /// </para>
    /// </remarks>
    private Material ScaledMaterial(int level)
    {
      // Under the neutral scale is not a state CoverageScale can reach - it never returns below 1 - so a
      // negative level is a rounding artefact rather than a smaller plant, and nothing is gained by giving it a
      // material of its own.
      if (level <= 0 || _material is not ShaderMaterial shader)
      {
        return _material;
      }

      if (_scaled.TryGetValue(level, out var cached))
      {
        return cached;
      }

      var own = (ShaderMaterial)shader.Duplicate();
      own.SetShaderParameter(ExtraScaleParameter, level * ScaleStep);
      _scaled[level] = own;

      return own;
    }

    /// <summary>
    /// Takes one chunk's grass away, either at once or over <see cref="AppearSeconds"/>.
    /// </summary>
    /// <remarks>
    /// <b>A withdrawal fades; a replacement does not.</b> The two callers want opposite things.
    /// <see cref="Build"/> removes a chunk because it is about to scatter the same chunk again, and the
    /// scatter is seeded from the chunk key alone - so the new blades land in the very same spots as the old
    /// ones, and a fade there reads as doubled grass rather than as a transition. Bare ground for a frame or
    /// two is the right answer there and is the one <see cref="Build"/> already argues for.
    ///
    /// <para>
    /// Worth being straight about what the fade buys today: nothing visible. A chunk leaves the manifest at
    /// 160 m at the closest, and the widest the tuft band ever reaches is about 114 m, so every withdrawal
    /// already lands on a cell that was drawing nothing. It is here against the view radius being lowered,
    /// which is the server's number to change.
    /// </para>
    /// </remarks>
    public void Remove(ChunkKey key, bool fade)
    {
      _pending.Remove(key);

      if (!_patches.Remove(key, out var patches))
      {
        return;
      }

      if (fade && AppearSeconds > 0.0f)
      {
        // Unkeyed on purpose. A chunk that streams back in while its old cells are still going out installs
        // fresh nodes that know nothing about them, so there is no collision to resolve and no key to hold -
        // only two nodes wanting the same name, which Godot settles by renaming one.
        //
        // Build never gets here, so a re-mesh still frees its old cells outright.
        _dying.AddRange(patches);

        return;
      }

      foreach (var patch in patches)
      {
        Free(patch);
      }
    }

    /// <summary>Frees one cell's node, if the engine has not already.</summary>
    private static void Free(Patch patch)
    {
      if (IsInstanceValid(patch.Node))
      {
        patch.Node.QueueFree();
      }
    }

    /// <summary>
    /// Shrinks the cells that are on their way out, and frees the ones that have got there.
    /// </summary>
    /// <remarks>
    /// They keep the fraction they were last measured at rather than being measured again - what they are
    /// doing is leaving, not tracking the player - and they stay out of the frame's budget, because a fraction
    /// of a second of overshoot is not worth the accounting.
    /// </remarks>
    private void Vanish(double delta)
    {
      for (var i = _dying.Count - 1; i >= 0; i--)
      {
        var patch = _dying[i];

        patch.Appear = GrassLod.Appear(patch.Appear, 0.0f, (float)delta, AppearSeconds);

        if (patch.Appear <= 0.0f)
        {
          Free(patch);
          _dying.RemoveAt(i);

          continue;
        }

        Retune(patch, 1.0f);
      }
    }

    public void Clear()
    {
      _pending.Clear();

      foreach (var key in new List<ChunkKey>(_patches.Keys))
      {
        Remove(key, fade: false);
      }

      // Including whatever was still on its way out. A world change is not a withdrawal to be eased.
      foreach (var patch in _dying)
      {
        Free(patch);
      }

      _dying.Clear();
    }

    /// <summary>What one instance of a tier costs, for the budget in <see cref="_Process"/>.</summary>
    private int TrianglesOf(GrassTier tier) => tier == GrassTier.Tussock ? _tussockTriangles : _tuftTriangles;

    /// <summary>Loads the mesh and material once. False if the mesh could not be loaded.</summary>
    /// <remarks>
    /// A failure is remembered, not retried. The alternative is a file-not-found for every chunk that streams
    /// in, which is the log for the rest of the session.
    /// </remarks>
    private bool Load()
    {
      if (_loaded)
      {
        return _mesh != null;
      }

      _loaded = true;

      _mesh = ResourceLoader.Load<Godot.Mesh>(MeshPath);
      if (_mesh == null)
      {
        GD.PushError($"[grass] {MeshPath} did not load; the ground will have no grass on it.");
        return false;
      }

      // Built rather than loaded, so its width and raggedness are constants next to the tier that uses them
      // instead of a mesh nobody can adjust without Blender.
      _tussock = GrassTussock.Build(NaturalHeight);

      _tuftTriangles = Mathf.Max(_mesh.GetFaces().Length / 3, 1);
      _tussockTriangles = Mathf.Max(_tussock.GetFaces().Length / 3, 1);

      var authored = ResourceLoader.Load<ShaderMaterial>(MaterialPath);
      if (authored == null)
      {
        GD.PushError($"[grass] {MaterialPath} did not load; the grass keeps the mesh's own material.");
        return true;
      }

      // Its own copy, with the one flag that is a fact about the mesh flipped - the same duplicate-and-set
      // StaticEntityRenderer.MaterialFor does per kind, and for the same reason: `uv_v_at_tip` is a material
      // uniform, and grass.tres is the very resource PropAppearance hands every shrub and reed. Setting it in
      // place would unwrap all of them backwards. One duplicate for the whole field, so it is still a single
      // material however many cells carry it.
      var own = (ShaderMaterial)authored.Duplicate();
      own.SetShaderParameter(UvVAtTip, true);
      _material = own;

      return true;
    }

    /// <summary>
    /// The scatter's random source: xorshift32, inlined so a chunk's worth of rolls allocates nothing.
    /// </summary>
    /// <remarks>
    /// Not <see cref="RandomNumberGenerator"/>, and not <c>System.Random</c>. What is wanted here is that the
    /// same chunk scatters the same grass on every client and after every re-mesh, forever - and neither of
    /// those documents its sequence as stable across versions. Six lines that do is the cheaper guarantee.
    /// </remarks>
    private struct Rng
    {
      private uint _state;

      internal Rng(uint seed)
      {
        _state = seed == 0u ? 0x9E3779B9u : seed;
      }

      internal uint Next()
      {
        _state ^= _state << 13;
        _state ^= _state >> 17;
        _state ^= _state << 5;

        return _state;
      }

      /// <summary>A float in [0, 1). The top 24 bits, because xorshift's low ones are the weakest.</summary>
      internal float Float() => (Next() >> 8) * (1.0f / 16777216.0f);
    }

    /// <summary>Avalanches a seed, so that chunks whose keys differ in one bit do not scatter alike.</summary>
    private static uint Mix(uint x)
    {
      x ^= x >> 16;
      x *= 0x7FEB352Du;
      x ^= x >> 15;
      x *= 0x846CA68Bu;
      x ^= x >> 16;

      return x;
    }
  }
}
