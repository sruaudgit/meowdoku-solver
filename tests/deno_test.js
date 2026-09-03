// Test de régression : charge le vrai color.js + detector.js, exécute GridDetector.detect
// sur l'image sample (pixels RGB extraits par dump_rgba.py) et compare au résultat attendu.
// Usage : deno run --allow-read tests/deno_test.js
// (nécessite le fichier rgba généré par tests/dump_rgba.py)

const colorCode = await Deno.readTextFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/js/color.js");
const detCode = await Deno.readTextFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/js/detector.js");
const raw = await Deno.readFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/tests/sample.rgba");

const rgba = new Uint8ClampedArray(Math.floor(raw.length / 3) * 4);
for (let i = 0, j = 0; i < raw.length; i += 3, j += 4) {
  rgba[j] = raw[i]; rgba[j + 1] = raw[i + 1]; rgba[j + 2] = raw[i + 2]; rgba[j + 3] = 255;
}

const W = 1080, H = 2340;
const image = {
  width: W, height: H, data: rgba,
  get(x, y) {
    const i = (y * W + x) * 4;
    return [rgba[i], rgba[i + 1], rgba[i + 2]];
  }
};

const ColorUtil = new Function(colorCode + "\nreturn ColorUtil;")();
globalThis.ColorUtil = ColorUtil;
const GridDetector = new Function(detCode + "\nreturn GridDetector;")();

const grid = GridDetector.detect(image);

const EXPECTED = [
  [1,1,2,2,2,2,3,3,3,3],
  [1,1,1,4,2,2,4,3,5,3],
  [1,1,4,4,4,4,4,3,5,3],
  [1,1,1,4,4,6,4,4,5,3],
  [1,1,1,7,7,6,4,5,5,3],
  [1,1,1,1,7,6,4,5,8,8],
  [7,7,7,1,7,6,6,5,5,8],
  [7,7,7,7,7,7,6,5,5,8],
  [9,6,6,6,6,6,6,5,8,8],
  [9,6,6,6,6,6,6,8,8,10],
];

let ok = true;
if (grid.size !== 10) { console.log("FAIL size:", grid.size); ok = false; }
for (let i = 0; i < 10; i++) {
  for (let j = 0; j < 10; j++) {
    const cell = grid.cells[i][j];
    if (cell.color !== EXPECTED[i][j]) { console.log(`FAIL color (${i},${j}) got ${cell.color} exp ${EXPECTED[i][j]}`); ok = false; }
    const wantSymbol = i === 9 && j === 9;
    if ((cell.state === "symbol") !== wantSymbol) { console.log(`FAIL symbol (${i},${j}) got ${cell.state}`); ok = false; }
  }
}
if (grid.symbolCount !== 1) { console.log("FAIL symbolCount:", grid.symbolCount); ok = false; }
const bb = grid.boundingBox;
console.log("bbox:", JSON.stringify(bb));
console.log(ok ? "PASS" : "FAIL");
