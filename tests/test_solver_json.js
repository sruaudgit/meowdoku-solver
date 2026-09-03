// Inline solver logic (from js/solver.js) — same as Kotlin port
const GridSolver = (() => {
  function checkConsistency(symbols, n) {
    for (let a = 0; a < symbols.length; a++) {
      for (let b = a + 1; b < symbols.length; b++) {
        const s = symbols[a], t = symbols[b];
        if (s.row === t.row) throw new Error(" deux symboles ligne " + s.row);
        if (s.col === t.col) throw new Error(" deux symboles col " + s.col);
        if (s.color === t.color) throw new Error(" deux symboles couleur " + s.color);
        if (Math.abs(s.row - t.row) <= 1 && Math.abs(s.col - t.col) <= 1)
          throw new Error(" adjacents (" + s.row + "," + s.col + ") (" + t.row + "," + t.col + ")");
      }
    }
  }

  function findSolution(grid, fixedSymbols) {
    const n = grid.size;
    checkConsistency(fixedSymbols, n);

    const fixedRow = new Array(n).fill(null);
    const usedCols = new Set();
    const usedColors = new Set();
    for (const s of fixedSymbols) {
      fixedRow[s.row] = s;
      usedCols.add(s.col);
      usedColors.add(s.color);
    }

    const placed = [];
    const rows = [];
    for (let r = 0; r < n; r++) rows.push(r);
    rows.sort((a, b) => {
      if (fixedRow[a] && !fixedRow[b]) return -1;
      if (!fixedRow[a] && fixedRow[b]) return 1;
      return candidateCount(grid, a) - candidateCount(grid, b);
    });

    function candidateCount(g, r) {
      let c = 0;
      for (let j = 0; j < n; j++) if (feasible(g, r, j)) c++;
      return c;
    }

    function feasible(g, r, c) {
      if (usedCols.has(c)) return false;
      if (usedColors.has(g.cells[r][c].color)) return false;
      for (const p of placed) {
        if (Math.abs(p.row - r) <= 1 && Math.abs(p.col - c) <= 1) return false;
      }
      return true;
    }

    function backtrack() {
      if (placed.length === n) return true;
      const r = rows[placed.length];
      const fixed = fixedRow[r];
      if (fixed) {
        placed.push(fixed);
        if (backtrack()) return true;
        placed.pop();
        return false;
      }
      for (let c = 0; c < n; c++) {
        if (!feasible(grid, r, c)) continue;
        placed.push({ row: r, col: c, color: grid.cells[r][c].color });
        usedCols.add(c);
        usedColors.add(grid.cells[r][c].color);
        if (backtrack()) return true;
        placed.pop();
        usedCols.delete(c);
        usedColors.delete(grid.cells[r][c].color);
      }
      return false;
    }

    const ok = backtrack();
    if (!ok) return null;
    return placed.slice();
  }

  return { findSolution };
})();

const input = JSON.parse(Deno.readTextFileSync("Samples/grid_9x9-01.json"));
const solved = JSON.parse(Deno.readTextFileSync("Samples/grid_9x9-01-solved.json"));

const n = input.size;
const cells = input.cells.map((row, i) =>
  row.map((c, j) => ({ row: i, col: j, color: c.color, state: "free", hex: "" }))
);
const grid = { size: n, cells };

const solution = GridSolver.findSolution(grid, []);

console.log("=== Solution JS ===");
for (const s of solution.sort((a,b) => a.row - b.row)) {
  console.log(`  row=${s.row} col=${s.col} color=${s.color}`);
}

const colorsUsed = solution.map(s => s.color).sort();
const colorsExpected = solved.detectedSymbols.map(s => s.color).sort();
console.log("\nCouleurs JS:", colorsUsed);
console.log("Couleurs attendues:", colorsExpected);
console.log("Mêmes couleurs:", JSON.stringify(colorsUsed) === JSON.stringify(colorsExpected) ? "OUI" : "NON");

const expected = solved.detectedSymbols;
const match = expected.length === solution.length &&
  solution.every(s => expected.some(e => e.row === s.row && e.col === s.col && e.color === s.color));
console.log("\nRésultat:", match ? "OK" : "DIFFÉRENCE");
