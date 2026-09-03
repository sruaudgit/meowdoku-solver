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

  return { derive, collectSymbols, createManualMap };
})();
