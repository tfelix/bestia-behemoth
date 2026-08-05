# Third-party work

Bestia Behemoth is licensed under the [GNU General Public License v3.0](LICENSE). This file lists third-party
work the project incorporates or derives from, so that the obligation attached to each is recorded in one place
rather than only in the file that happens to carry it.

Ordinary build-time dependencies resolved by Gradle or NuGet are not listed here — this file is for code and
data that was *copied into* or *derived from* another project, which is the case the GPL asks us to document.

---

## Medieval Fantasy City Generator (TownGeneratorOS)

- **Author:** watabou
- **Source:** <https://github.com/watabou/TownGeneratorOS>
- **Licence:** GNU General Public License v3.0
- **Used by:** `worldgen/src/main/kotlin/net/bestia/worldgen/civ/` — the settlement layout stage

The town layout in `worldgen` follows this project's approach: a partition of the built-up area into patches, a quarter type assigned per patch with its own grain, recursive subdivision of a patch into building plots, and a wall circuit traced from the patch boundary rather than drawn as a circle.

Most of it is an independent implementation against this repository's own geometry types (`vector/Vec2d`,
`Ring`, `Polyline`, `fields/PoissonDisk`) rather than a translation of the Haxe. Where a file *is* derived from
a reference file, that file carries its own header naming this project, the source URL, and the fact that it was modified — as GPL-3.0 §5(a) requires. Both projects are GPL-3.0, so the licences are compatible.
