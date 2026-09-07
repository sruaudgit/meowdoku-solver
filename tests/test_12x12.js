"use strict";

const colorCode = await Deno.readTextFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/js/color.js");
const detCode = await Deno.readTextFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/js/detector.js");
const raw = await Deno.readFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/tests/sample_12x12.rgba");

const rgba = new Uint8ClampedArray(Math.floor(raw.length / 3) * 4);
for (let i = 0, j = 0; i < raw.length; i += 3, j += 4) {
  rgba[j] = raw[i]; rgba[j + 1] = raw[i + 1]; rgba[j + 2] = raw[i + 2]; rgba[j + 3] = 255;
}

const W = 1080, H = 2340;
const image = {
  width: W, height: H, data: rgba,
  get(x, y) { const i = (y * W + x) * 4; return [rgba[i], rgba[i + 1], rgba[i + 2]]; }
};

const ColorUtil = new Function(colorCode + "\nreturn ColorUtil;")();
globalThis.ColorUtil = ColorUtil;
const GridDetector = new Function(detCode + "\nreturn GridDetector;")();

try {
  const grid = GridDetector.detect(image);
  console.log("size:", grid.size);
  console.log("boundingBox:", JSON.stringify(grid.boundingBox));
  console.log("symbolCount:", grid.symbolCount);
  console.log("timing:", JSON.stringify(grid.timing));
  console.log("colorMap:", JSON.stringify(grid.colorMap));
  if (grid.size === 12) {
    console.log("SUCCESS: 12x12 detecté correctement");
  } else {
    console.log("WARNING: taille=", grid.size, "(attendu 12)");
  }
} catch (e) {
  console.log("ERREUR:", e.message);
}
