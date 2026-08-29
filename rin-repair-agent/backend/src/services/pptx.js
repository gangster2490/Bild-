import fs from 'fs';
import path from 'path';
import JSZip from 'jszip';

function xmlEscape(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function wrapText(text, maxLen = 90) {
  const words = String(text || '').split(/\s+/);
  const lines = [];
  let line = '';
  for (const w of words) {
    if ((line + ' ' + w).trim().length > maxLen) {
      if (line) lines.push(line);
      line = w;
    } else {
      line = (line + ' ' + w).trim();
    }
  }
  if (line) lines.push(line);
  return lines;
}

function textBody(lines, fontSize = 12, bold = false) {
  const runs = lines
    .map(
      (line) => `
      <a:p>
        <a:pPr algn="l"/>
        <a:r>
          <a:rPr lang="ru-RU" sz="${fontSize * 100}"${bold ? ' b="1"' : ''}>
            <a:solidFill><a:srgbClr val="1A1A1A"/></a:solidFill>
            <a:latin typeface="Calibri"/>
          </a:rPr>
          <a:t>${xmlEscape(line)}</a:t>
        </a:r>
      </a:p>`
    )
    .join('');
  return `<p:txBody><a:bodyPr wrap="square" lIns="36000" tIns="36000" rIns="36000" bIns="36000"/><a:lstStyle/>${runs || '<a:p><a:endParaRPr/></a:p>'}</p:txBody>`;
}

function shapeRect(name, x, y, cx, cy, fill) {
  return `
  <p:sp>
    <p:nvSpPr>
      <p:cNvPr id="${Math.floor(Math.random() * 100000)}" name="${xmlEscape(name)}"/>
      <p:cNvSpPr/>
      <p:nvPr/>
    </p:nvSpPr>
    <p:spPr>
      <a:xfrm><a:off x="${x}" y="${y}"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm>
      <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
      <a:solidFill><a:srgbClr val="${fill}"/></a:solidFill>
      <a:ln><a:noFill/></a:ln>
    </p:spPr>
  </p:sp>`;
}

function textShape(name, x, y, cx, cy, lines, fontSize, bold = false) {
  return `
  <p:sp>
    <p:nvSpPr>
      <p:cNvPr id="${Math.floor(Math.random() * 100000)}" name="${xmlEscape(name)}"/>
      <p:cNvSpPr txBox="1"/>
      <p:nvPr/>
    </p:nvSpPr>
    <p:spPr>
      <a:xfrm><a:off x="${x}" y="${y}"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm>
      <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
      <a:noFill/>
      <a:ln><a:noFill/></a:ln>
    </p:spPr>
    ${textBody(lines, fontSize, bold)}
  </p:sp>`;
}

function pictureShape(name, embedId, x, y, cx, cy) {
  return `
  <p:pic>
    <p:nvPicPr>
      <p:cNvPr id="${Math.floor(Math.random() * 100000)}" name="${xmlEscape(name)}"/>
      <p:cNvPicPr><a:picLocks noChangeAspect="0"/></p:cNvPicPr>
      <p:nvPr/>
    </p:nvPicPr>
    <p:blipFill>
      <a:blip r:embed="${embedId}"/>
      <a:stretch><a:fillRect/></a:stretch>
    </p:blipFill>
    <p:spPr>
      <a:xfrm><a:off x="${x}" y="${y}"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm>
      <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
      <a:ln w="12700"><a:solidFill><a:srgbClr val="145A8C"/></a:solidFill></a:ln>
    </p:spPr>
  </p:pic>`;
}

function buildSlideXml({ title, stage, instruction, warning, tools, caption, imageRid, slideW, slideH, footer, pageNo }) {
  // Layout inspired by RIN workshop slides: header bar, photo left/top, text right/bottom, warning box, footer.
  const headerH = Math.floor(slideH * 0.11);
  const footerH = Math.floor(slideH * 0.07);
  const margin = Math.floor(slideW * 0.03);
  const contentTop = headerH + margin;
  const contentH = slideH - headerH - footerH - margin * 2;
  const photoW = Math.floor(slideW * 0.46);
  const photoH = Math.floor(contentH * 0.72);
  const textX = margin * 2 + photoW;
  const textW = slideW - textX - margin;

  const toolLine = tools?.length ? `Инструменты / Tools: ${tools.join(', ')}` : '';
  const bodyLines = [
    ...wrapText(instruction, 55),
    '',
    ...wrapText(toolLine, 55),
    '',
    ...wrapText(`⚠ ${warning || ''}`, 55)
  ];

  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg>
      <p:bgPr>
        <a:solidFill><a:srgbClr val="F3F6F9"/></a:solidFill>
        <a:effectLst/>
      </p:bgPr>
    </p:bg>
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="${slideW}" cy="${slideH}"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="${slideW}" cy="${slideH}"/>
        </a:xfrm>
      </p:grpSpPr>
      ${shapeRect('Header', 0, 0, slideW, headerH, '145A8C')}
      ${textShape('Title', margin, Math.floor(headerH * 0.2), slideW - margin * 2, Math.floor(headerH * 0.6), wrapText(title, 70), 16, true)}
      ${pictureShape('Photo', imageRid, margin, contentTop, photoW, photoH)}
      ${textShape('Caption', margin, contentTop + photoH + Math.floor(margin * 0.3), photoW, Math.floor(contentH * 0.18), wrapText(caption, 50), 11)}
      ${textShape('Stage', textX, contentTop, textW, Math.floor(contentH * 0.12), wrapText(stage, 50), 14, true)}
      ${textShape('Instruction', textX, contentTop + Math.floor(contentH * 0.12), textW, Math.floor(contentH * 0.78), bodyLines, 11)}
      ${shapeRect('FooterBar', 0, slideH - footerH, slideW, footerH, '1F7A6C')}
      ${textShape('Footer', margin, slideH - footerH + Math.floor(footerH * 0.2), slideW - margin * 2, Math.floor(footerH * 0.6), [`${footer}  |  ${pageNo}`], 10)}
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>`;
}

function slideRels(imageRid, imageTarget) {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="${imageRid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="${imageTarget}"/>
</Relationships>`;
}

async function readSlideSize(zip) {
  const pres = await zip.file('ppt/presentation.xml')?.async('string');
  if (!pres) return { cx: 12192000, cy: 6858000 }; // 16:9 EMUs default
  const m = pres.match(/sldSz[^>]*cx="(\d+)"[^>]*cy="(\d+)"/) ||
    pres.match(/sldSz[^>]*cy="(\d+)"[^>]*cx="(\d+)"/);
  if (!m) return { cx: 12192000, cy: 6858000 };
  if (pres.includes(`cx="${m[1]}"`) && pres.includes(`cy="${m[2]}"`)) {
    return { cx: Number(m[1]), cy: Number(m[2]) };
  }
  return { cx: Number(m[2]), cy: Number(m[1]) };
}

function ensureMinimalLayout(zip) {
  // If template lacks layouts, add a blank layout/master so slides validate.
  const layout = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
  <p:cSld name="Blank">
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr/>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sldLayout>`;
  const layoutRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>`;
  const master = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg><p:bgRef idx="1001"><a:schemeClr val="bg1"/></p:bgRef></p:bg>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr/>
    </p:spTree>
  </p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst>
    <p:sldLayoutId id="2147483649" r:id="rId1"/>
  </p:sldLayoutIdLst>
</p:sldMaster>`;
  const masterRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>`;
  const theme = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="RIN">
  <a:themeElements>
    <a:clrScheme name="RIN">
      <a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1>
      <a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="14212B"/></a:dk2>
      <a:lt2><a:srgbClr val="E8EEF3"/></a:lt2>
      <a:accent1><a:srgbClr val="145A8C"/></a:accent1>
      <a:accent2><a:srgbClr val="1F7A6C"/></a:accent2>
      <a:accent3><a:srgbClr val="C45C26"/></a:accent3>
      <a:accent4><a:srgbClr val="5B6B7A"/></a:accent4>
      <a:accent5><a:srgbClr val="2F6B9A"/></a:accent5>
      <a:accent6><a:srgbClr val="7A8B99"/></a:accent6>
      <a:hlink><a:srgbClr val="145A8C"/></a:hlink>
      <a:folHlink><a:srgbClr val="1F7A6C"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="RIN">
      <a:majorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>
      <a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="RIN">
      <a:fillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:fillStyleLst>
      <a:lnStyleLst>
        <a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="12700"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
      </a:lnStyleLst>
      <a:effectStyleLst>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
      </a:effectStyleLst>
      <a:bgFillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>`;

  if (!zip.file('ppt/slideLayouts/slideLayout1.xml')) {
    zip.file('ppt/slideLayouts/slideLayout1.xml', layout);
    zip.file('ppt/slideLayouts/_rels/slideLayout1.xml.rels', layoutRels);
  }
  if (!zip.file('ppt/slideMasters/slideMaster1.xml')) {
    zip.file('ppt/slideMasters/slideMaster1.xml', master);
    zip.file('ppt/slideMasters/_rels/slideMaster1.xml.rels', masterRels);
  }
  if (!zip.file('ppt/theme/theme1.xml')) {
    zip.file('ppt/theme/theme1.xml', theme);
  }
}

/**
 * Build a language-specific PPTX using the uploaded RIN template as the package base
 * (theme/fonts/slide size preserved when present).
 */
export async function generatePptx({
  templatePath,
  outputPath,
  locale,
  project,
  steps,
  photoFiles
}) {
  const templateBuf = fs.readFileSync(templatePath);
  const templateZip = await JSZip.loadAsync(templateBuf);
  const { cx: slideW, cy: slideH } = await readSlideSize(templateZip);

  const out = new JSZip();
  // Copy template parts except old slides / presentation relationships we will rewrite.
  const entries = Object.keys(templateZip.files);
  for (const name of entries) {
    const file = templateZip.files[name];
    if (file.dir) continue;
    if (name.startsWith('ppt/slides/')) continue;
    if (name === 'ppt/presentation.xml') continue;
    if (name === 'ppt/_rels/presentation.xml.rels') continue;
    if (name === '[Content_Types].xml') continue;
    out.file(name, await file.async('nodebuffer'));
  }

  ensureMinimalLayout(out);

  const photoByName = new Map(photoFiles.map((f) => [f.originalname || f.filename || path.basename(f.path), f]));
  const usedNames = new Set();
  const slideRelsList = [];
  let page = 1;

  for (const step of steps) {
    const name = step.fileName;
    const photo = photoByName.get(name) || photoFiles[page - 1];
    if (!photo) {
      throw new Error(`Фото для шага ${step.photoNumber} не найдено`);
    }
    if (usedNames.has(photo.path)) {
      throw new Error(`Повторяющееся фото: ${name}`);
    }
    usedNames.add(photo.path);

    const mediaName = `image${page}.jpg`;
    const mediaPath = `ppt/media/${mediaName}`;
    out.file(mediaPath, fs.readFileSync(photo.path));

    const imageRid = 'rIdImage1';
    const isRu = locale === 'RU';
    const title = isRu
      ? `${project.title} — шаг ${step.photoNumber}`
      : `${project.title} — Step ${step.photoNumber}`;
    const stage = isRu ? step.repairStage : step.repairStageEn || step.repairStage;
    const instruction = isRu
      ? step.beginnerInstruction
      : step.beginnerInstructionEn || step.beginnerInstruction;
    const warning = isRu
      ? step.importantWarning
      : step.importantWarningEn || step.importantWarning;
    const caption = isRu
      ? `Фото ${step.photoNumber}: ${step.visibleAction || stage}`
      : `Photo ${step.photoNumber}: ${step.visibleActionEn || step.visibleAction || stage}`;
    const footer = isRu
      ? `RIN Repair Instruction | ${project.productModel}`
      : `RIN Repair Instruction | ${project.productModel}`;

    const slideXml = buildSlideXml({
      title,
      stage,
      instruction,
      warning,
      tools: step.tools || [],
      caption,
      imageRid,
      slideW,
      slideH,
      footer,
      pageNo: String(page)
    });

    out.file(`ppt/slides/slide${page}.xml`, slideXml);
    out.file(
      `ppt/slides/_rels/slide${page}.xml.rels`,
      slideRels(imageRid, `../media/${mediaName}`)
    );
    slideRelsList.push(page);
    page += 1;
  }

  if (slideRelsList.length === 0) {
    throw new Error('Нет слайдов для PowerPoint');
  }

  // presentation.xml
  const sldIdLst = slideRelsList
    .map((n, idx) => `<p:sldId id="${256 + idx}" r:id="rId${10 + n}"/>`)
    .join('');
  const presentationXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst>
    <p:sldMasterId id="2147483648" r:id="rId1"/>
  </p:sldMasterIdLst>
  <p:sldIdLst>${sldIdLst}</p:sldIdLst>
  <p:sldSz cx="${slideW}" cy="${slideH}"/>
  <p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>`;
  out.file('ppt/presentation.xml', presentationXml);

  const relParts = [
    `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>`,
    ...slideRelsList.map(
      (n) =>
        `<Relationship Id="rId${10 + n}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${n}.xml"/>`
    ),
    `<Relationship Id="rIdTheme" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>`
  ];
  out.file(
    'ppt/_rels/presentation.xml.rels',
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
${relParts.join('\n')}
</Relationships>`
  );

  const overrideSlides = slideRelsList
    .map(
      (n) =>
        `<Override PartName="/ppt/slides/slide${n}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>`
    )
    .join('');
  const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  ${overrideSlides}
</Types>`;
  out.file('[Content_Types].xml', contentTypes);

  if (!out.file('_rels/.rels')) {
    out.file(
      '_rels/.rels',
      `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>`
    );
  }

  const buf = await out.generateAsync({ type: 'nodebuffer' });
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, buf);
  return outputPath;
}
