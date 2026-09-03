"use strict";

const GridDetector = (() => {
  function detect(image) {
    const bgColor = image.get(0, 0);
    const equalBg = (c) => ColorUtil.equal(c, bgColor);
    const W = image.width, H = image.height;
    const t = {};
    const T0 = performance.now();

    const hBounds = findHorizontalBounds(image, equalBg, W, H);
    const y0 = hBounds.start, y1 = hBounds.end;
    t.location = performance.now() - T0;

    const vBounds = findVerticalBounds(image, equalBg, W, y0, y1);
    const x0 = vBounds.start, x1 = vBounds.end;

    const T1 = performance.now();
    const { borderTop, cellCount } = detectPattern(image, equalBg, x0, x1, y0, y1);
    t.contours = performance.now() - T1;

    if (cellCount < 1) {
      throw new Error("Impossible de détecter les contours de cases dans la grille.");
    }

    const contourColor = sampleContourColor(image, [borderTop, borderTop + 5], x0, x1, equalBg);

    // Bordures verticales (colonnes) et horizontales (rangées) des cases. On
    // évite l'extrapolation par pas constant : la taille des cases peut varier
    // légèrement (déformation d'écran), ce qui faisait dériver les dernières
    // lignes. On détecte donc chaque bande réelle, en n'échantillonnant qu'une
    // fraction des lignes/colonnes.
    const colBorders = detectBorders(image, equalBg, axisVertical, x0, x1, y0, y1, contourColor);
    if (colBorders.length !== cellCount + 1) {
      throw new Error("Grille non carrée détectée (lignes=" + cellCount + ", colonnes=" + (colBorders.length - 1) + ").");
    }

    // Colonnes de référence (milieux de cases) pour détecter les rangées.
    const sampleXs = [];
    for (let j = 0; j < colBorders.length - 1; j++) {
      sampleXs.push(Math.floor((colBorders[j][1] + colBorders[j + 1][0]) / 2));
    }
    const rowBorders = detectBorders(image, equalBg, axisHorizontal, x0, x1, y0, y1, contourColor, sampleXs);
    if (rowBorders.length !== cellCount + 1) {
      throw new Error("Grille non carrée détectée (lignes=" + (rowBorders.length - 1) + ", colonnes=" + cellCount + ").");
    }
    const size = cellCount;

    const T2 = performance.now();
    const cells = [];
    const colorMap = [];
    let symbolCount = 0;

    for (let i = 0; i < size; i++) {
      const row = [];
      const yTop = rowBorders[i][1] + 1;
      const yBottom = rowBorders[i + 1][0] - 1;
      const cellH = yBottom - yTop + 1;

      for (let j = 0; j < size; j++) {
        const xLeft = colBorders[j][1] + 1;
        const xRight = colBorders[j + 1][0] - 1;
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
    t.cells = performance.now() - T2;
    t.total = performance.now() - T0;

    return {
      size,
      cells,
      backgroundColor: bgColor.slice(),
      contourColor,
      boundingBox: { x: x0, y: y0, width: Math.abs(x1 - x0) + 1, height: Math.abs(y1 - y0) + 1 },
      colorMap: colorMap.map(c => ColorUtil.toHex(c.rgb)),
      symbolCount,
      timing: t
    };
  }

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

  function isContourLine(image, equalBg, x0, x1, y, step) {
    const colors = [];
    for (let x = x0; x <= x1; x += step) {
      const c = image.get(x, y);
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

  function detectPattern(image, equalBg, x0, x1, y0, y1) {
    const step = 3;
    const gap = 8;

    // Parcourt les lignes de haut en bas, mais s'arrête dès que la structure
    // est déterminée : bordure externe, première rangée de cases, puis première
    // bordure interne. Il n'est pas nécessaire d'aller plus loin.
    const merged = []; // bandes de contour fusionnées : [startY, endY]
    let cur = null;    // run en cours : [startY, endY, isContour]
    let stop = false;
    for (let y = y0; y <= y1 && !stop; y++) {
      const isContour = isContourLine(image, equalBg, x0, x1, y, step);
      if (cur && cur[2] === isContour) {
        cur[1] = y;
      } else {
        // On ferme le run en cours ; s'il est un contour, on l'insère dans les
        // bandes fusionnées (en absorbant les petits écarts de bruit JPEG).
        if (cur) {
          if (cur[2]) {
            const last = merged[merged.length - 1];
            if (last && cur[0] - last[1] <= gap + 1) last[1] = cur[1];
            else merged.push([cur[0], cur[1]]);
            if (merged.length >= 2) stop = true;
          }
        }
        cur = [y, y, isContour];
      }
    }
    if (cur) {
      if (cur[2]) {
        const last = merged[merged.length - 1];
        if (last && cur[0] - last[1] <= gap + 1) last[1] = cur[1];
        else merged.push([cur[0], cur[1]]);
      }
    }

    if (merged.length < 2) {
      throw new Error("Pattern de grille non détecté.");
    }

    // Première bande de contour = bordure externe, la suivante = bordure interne.
    const borderTop = merged[0][0];
    const outerBorder = merged[0][1] - merged[0][0] + 1;
    const innerBorder = merged[1][1] - merged[1][0] + 1;
    const cellSize = merged[1][0] - merged[0][1] - 1;

    if (cellSize <= 0 || innerBorder < 0) {
      throw new Error("Dimensions de grille incohérentes.");
    }

    // Grille carrée et régulière : le nombre de cases par côté se déduit de la
    // hauteur totale (bordure externe haute->basse + n cases + n-1 bordures internes).
    const totalHeight = y1 - borderTop + 1;
    const innerHeight = totalHeight - 2 * outerBorder;
    const cellCount = Math.round((innerHeight + innerBorder) / (cellSize + innerBorder));
    if (cellCount < 1) {
      throw new Error("Impossible de compter les cases de la grille.");
    }

    return { borderTop, outerBorder, cellSize, innerBorder, cellCount };
  }

  const axisVertical = 0;
  const axisHorizontal = 1;

  // Détecte les bandes de contour (bordures de cases) selon un axe.
  // - axisVertical   : on vote pour chaque colonne x en échantillonnant des
  //                     lignes réparties sur la hauteur.
  // - axisHorizontal : on vote pour chaque ligne y en échantillonnant des
  //                     colonnes données (sampleXs, typiquement les milieux
  //                     de cases).
  // Une coordonnée est une bordure si la couleur de contour y est présente sur
  // la majorité des échantillons. Beaucoup plus léger qu'un balayage exhaustif.
  function detectBorders(image, equalBg, axis, x0, x1, y0, y1, contourColor, sampleXs) {
    let samples, extent, makeVote;
    if (axis === axisVertical) {
      samples = [];
      for (let y = y0 + 5; y <= y1 - 5; y += 40) samples.push(y);
      extent = x1 - x0 + 1;
      makeVote = () => {
        const vote = new Array(extent).fill(0);
        for (const y of samples) {
          for (let x = x0; x <= x1; x++) {
            if (ColorUtil.equal(image.get(x, y), contourColor)) vote[x - x0]++;
          }
        }
        return vote;
      };
    } else {
      samples = sampleXs;
      extent = y1 - y0 + 1;
      makeVote = () => {
        const vote = new Array(extent).fill(0);
        for (const x of samples) {
          for (let y = y0; y <= y1; y++) {
            if (ColorUtil.equal(image.get(x, y), contourColor)) vote[y - y0]++;
          }
        }
        return vote;
      };
    }

    const threshold = Math.max(1, samples.length / 2);
    const vote = makeVote();
    const borders = [];
    let cur = null;
    for (let a = 0; a < extent; a++) {
      const isBorder = vote[a] > threshold;
      const pos = axis === axisVertical ? x0 + a : y0 + a;
      if (isBorder) {
        if (cur) cur[1] = pos;
        else cur = [pos, pos];
      } else {
        if (cur) borders.push(cur);
        cur = null;
      }
    }
    if (cur) borders.push(cur);
    return borders;
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
