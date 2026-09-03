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

  let currentImage = null;

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
    const drawW = 1080; // largeur de traitement fixe (échelle), on garde le ratio
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

    try {
      const start = performance.now();
      const grid = GridDetector.detect(currentImage);
      const ms = (performance.now() - start).toFixed(1);
      resultEl.classList.remove("hidden");
      renderGrid(grid, ms);
      setStatus("Grille détectée en " + ms + " ms.", "info");
    } catch (err) {
      resultEl.classList.add("hidden");
      setStatus("Erreur de détection : " + err.message, "error");
      console.error(err);
    }
  }

  function renderGrid(grid, ms) {
    gridMeta.innerHTML =
      "Taille : <strong>" + grid.size + "x" + grid.size + "</strong> (" + (grid.size * grid.size) + " cases)" +
      "<br>BoundingBox : x=" + grid.boundingBox.x + ", y=" + grid.boundingBox.y +
      ", w=" + grid.boundingBox.width + ", h=" + grid.boundingBox.height +
      "<br>Fond : " + ColorUtil.toHex(grid.backgroundColor) +
      " | Contour : " + ColorUtil.toHex(grid.contourColor) +
      " | Symbole(s) : " + grid.symbolCount +
      "<br>Temps : " + ms + " ms";

    const frag = document.createDocumentFragment();
    const tbody = document.createElement("tbody");
    for (let i = 0; i < grid.size; i++) {
      const tr = document.createElement("tr");
      for (let j = 0; j < grid.size; j++) {
        const cell = grid.cells[i][j];
        const td = document.createElement("td");
        td.style.backgroundColor = cell.hex;
        td.title = "case (" + i + "," + j + ") couleur " + cell.color + " état " + cell.state;
        if (cell.state === "symbol") td.className = "symbol";
        tr.appendChild(td);
      }
      tbody.appendChild(tr);
    }
    frag.appendChild(tbody);
    gridTable.innerHTML = "";
    gridTable.appendChild(frag);

    // Légende
    legendEl.innerHTML = "";
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
