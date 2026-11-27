"""Generates the terrain slot 12 maps: the Withering's creep, grey slime veined over the ground.

No texture pack has this look, so it is made here, tileable by construction: every noise is periodic on the
texture's edge. Run from the repo root:

    python3 tools/client/generate_creep_slot.py
"""

import numpy as np
from PIL import Image

SIZE = 512
OUT = "bestia-client/src/Game/World/Shader/Slots/12_corrupted_{}.png"


def periodic_noise(rng, falloff):
    """Random noise shaped in frequency space, so it repeats exactly on the texture's edge."""
    white = rng.standard_normal((SIZE, SIZE))
    fx = np.fft.fftfreq(SIZE)[None, :]
    fy = np.fft.fftfreq(SIZE)[:, None]
    radius = np.sqrt(fx * fx + fy * fy)
    radius[0, 0] = 1.0
    shaped = np.real(np.fft.ifft2(np.fft.fft2(white) * radius ** -falloff))
    return (shaped - shaped.min()) / (shaped.max() - shaped.min())


def ridges(rng, falloff, sharpness):
    """Thin wandering lines where smooth noise crosses its middle: veins rather than cracks."""
    return (1.0 - np.abs(periodic_noise(rng, falloff) * 2.0 - 1.0)) ** sharpness


def smoothstep(edge0, edge1, value):
    t = np.clip((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def save_grey(name, values):
    Image.fromarray((np.clip(values, 0, 1) * 255).astype(np.uint8), "L").save(OUT.format(name))


def main():
    rng = np.random.default_rng(1261)

    veins = np.maximum(ridges(rng, 2.2, 14), 0.6 * ridges(rng, 1.8, 20))
    pools = smoothstep(0.58, 0.68, periodic_noise(rng, 2.6))
    grain = periodic_noise(rng, 0.6)
    lumps = periodic_noise(rng, 1.8)

    height = np.clip(0.35 + 0.35 * lumps + 0.25 * veins - 0.25 * pools + 0.05 * grain, 0, 1)

    # Slime is wet and glossy, the dried crust between the pools is not.
    roughness = np.clip(0.75 - 0.5 * pools - 0.2 * veins + 0.1 * grain, 0.05, 1)

    # Near grey: the shader divides the texture's own colour out and lays `mark_corrupted_colour` over it, so
    # only the light and dark of this survives.
    base = 0.42 + 0.06 * (lumps - 0.5) + 0.06 * (grain - 0.5)
    shade = base * (1.0 - 0.25 * veins) * (1.0 - 0.05 * pools)
    albedo = np.stack([shade * 0.97, shade, shade * 0.96], axis=-1)
    Image.fromarray((np.clip(albedo, 0, 1) * 255).astype(np.uint8), "RGB").save(OUT.format("albedo"))

    save_grey("height", height)
    save_grey("roughness", roughness)

    # Tangent normal, OpenGL convention (+Y up), from the wrapped height gradient.
    strength = 6.0
    gx = (np.roll(height, -1, axis=1) - np.roll(height, 1, axis=1)) * strength
    gy = (np.roll(height, -1, axis=0) - np.roll(height, 1, axis=0)) * strength
    normal = np.stack([-gx, gy, np.ones_like(gx)], axis=-1)
    normal /= np.linalg.norm(normal, axis=-1, keepdims=True)
    Image.fromarray(((normal * 0.5 + 0.5) * 255).astype(np.uint8), "RGB").save(OUT.format("normal"))


if __name__ == "__main__":
    main()
