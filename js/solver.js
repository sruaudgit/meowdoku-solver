"use strict";

// Solver / déduction des contraintes Meowdoku.
// À partir des symboles posés, calcule pour chaque case son état affiché :
//   'symbol' | 'impossible-manual' | 'impossible-auto' | 'free'
//
// Règles de couverture automatique d'une case (i,j) par un symbole S(r,c,col) :
//   - même ligne (i === r)
//   - même colonne (j === c)
//   - même couleur (grid<i,j>.color === col)
//   - voisinage 8 (|i-r| <= 1 et |j-c| <= 1)
//   exception : la case du symbole elle-même n'est pas "couverte".
//
// Une case marquée manuellement (manualImpossible) reste impossible même si
// les symboles qui la couvraient disparaissent.

const GridSolver = (() => {

  // Récupère les symboles posés depuis une grille détectée (state === 'symbol').
  function collectSymbols(grid, nextId = 1) {
    const symbols = [];
    for (let i = 0; i < grid.size; i++) {
      for (let j = 0; j < grid.size; j++) {
        const c = grid.cells[i][j];
        if (c.state === 'symbol') {
          symbols.push({ id: nextId++, row: i, col: j, color: c.color });
        }
      }
    }
    return symbols;
  }

  // Calcule l'état affiché de chaque case.
  // grid : { size, cells: [[{color, hex, ...}]] }
  // symbols : [{id,row,col,color}]
  // manual : si fourni, boolean[row][col] indiquant les croix manuelles.
  // Retourne un tableau 2D d'états : 'symbol'|'impossible-manual'|'impossible-auto'|'free'.
  function derive(grid, symbols, manual) {
    const n = grid.size;
    const manualMap = manual || createManualMap(grid);
    const states = new Array(n);
    for (let i = 0; i < n; i++) states[i] = new Array(n).fill('free');

    // marque les symboles
    for (const s of symbols) {
      states[s.row][s.col] = 'symbol';
    }

    // marque les couvertures automatiques
    const covered = new Array(n);
    for (let i = 0; i < n; i++) covered[i] = new Array(n).fill(false);

    for (const s of symbols) {
      const r = s.row, c = s.col, col = s.color;
      for (let i = 0; i < n; i++) {
        for (let j = 0; j < n; j++) {
          if (i === r && j === c) continue; // la case du symbole
          const inRow = i === r;
          const inCol = j === c;
          const inColor = grid.cells[i][j].color === col;
          const adjacent = Math.abs(i - r) <= 1 && Math.abs(j - c) <= 1;
          if (inRow || inCol || inColor || adjacent) covered[i][j] = true;
        }
      }
    }

    // applique croix manuelles puis couverture auto
    for (let i = 0; i < n; i++) {
      for (let j = 0; j < n; j++) {
        if (states[i][j] === 'symbol') continue;
        if (manualMap[i][j]) states[i][j] = 'impossible-manual';
        else if (covered[i][j]) states[i][j] = 'impossible-auto';
      }
    }

    return states;
  }

  // Grille des croix manuelles (toutes à false par défaut).
  function createManualMap(grid) {
    const m = new Array(grid.size);
    for (let i = 0; i < grid.size; i++) m[i] = new Array(grid.size).fill(false);
    return m;
  }

  // Vérifie que des symboles posés ne se contredisent pas.
  // Lève une Error descriptive si deux symboles partagent ligne/colonne/couleur ou sont adjacents.
  function checkConsistency(symbols, n) {
    for (let a = 0; a < symbols.length; a++) {
      for (let b = a + 1; b < symbols.length; b++) {
        const s = symbols[a], t = symbols[b];
        if (s.row === t.row) {
          throw new Error("Symboles incohérents : deux symboles sur la ligne " + s.row + ".");
        }
        if (s.col === t.col) {
          throw new Error("Symboles incohérents : deux symboles sur la colonne " + s.col + ".");
        }
        if (s.color === t.color) {
          throw new Error("Symboles incohérents : deux symboles de la couleur " + s.color + ".");
        }
        if (Math.abs(s.row - t.row) <= 1 && Math.abs(s.col - t.col) <= 1) {
          throw new Error("Symboles incohérents : les symboles (" + s.row + "," + s.col +
            ") et (" + t.row + "," + t.col + ") sont adjacents.");
        }
      }
    }
    void n;
  }

  // Résout la grille : trouve une solution complète (n symboles) par backtracking ligne par ligne.
  // grid : { size, cells: [[{color}]] }
  // fixedSymbols : symboles déjà posés à conserver (positions fixes).
  // Retourne [{row,col,color}] (tous les n symboles) ou null si aucune solution.
  function findSolution(grid, fixedSymbols) {
    const n = grid.size;
    checkConsistency(fixedSymbols, n);

    // symboles obligatoires par ligne/colonne/couleur
    const fixedRow = new Array(n).fill(null);
    const usedCols = new Set();
    const usedColors = new Set();
    for (const s of fixedSymbols) {
      fixedRow[s.row] = s;
      usedCols.add(s.col);
      usedColors.add(s.color);
    }

    // tri des lignes : celles avec symbole fixe d'abord (heuristique), sinon par nb de candidats
    const placed = []; // [{row,col,color}]
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

  return { derive, collectSymbols, createManualMap, checkConsistency, findSolution };
})();
