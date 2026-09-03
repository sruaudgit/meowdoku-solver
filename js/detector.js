"use strict";

const GridDetector = (() => {
  // Données : { width, height, data: Uint8ClampedArray RGBA, get(x,y)->[r,g,b] }
  // Retourne une grille { size, cells, backgroundColor, contourColor, boundingBox, ... }

  function detect(image) {
    const bgColor = image.get(0, 0);
    const equalBg = (c) => ColorUtil.equal(c, bgColor);
    const W = image.width, H = image.height;

    // ---- Bornes horizontales : lignes avec beaucoup de contenu non-fond ----
    const hBounds = findHorizontalBounds(image, equalBg, W, H);
    const y0 = hBounds.start, y1 = hBounds.end;

    // ---- Bornes verticales dans la zone horizontale ----
    const vBounds = findVerticalBounds(image, equalBg, W, y0, y1);
    const x0 = vBounds.start, x1 = vBounds.end;

    // ---- Lignes de contour (bandes de une couleur quasi-unique) ----
    const rawH = findContourBands(image, equalBg, 'horizontal', x0, x1, y0, y1);
    const rawV = findContourBands(image, equalBg, 'vertical', x0, x1, y0, y1);
    const hc = mergeBands(rawH);
    const vc = mergeBands(rawV);

    const nH = hc.length - 1, nV = vc.length - 1;
    if (hc.length < 3 || vc.length < 3) {
      throw new Error("Impossible de détecter les contours de cases dans la grille.");
    }
    if (nH !== nV) {
      throw new Error("Grille non carrée détectée (lignes=" + nV + ", colonnes=" + nH + ").");
    }
    const size = nV;

    // ---- Couleur de contour ----
    const contourColor = sampleContourColor(image, hc[0], x0, x1, equalBg);

    // ---- Couleurs des cases ----
    const cells = [];
    const colorMap = [];
    let symbolCount = 0;

    for (let i = 0; i < size; i++) {
      const row = [];
      const yTop = hc[i][1] + 1;
      const yBottom = hc[i + 1][0] - 1;
      const cellH = yBottom - yTop + 1;

      for (let j = 0; j < size; j++) {
        const xLeft = vc[j][1] + 1;
        const xRight = vc[j + 1][0] - 1;
        const cellW = xRight - xLeft + 1;

        const inX = Math.min(xLeft + 8, xRight);
        const inY = Math.min(yTop + 8, yBottom);
        const baseColor = image.get(inX, inY);

        const centerX = xLeft + Math.floor(cellW / 2);
        const centerY = yTop + Math.floor(cellH / 2);
        const centerColor = image.get(centerX, centerY);

        const hasSymbol = !ColorUtil.equal(baseColor, centerColor);
        const colorIdx = assignColor(colorMap, baseColor);
        const state = hasSymbol ? "symbol" : "free";

        row.push({
          row: i, col: j,
          color: colorIdx,
          state,
          hex: ColorUtil.toHex(baseColor)
        });
        if (hasSymbol) symbolCount++;
      }
      cells.push(row);
    }

    return {
      size,
      cells,
      backgroundColor: bgColor.slice(),
      contourColor,
      boundingBox: { x: x0, y: y0, width: Math.abs(x1 - x0) + 1, height: Math.abs(y1 - y0) + 1 },
      colorMap: colorMap.map(c => ColorUtil.toHex(c.rgb)),
      symbolCount
    };
  }

  // Une ligne y est dans la grille si la majorité de sa largeur est non-fond.
  function rowInGrid(image, equalBg, y, W, step) {
    let nonBg = 0, total = 0;
    for (let x = 0; x < W; x += step) {
      total++;
      if (!equalBg(image.get(x, y))) nonBg++;
    }
    return nonBg / total > 0.2;
  }

  function colInGrid(image, equalBg, x, y0, y1, step) {
    let nonBg = 0, total = 0;
    for (let y = y0; y <= y1; y += step) {
      total++;
      if (!equalBg(image.get(x, y))) nonBg++;
    }
    return nonBg / total > 0.2;
  }

  function longestConsecutiveRun(predicate, length) {
    let best = null, cur = null;
    for (let i = 0; i < length; i++) {
      if (predicate(i)) {
        cur = cur ? [cur[0], i] : [i, i];
      } else {
        if (cur && (!best || cur[1] - cur[0] > best[1] - best[0])) best = cur;
        cur = null;
      }
    }
    if (cur && (!best || cur[1] - cur[0] > best[1] - best[0])) best = cur;
    return best;
  }

  function findHorizontalBounds(image, equalBg, W, H) {
    const step = Math.max(1, Math.floor(W / 200));
    const run = longestConsecutiveRun(i => rowInGrid(image, equalBg, i, W, step), H);
    if (!run) throw new Error("Grille non trouvée dans l'image.");
    return { start: run[0], end: run[1] };
  }

  function findVerticalBounds(image, equalBg, W, y0, y1) {
    const step = Math.max(1, Math.floor((y1 - y0) / 200));
    const run = longestConsecutiveRun(x => colInGrid(image, equalBg, x, y0 + 5, y1 - 5, step), W);
    if (!run) throw new Error("Bornes verticales de la grille non trouvées.");
    return { start: run[0], end: run[1] };
  }

  // Une ligne est un contour si ses pixels non-fond se regroupent en ≤ 1 couleur.
  function isContourLine(image, equalBg, axis, a0, a1, fixed, step) {
    const colors = [];
    for (let a = a0; a <= a1; a += step) {
      const c = axis === 'horizontal' ? image.get(a, fixed) : image.get(fixed, a);
      if (!equalBg(c)) colors.push(c);
    }
    if (colors.length === 0) return false;
    const clusters = [];
    for (const c of colors) {
      let found = false;
      for (const cl of clusters) {
        if (ColorUtil.equal(c, cl)) { found = true; break; }
      }
      if (!found) clusters.push(c);
    }
    return clusters.length <= 1;
  }

  function findContourBands(image, equalBg, axis, x0, x1, y0, y1) {
    const bands = [];
    let cur = null;
    const len = axis === 'horizontal' ? (y1 - y0 + 1) : (x1 - x0 + 1);
    const step = 3;
    for (let t = 0; t < len; t++) {
      let val;
      if (axis === 'horizontal') {
        val = isContourLine(image, equalBg, 'horizontal', x0, x1, y0 + t, step);
      } else {
        val = isContourLine(image, equalBg, 'vertical', y0, y1, x0 + t, step);
      }
      if (val) {
        const pos = axis === 'horizontal' ? y0 + t : x0 + t;
        cur = cur ? [cur[0], pos] : [pos, pos];
      } else {
        if (cur) bands.push(cur);
        cur = null;
      }
    }
    if (cur) bands.push(cur);
    return bands;
  }

  // Fusionne les bandes de contour séparées par de petits écarts (bruit JPEG / coins arrondis).
  function mergeBands(bands, gap = 10) {
    if (bands.length === 0) return bands;
    const merged = [bands[0].slice()];
    for (let i = 1; i < bands.length; i++) {
      if (bands[i][0] - merged[merged.length - 1][1] < gap) {
        merged[merged.length - 1][1] = bands[i][1];
      } else {
        merged.push(bands[i].slice());
      }
    }
    return merged;
  }

  function sampleContourColor(image, band, x0, x1, equalBg) {
    const y = Math.floor((band[0] + band[1]) / 2);
    let acc = [0, 0, 0], count = 0;
    for (let x = x0; x <= x1; x++) {
      const c = image.get(x, y);
      if (!equalBg(c)) { acc[0] += c[0]; acc[1] += c[1]; acc[2] += c[2]; count++; }
    }
    if (count === 0) return [255, 255, 255];
    return [acc[0] / count, acc[1] / count, acc[2] / count];
  }

  // Assigne un index de couleur (1..n) à une couleur RGB en se basant sur la distance lab.
  function assignColor(colorMap, rgb) {
    const lab = ColorUtil.rgbToLab(rgb[0], rgb[1], rgb[2]);
    for (let i = 0; i < colorMap.length; i++) {
      if (ColorUtil.deltaE(lab, colorMap[i].lab) < 3) return i + 1;
    }
    colorMap.push({ lab, rgb: rgb.slice() });
    return colorMap.length;
  }

  return { detect };
})();
