/**
 * Validate export payload before generating documents.
 * Stops export when photos are skipped unexpectedly, duplicated, or descriptions mismatch.
 */
export function validateExportPayload({ steps, photoFiles }) {
  const errors = [];
  let mappingErrors = 0;
  let duplicatePhotos = 0;
  let photosNeedingReview = 0;

  const loadedPhotos = photoFiles.length;
  const usedPhotos = steps.length;
  const skippedPhotos = Math.max(0, loadedPhotos - usedPhotos);

  if (!steps?.length) {
    errors.push('Нет шагов для экспорта');
  }
  if (!photoFiles?.length) {
    errors.push('Нет загруженных фотографий');
  }

  const paths = photoFiles.map((f) => f.path);
  duplicatePhotos = paths.length - new Set(paths).size;
  if (duplicatePhotos > 0) {
    errors.push('Обнаружены повторяющиеся фотографии');
  }

  const byName = new Map(photoFiles.map((f) => [f.originalname || f.filename, f]));
  const usedFiles = new Set();

  steps.forEach((step, idx) => {
    if (!step.beginnerInstruction || !String(step.beginnerInstruction).trim()) {
      mappingErrors += 1;
      errors.push(`Фото ${step.photoNumber}: пустое описание`);
    }
    const file = byName.get(step.fileName) || photoFiles[idx];
    if (!file) {
      mappingErrors += 1;
      errors.push(`Фото ${step.photoNumber}: файл не найден`);
    } else if (usedFiles.has(file.path)) {
      duplicatePhotos += 1;
      errors.push(`Фото ${step.photoNumber}: повторяется`);
    } else {
      usedFiles.add(file.path);
    }
  });

  // Client sends only photos intended for export; mismatch means mapping error.
  if (steps.length !== photoFiles.length) {
    errors.push(`Несоответствие числа фото и шагов: ${photoFiles.length} vs ${steps.length}`);
    mappingErrors += 1;
  }

  return {
    loadedPhotos,
    usedPhotos,
    skippedPhotos,
    duplicatePhotos,
    photosNeedingReview,
    mappingErrors,
    powerpointErrors: 0,
    pdfErrors: 0,
    canExport: errors.length === 0,
    errors: [...new Set(errors)]
  };
}
