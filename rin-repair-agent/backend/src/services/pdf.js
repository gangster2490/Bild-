import fs from 'fs';
import path from 'path';
import PDFDocument from 'pdfkit';

/**
 * Create a PDF that visually mirrors the RIN PowerPoint layout:
 * header bar, photo + caption, instruction/warning column, footer with page numbers.
 */
export async function generatePdf({
  outputPath,
  locale,
  project,
  steps,
  photoFiles
}) {
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const photoByName = new Map(
    photoFiles.map((f) => [f.originalname || f.filename || path.basename(f.path), f])
  );

  const doc = new PDFDocument({
    size: 'A4',
    layout: 'landscape',
    margins: { top: 36, bottom: 36, left: 36, right: 36 },
    autoFirstPage: false,
    info: {
      Title: `RIN Repair Instruction ${locale}`,
      Author: 'RIN Repair Agent'
    }
  });

  const stream = fs.createWriteStream(outputPath);
  doc.pipe(stream);

  let page = 0;
  const used = new Set();

  for (const step of steps) {
    page += 1;
    const photo = photoByName.get(step.fileName) || photoFiles[page - 1];
    if (!photo || !fs.existsSync(photo.path)) {
      throw new Error(`PDF: фото шага ${step.photoNumber} отсутствует`);
    }
    if (used.has(photo.path)) {
      throw new Error(`PDF: повторяющееся фото ${step.fileName}`);
    }
    used.add(photo.path);

    doc.addPage();
    const pageW = doc.page.width;
    const pageH = doc.page.height;

    // Background
    doc.rect(0, 0, pageW, pageH).fill('#F3F6F9');

    // Header
    doc.rect(0, 0, pageW, 48).fill('#145A8C');
    const isRu = locale === 'RU';
    const title = isRu
      ? `${project.title} — шаг ${step.photoNumber}`
      : `${project.title} — Step ${step.photoNumber}`;
    doc.fillColor('#FFFFFF').fontSize(16).text(title, 24, 16, {
      width: pageW - 48,
      ellipsis: true
    });

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

    const contentTop = 64;
    const photoW = pageW * 0.45;
    const photoH = pageH * 0.58;
    const textX = 24 + photoW + 16;
    const textW = pageW - textX - 24;

    try {
      doc.image(photo.path, 24, contentTop, {
        fit: [photoW, photoH],
        align: 'center',
        valign: 'center'
      });
    } catch {
      doc.fillColor('#000').fontSize(12).text('[image]', 24, contentTop);
    }

    // Caption under photo
    doc.fillColor('#14212B').fontSize(10).text(caption, 24, contentTop + photoH + 8, {
      width: photoW,
      height: 40
    });

    // Stage + instruction
    doc.fillColor('#145A8C').fontSize(13).text(stage || '', textX, contentTop, {
      width: textW
    });
    const textTop = contentTop + 28;
    doc.fillColor('#14212B').fontSize(11).text(instruction || '', textX, textTop, {
      width: textW,
      height: pageH * 0.42,
      align: 'left'
    });

    const tools = (step.tools || []).join(', ');
    if (tools) {
      doc.moveDown(0.5);
      doc.fontSize(10).fillColor('#1F7A6C').text(
        (isRu ? 'Инструменты: ' : 'Tools: ') + tools,
        textX,
        doc.y,
        { width: textW }
      );
    }

    doc.moveDown(0.6);
    doc.fontSize(10).fillColor('#B42318').text(
      (isRu ? 'Предупреждение: ' : 'Warning: ') + (warning || ''),
      textX,
      Math.min(doc.y, pageH - 90),
      { width: textW }
    );

    // Footer
    doc.rect(0, pageH - 32, pageW, 32).fill('#1F7A6C');
    doc.fillColor('#FFFFFF').fontSize(9).text(
      `RIN Repair Instruction | ${project.productModel} | ${page}`,
      24,
      pageH - 22,
      { width: pageW - 48 }
    );
  }

  if (page === 0) {
    throw new Error('PDF: нет страниц');
  }

  doc.end();
  await new Promise((resolve, reject) => {
    stream.on('finish', resolve);
    stream.on('error', reject);
  });

  // Basic validation: file exists and non-empty
  const stat = fs.statSync(outputPath);
  if (stat.size < 100) throw new Error('PDF: пустой файл');
  return outputPath;
}
