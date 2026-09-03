// Test de régression : charge le vrai color.js + detector.js, exécute GridDetector.detect
// sur l'image sample (pixels RGB extraits par dump_rgba.py) et compare au résultat attendu.
// Usage : deno run --allow-read tests/deno_test.js
// (nécessite le fichier rgba généré par tests/dump_rgba.py)

const colorCode = await Deno.readTextFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/js/color.js");
const detCode = await Deno.readTextFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/js/detector.js");
const solveCode = await Deno.readTextFile("C:/Users/notre/Documents/Code/web/meowdoku-solver/js/solver.js");
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
const GridSolver = new Function(solveCode + "\nreturn GridSolver;")();

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

// ---- Solver / déduction ----
// Construction de la grille au format attendu par GridSolver
const solverGrid = {
  size: grid.size,
  cells: grid.cells // cells[i][j].color existe
};

const symbols = GridSolver.collectSymbols(solverGrid);
const manual = GridSolver.createManualMap(solverGrid);

// Avec uniquement le symbole (9,9) couleur 10 posé, aucune croix manuelle :
// doivent être 'impossible-auto' : ligne 9, colonne 9, 8-voisinage de (9,9),
// et toutes les cases de couleur 10 (hors (9,9)). Le reste : 'free'.
const states = GridSolver.derive(solverGrid, symbols, manual);

// Calcule la référence attendue pour l'état impossible-auto
function expectedCovered(i, j) {
  const r = 9, c = 9, col = 10;
  if (i === r && j === c) return false; // case du symbole
  if (i === r) return true;          // ligne
  if (j === c) return true;          // colonne
  if (solverGrid.cells[i][j].color === col) return true; // couleur
  if (Math.abs(i - r) <= 1 && Math.abs(j - c) <= 1) return true; // 8-voisinage
  return false;
}

for (let i = 0; i < 10; i++) {
  for (let j = 0; j < 10; j++) {
    const isSym = (i === 9 && j === 9);
    if (isSym) {
      if (states[i][j] !== "symbol") { console.log(`FAIL sym state (${i},${j}): ${states[i][j]}`); ok = false; }
    } else if (expectedCovered(i, j)) {
      if (states[i][j] !== "impossible-auto") { console.log(`FAIL auto (${i},${j}): ${states[i][j]}`); ok = false; }
    } else {
      if (states[i][j] !== "free") { console.log(`FAIL free (${i},${j}): ${states[i][j]}`); ok = false; }
    }
  }
}
console.log("symbol count:", symbols.length);

// Test interaction manuelle : marquer manuellement puis ajouter un symbole qui couvre, retirer.
// 1) marque manuelle sur (0,0)
manual[0][0] = true;
let st = GridSolver.derive(solverGrid, symbols, manual);
if (st[0][0] !== "impossible-manual") { console.log("FAIL manual (0,0):", st[0][0]); ok = false; }

// 2) retirer le symbole (9,9) : (0,0) doit rester manuel
const reduced = symbols.filter(s => !(s.row === 9 && s.col === 9));
st = GridSolver.derive(solverGrid, reduced, manual);
if (st[0][0] !== "impossible-manual") { console.log("FAIL manual persists (0,0):", st[0][0]); ok = false; }
// (9,9) + sa ligne/col/couleur/vie sont désormais libres (sauf manuel)
if (st[9][8] !== "free") { console.log("FAIL neighbor freed (9,8):", st[9][8]); ok = false; }

// 3) aucune croix manuelle : retirer le symbole libère tout
const manualEmpty = GridSolver.createManualMap(solverGrid);
st = GridSolver.derive(solverGrid, reduced, manualEmpty);
let anyNonFree = false;
for (let i = 0; i < 10; i++) for (let j = 0; j < 10; j++) if (st[i][j] !== "free") anyNonFree = true;
if (anyNonFree) { console.log("FAIL all free after symbol removal"); ok = false; }

// 4) deux symboles couvrant une même case : retirer l'un garde l'autre
const sA = { id: 1, row: 0, col: 0, color: 1 };
const sB = { id: 2, row: 0, col: 5, color: 2 };
// case (0,1) couverte par ligne de A et de B
let st2 = GridSolver.derive(solverGrid, [sA, sB], manualEmpty);
if (st2[0][1] !== "impossible-auto") { console.log("FAIL covered by two:", st2[0][1]); ok = false; }
st2 = GridSolver.derive(solverGrid, [sB], manualEmpty);
if (st2[0][1] !== "impossible-auto") { console.log("FAIL still covered by B:", st2[0][1]); ok = false; }

// ---- findSolution ----
// Résoud la grille sample avec le symbole fixe (9,9) comme seul symbole posé.
const fixed = GridSolver.collectSymbols(solverGrid); // = le symbole (9,9)
const sol = GridSolver.findSolution(solverGrid, []);
const solWithFixed = GridSolver.findSolution(solverGrid, fixed);

if (!sol) { console.log("FAIL no solution"); ok = false; }
if (!solWithFixed) { console.log("FAIL no solution with fixed"); ok = false; }

function validateSolution(solCells, n) {
  if (solCells.length !== n) return "len=" + solCells.length;
  const rows = new Set(), cols = new Set(), colors = new Set();
  for (const s of solCells) {
    if (rows.has(s.row)) return "dup row " + s.row;
    if (cols.has(s.col)) return "dup col " + s.col;
    if (colors.has(s.color)) return "dup color " + s.color;
    rows.add(s.row); cols.add(s.col); colors.add(s.color);
  }
  // couleurs = toutes distinctes (n couleurs, n symboles -> permutation)
  // adjacence
  for (let a = 0; a < n; a++) {
    for (let b = a + 1; b < n; b++) {
      if (Math.abs(solCells[a].row - solCells[b].row) <= 1 &&
          Math.abs(solCells[a].col - solCells[b].col) <= 1) {
        return "adjacent " + a + "," + b;
      }
    }
  }
  return null;
}

for (const [name, s] of [["empty", sol], ["withFixed", solWithFixed]]) {
  if (s) {
    const err = validateSolution(s, grid.size);
    if (err) { console.log(`FAIL ${name} solution invalid: ${err}`); ok = false; }
    else console.log(`${name}: solution valide`);
  }
}

// avec fixe : (9,9) doit être présent
if (solWithFixed) {
  const present = solWithFixed.some(s => s.row === 9 && s.col === 9 && s.color === 10);
  if (!present) { console.log("FAIL fixed (9,9) not in solution"); ok = false; }
  console.log("solution fixe:", JSON.stringify(solWithFixed));
}

// cohérence : deux symboles sur la même ligne doivent lever une erreur
try {
  GridSolver.findSolution(solverGrid, [ {row:0,col:1,color:1}, {row:0,col:2,color:2} ]);
  console.log("FAIL no error on inconsistent rows"); ok = false;
} catch (e) {
  if (!/incoh/.test(e.message)) { console.log("FAIL wrong error msg:", e.message); ok = false; }
}

console.log("bbox:", JSON.stringify(grid.boundingBox));
console.log(ok ? "PASS" : "FAIL");
