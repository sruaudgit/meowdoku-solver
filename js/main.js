"use strict";

(() => {
  const dropZone = document.getElementById("upload-zone");
  const fileInput = document.getElementById("file-input");
  const browseBtn = document.getElementById("browse-btn");
  const statusEl = document.getElementById("status");
  const resultEl = document.getElementById("result");
  const canvas = document.getElementById("image-canvas");
  const imageMeta = document.getElementById("image-meta");
  const gridMeta = document.getElementById("grid-meta");
  const gridTable = document.getElementById("grid-table");
  const legendEl = document.getElementById("legend");
  const solveBtn = document.getElementById("solve-btn");
  const exportBtn = document.getElementById("export-btn");

  let currentImage = null;

  // État éditable de la grille
  let grid = null;        // données détectées (size, cells, colorMap, ...)
  let symbols = [];       // [{id,row,col,color}]
  let nextSymbolId = 1;
  let manual = null;      // boolean[row][col] croix manuelles
  let cells = [];         // noeuds <td> par [row][col]

  // ---- Drag & drop + browse ----
  browseBtn.addEventListener("click", () => fileInput.click());
  fileInput.addEventListener("change", () => {
    if (fileInput.files.length) handleFile(fileInput.files[0]);
  });

  ["dragenter", "dragover"].forEach(ev => {
    dropZone.addEventListener(ev, e => { e.preventDefault(); dropZone.classList.add("dragover"); });
  });
  ["dragleave", "drop"].forEach(ev => {
    dropZone.addEventListener(ev, e => { e.preventDefault(); dropZone.classList.remove("dragover"); });
  });
  dropZone.addEventListener("drop", e => {
    const f = e.dataTransfer.files[0];
    if (f) handleFile(f);
  });

  function setStatus(msg, type = "info") {
    statusEl.textContent = msg;
    statusEl.className = "status " + type;
  }

  function handleFile(file) {
    const name = file.name.toLowerCase();
    if (!(name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
      setStatus("Fichier invalide : veuillez choisir un fichier .jpg ou .jpeg.", "error");
      return;
    }
    const reader = new FileReader();
    reader.onload = e => {
      const img = new Image();
      img.onload = () => processImage(img);
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  function processImage(img) {
    const drawW = 1080;
    const drawH = Math.round(img.height * (drawW / img.width));
    canvas.width = drawW;
    canvas.height = drawH;
    const ctx = canvas.getContext("2d");
    ctx.drawImage(img, 0, 0, drawW, drawH);

    const imageData = ctx.getImageData(0, 0, drawW, drawH);
    const data = imageData.data;
    currentImage = {
      width: drawW,
      height: drawH,
      data,
      get(x, y) {
        if (x < 0 || y < 0 || x >= drawW || y >= drawH) return [0, 0, 0];
        const i = (y * drawW + x) * 4;
        return [data[i], data[i + 1], data[i + 2]];
      }
    };

    imageMeta.textContent = "Dimensions : " + img.naturalWidth + "x" + img.naturalHeight +
      " px (analysé à " + drawW + "x" + drawH + " px)";

    // Éfface l'ancienne grille affichée tant que la nouvelle n'est pas analysée.
    resultEl.classList.add("hidden");
    grid = null;
    gridTable.innerHTML = "";
    gridMeta.innerHTML = "";
    legendEl.innerHTML = "";
    solveBtn.classList.add("hidden");
    exportBtn.classList.add("hidden");

    setStatus("Analyse de la grille en cours…", "info");
    const start = performance.now();
    // Laisse le navigateur peindre le message avant le calcul synchrone.
    // Le try/catch est placé DANS le callback : sinon les erreurs synchrones
    // levées par GridDetector.detect() (exécuté de façon asynchrone via
    // setTimeout) échapperaient au try/catch externe et atterriraient dans la
    // console au lieu de la section status.
    setTimeout(() => {
      try {
        const detected = GridDetector.detect(currentImage);
        const ms = (performance.now() - start).toFixed(1);
        const t = detected.timing || {};
        const part = (k) => t[k] != null ? t[k].toFixed(1) : "–";
        const breakdown = "Détection en " + ms + " ms — "
          + "localisation : " + part("location") + " ms, "
          + "contours : " + part("contours") + " ms, "
          + "couleurs des cases : " + part("cells") + " ms";
        initEditable(detected);
        resultEl.classList.remove("hidden");
        solveBtn.classList.remove("hidden");
        exportBtn.classList.remove("hidden");
        renderAll();
        setStatus(breakdown, "info");
      } catch (err) {
        setStatus("Erreur de détection : " + err.message, "error");
        console.error(err);
      }
    }, 0);
  }

  // Initialise l'état éditable depuis la grille détectée.
  function initEditable(detected) {
    grid = detected;
    symbols = GridSolver.collectSymbols(detected, nextSymbolId);
    nextSymbolId = symbols.length + 1;
    manual = GridSolver.createManualMap(detected);
    cells = [];
  }

  // ---- Édition ----
  function toggleManual(row, col) {
    if (isSymbolAt(row, col)) return;
    manual[row][col] = !manual[row][col];
    renderAll();
  }

  function setSymbol(row, col) {
    // pose un symbole, efface la croix manuelle de cette case
    if (isSymbolAt(row, col)) return;
    symbols.push({ id: nextSymbolId++, row, col, color: grid.cells[row][col].color });
    manual[row][col] = false;
    renderAll();
  }

  function removeSymbol(row, col) {
    const idx = symbols.findIndex(s => s.row === row && s.col === col);
    if (idx === -1) return;
    symbols.splice(idx, 1);
    renderAll();
  }

  function isSymbolAt(row, col) {
    return symbols.some(s => s.row === row && s.col === col);
  }

  // ---- Résolution ----
  function solve() {
    if (!grid) return;
    const fixed = symbols.map(s => ({ row: s.row, col: s.col, color: s.color }));
    setStatus("Résolution en cours…", "info");
    setTimeout(() => {
      try {
        const start = performance.now();
        const solution = GridSolver.findSolution(grid, fixed);
        const ms = (performance.now() - start).toFixed(1);

        if (!solution) {
          setStatus("Aucune solution trouvée avec les symboles posés.", "error");
          return;
        }

        // applique la solution : remplace les symboles, efface les croix manuelles (grille propre)
        symbols = solution.map((s, idx) => ({ id: idx + 1, row: s.row, col: s.col, color: s.color }));
        nextSymbolId = symbols.length + 1;
        manual = GridSolver.createManualMap(grid);
        renderAll();
        setStatus("Solution trouvée (unique) en " + ms + " ms.", "info");
      } catch (err) {
        setStatus(err.message, "error");
      }
    }, 0);
  }

  solveBtn.addEventListener("click", solve);

  exportBtn.addEventListener("click", () => {
    if (!grid) return;
    const exportData = {
      size: grid.size,
      cells: grid.cells.map(row => row.map(c => ({ color: c.color }))),
      colorMap: grid.colorMap,
      backgroundColor: ColorUtil.toHex(grid.backgroundColor),
      contourColor: ColorUtil.toHex(grid.contourColor),
      detectedSymbols: symbols.map(s => ({ row: s.row, col: s.col, color: s.color })),
      solution: null
    };
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "grid_" + grid.size + "x" + grid.size + ".json";
    a.click();
    URL.revokeObjectURL(url);
  });

  // ---- Rendu ----
  function renderAll() {
    renderTable();
    renderMeta();
    renderLegend();
  }

  function renderTable() {
    const states = GridSolver.derive(grid, symbols, manual);
    const n = grid.size;
    gridTable.innerHTML = "";
    const tbody = document.createElement("tbody");
    cells = new Array(n);

    for (let i = 0; i < n; i++) {
      const tr = document.createElement("tr");
      cells[i] = new Array(n);
      for (let j = 0; j < n; j++) {
        const cell = grid.cells[i][j];
        const state = states[i][j];
        const td = document.createElement("td");
        td.style.backgroundColor = cell.hex;
        td.dataset.row = i;
        td.dataset.col = j;
        td.setAttribute("class", "cell " + state);
        td.title = "case (" + i + "," + j + ") couleur " + cell.color + " — " + state;

        const label = document.createElement("span");
        label.className = "cell-label";
        if (state === "symbol") label.textContent = "★";
        else if (state === "impossible-manual" || state === "impossible-auto") label.textContent = "✕";
        td.appendChild(label);

        // Interactivité
        td.addEventListener("click", () => toggleManual(i, j));
        td.addEventListener("dblclick", e => {
          if (isSymbolAt(i, j)) removeSymbol(i, j);
          else setSymbol(i, j);
        });

        tr.appendChild(td);
        cells[i][j] = td;
      }
      tbody.appendChild(tr);
    }
    gridTable.appendChild(tbody);
  }

  function renderMeta() {
    const states = GridSolver.derive(grid, symbols, manual);
    let countSymbol = 0, countManual = 0, countAuto = 0, countFree = 0;
    for (let i = 0; i < grid.size; i++) {
      for (let j = 0; j < grid.size; j++) {
        const s = states[i][j];
        if (s === "symbol") countSymbol++;
        else if (s === "impossible-manual") countManual++;
        else if (s === "impossible-auto") countAuto++;
        else countFree++;
      }
    }

    gridMeta.innerHTML =
      "Taille : <strong>" + grid.size + "x" + grid.size + "</strong> (" + (grid.size * grid.size) + " cases)" +
      "<br>BoundingBox : x=" + grid.boundingBox.x + ", y=" + grid.boundingBox.y +
      ", w=" + grid.boundingBox.width + ", h=" + grid.boundingBox.height +
      "<br>Fond : " + ColorUtil.toHex(grid.backgroundColor) +
      " | Contour : " + ColorUtil.toHex(grid.contourColor) +
      "<br>Symbole(s) : " + countSymbol + " | Impossibles (manuel) : " + countManual +
      " | Impossibles (auto) : " + countAuto + " | Libres : " + countFree +
      "<br><em>Cliquez pour marquer impossible, double-cliquez pour poser un symbole.</em>";
  }

  function renderLegend() {
    legendEl.innerHTML = "";
    // États
    const states = [
      { key: "symbol", label: "Symbole", cls: "legend-swatch symbol" },
      { key: "impossible-manual", label: "Impossible (manuel)", cls: "legend-swatch imp-manual" },
      { key: "impossible-auto", label: "Impossible (auto)", cls: "legend-swatch imp-auto" },
      { key: "free", label: "Libre", cls: "legend-swatch free" }
    ];
    states.forEach(s => {
      const item = document.createElement("div");
      item.className = "legend-item";
      const sw = document.createElement("span");
      sw.className = s.cls;
      sw.textContent = s.key === "symbol" ? "★" : s.key.startsWith("impossible") ? "✕" : "";
      item.appendChild(sw);
      item.appendChild(document.createTextNode(s.label));
      legendEl.appendChild(item);
    });

    // Couleurs
    grid.colorMap.forEach((hex, idx) => {
      const item = document.createElement("div");
      item.className = "legend-item";
      const sw = document.createElement("span");
      sw.className = "legend-swatch";
      sw.style.backgroundColor = hex;
      item.appendChild(sw);
      item.appendChild(document.createTextNode("#" + (idx + 1)));
      legendEl.appendChild(item);
    });
  }
})();
