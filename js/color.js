"use strict";

const ColorUtil = (() => {
  // Conversion RGB (0-255) -> CIELAB (D65), pour Delta E 76.
  function rgbToLab(r, g, b) {
    const R = srgb(r), G = srgb(g), B = srgb(b);

    // sRGB linéaire -> XYZ (D65)
    let x = (R * 0.4124564 + G * 0.3575761 + B * 0.1804375) / 0.95047;
    let y = (R * 0.2126729 + G * 0.7151522 + B * 0.0721750) / 1.00000;
    let z = (R * 0.0193339 + G * 0.1191920 + B * 0.9503041) / 1.08883;

    x = f(x);
    y = f(y);
    z = f(z);

    return {
      L: 116 * y - 16,
      a: 500 * (x - y),
      b: 200 * (y - z)
    };
  }

  function srgb(c) {
    c = c / 255;
    return c <= 0.04045
      ? c / 12.92
      : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  function f(t) {
    return t > 0.008856
      ? Math.cbrt(t)
      : (903.3 * t + 16) / 116;
  }

  // Delta E 76 : distance euclidienne dans L*a*b*.
  function deltaE(lab1, lab2) {
    const dL = lab1.L - lab2.L;
    const da = lab1.a - lab2.a;
    const db = lab1.b - lab2.b;
    return Math.sqrt(dL * dL + da * da + db * db);
  }

  function deltaEfromRGB(rgb1, rgb2) {
    return deltaE(rgbToLab(rgb1[0], rgb1[1], rgb1[2]),
                  rgbToLab(rgb2[0], rgb2[1], rgb2[2]));
  }

  // Comparaison "égales" si deltaE < 2.
  function equal(c1, c2, threshold = 2) {
    return deltaEfromRGB(c1, c2) < threshold;
  }

  function toHex(rgb) {
    return "#" + rgb.map(c => Math.max(0, Math.min(255, Math.round(c)))
      .toString(16).padStart(2, "0")).join("");
  }

  function rgbKey(rgb) {
    return Math.round(rgb[0]) + "," + Math.round(rgb[1]) + "," + Math.round(rgb[2]);
  }

  return { rgbToLab, deltaE, deltaEfromRGB, equal, toHex, rgbKey };
})();
