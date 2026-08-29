import cors from 'cors';
import express from 'express';
import fs from 'fs';
import multer from 'multer';
import path from 'path';
import { fileURLToPath } from 'url';
import { analyzePhoto, checkApiKey } from './services/ai.js';
import { generatePdf } from './services/pdf.js';
import { generatePptx } from './services/pptx.js';
import { validateExportPayload } from './services/validate.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const UPLOADS = path.join(ROOT, 'uploads');
const GENERATED = path.join(ROOT, 'generated');
const OUTPUT = path.resolve(ROOT, '../output');

for (const dir of [UPLOADS, GENERATED, OUTPUT]) {
  fs.mkdirSync(dir, { recursive: true });
}

const upload = multer({
  dest: UPLOADS,
  limits: { fileSize: 40 * 1024 * 1024, files: 120 }
});

const app = express();
app.use(cors());
app.use(express.json({ limit: '2mb' }));

// Never log API keys.
app.use((req, _res, next) => {
  if (req.body?.apiKey) req.body.apiKey = String(req.body.apiKey);
  next();
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', service: 'rin-repair-agent-backend' });
});

app.post('/api/check-key', async (req, res) => {
  try {
    const { apiKey, provider } = req.body || {};
    const result = await checkApiKey(apiKey, provider);
    res.json(result);
  } catch (e) {
    res.status(500).json({
      ok: false,
      provider: req.body?.provider || '',
      message: e.message || 'Ошибка проверки ключа'
    });
  }
});

app.post('/api/analyze-photo', upload.single('image'), async (req, res) => {
  try {
    const apiKey = req.body.apiKey;
    const provider = req.body.provider || 'OPENAI';
    if (!apiKey) {
      return res.status(400).json({ error: 'API-ключ не передан' });
    }
    if (!req.file) {
      return res.status(400).json({ error: 'Фотография не передана' });
    }

    const analysis = await analyzePhoto({
      apiKey,
      provider,
      imageBuffer: fs.readFileSync(req.file.path),
      mimeType: req.file.mimetype || 'image/jpeg',
      meta: {
        photoNumber: Number(req.body.photoNumber || 1),
        projectTitle: req.body.projectTitle || '',
        productModel: req.body.productModel || '',
        language: req.body.language || 'BOTH'
      }
    });

    res.json({ analysis });
  } catch (e) {
    const msg = e.message || 'Ошибка анализа';
    const status = /ключ/i.test(msg) ? 401 : 500;
    res.status(status).json({ error: msg });
  } finally {
    if (req.file?.path) fs.unlink(req.file.path, () => {});
  }
});

app.post(
  '/api/export',
  upload.fields([
    { name: 'template', maxCount: 1 },
    { name: 'photos', maxCount: 100 }
  ]),
  async (req, res) => {
    const cleanup = [];
    try {
      const template = req.files?.template?.[0];
      const photos = req.files?.photos || [];
      if (!template) {
        return res.status(400).json({
          projectId: '',
          files: [],
          validation: {
            canExport: false,
            errors: ['RIN-шаблон не передан'],
            loadedPhotos: photos.length,
            usedPhotos: 0,
            skippedPhotos: 0,
            duplicatePhotos: 0,
            photosNeedingReview: 0,
            mappingErrors: 1,
            powerpointErrors: 0,
            pdfErrors: 0
          }
        });
      }

      let payload;
      try {
        payload = JSON.parse(req.body.payload || '{}');
      } catch {
        return res.status(400).json({
          projectId: '',
          files: [],
          validation: {
            canExport: false,
            errors: ['Некорректный payload'],
            loadedPhotos: photos.length,
            usedPhotos: 0,
            skippedPhotos: 0,
            duplicatePhotos: 0,
            photosNeedingReview: 0,
            mappingErrors: 1,
            powerpointErrors: 0,
            pdfErrors: 0
          }
        });
      }

      // Reject accidental API key leakage into export payload.
      if (payload.apiKey || payload.api_key) {
        delete payload.apiKey;
        delete payload.api_key;
      }

      const steps = payload.steps || [];
      const project = {
        id: payload.projectId,
        title: payload.title,
        productModel: payload.productModel,
        serialNumber: payload.serialNumber || ''
      };
      const language = String(payload.language || 'BOTH').toUpperCase();

      const photoFiles = photos.map((f) => ({
        path: f.path,
        originalname: f.originalname,
        filename: f.filename
      }));
      cleanup.push(template.path, ...photoFiles.map((p) => p.path));

      let validation = validateExportPayload({ steps, photoFiles });
      if (!validation.canExport) {
        return res.json({
          projectId: project.id,
          files: [],
          validation
        });
      }

      const outDir = path.join(GENERATED, project.id || 'unknown');
      fs.mkdirSync(outDir, { recursive: true });
      const mirrorDir = path.join(OUTPUT, project.id || 'unknown');
      fs.mkdirSync(mirrorDir, { recursive: true });

      const locales = language === 'RU' ? ['RU'] : language === 'EN' ? ['EN'] : ['RU', 'EN'];
      const files = [];
      let powerpointErrors = 0;
      let pdfErrors = 0;
      const genErrors = [];

      for (const locale of locales) {
        const pptxName = `RIN_Repair_Instruction_${locale}.pptx`;
        const pdfName = `RIN_Repair_Instruction_${locale}.pdf`;
        const pptxPath = path.join(outDir, pptxName);
        const pdfPath = path.join(outDir, pdfName);

        try {
          await generatePptx({
            templatePath: template.path,
            outputPath: pptxPath,
            locale,
            project,
            steps,
            photoFiles
          });
          fs.copyFileSync(pptxPath, path.join(mirrorDir, pptxName));
          files.push({
            name: pptxName,
            relativePath: `${project.id}/${pptxName}`,
            mimeType:
              'application/vnd.openxmlformats-officedocument.presentationml.presentation'
          });
        } catch (e) {
          powerpointErrors += 1;
          genErrors.push(`PowerPoint ${locale}: ${e.message}`);
        }

        try {
          await generatePdf({
            outputPath: pdfPath,
            locale,
            project,
            steps,
            photoFiles
          });
          fs.copyFileSync(pdfPath, path.join(mirrorDir, pdfName));
          files.push({
            name: pdfName,
            relativePath: `${project.id}/${pdfName}`,
            mimeType: 'application/pdf'
          });
        } catch (e) {
          pdfErrors += 1;
          genErrors.push(`PDF ${locale}: ${e.message}`);
        }
      }

      validation = {
        ...validation,
        powerpointErrors,
        pdfErrors,
        canExport: genErrors.length === 0 && files.length > 0,
        errors: [...validation.errors, ...genErrors]
      };

      res.json({
        projectId: project.id,
        files,
        validation
      });
    } catch (e) {
      res.status(500).json({
        projectId: '',
        files: [],
        validation: {
          canExport: false,
          errors: [e.message || 'Ошибка экспорта'],
          loadedPhotos: 0,
          usedPhotos: 0,
          skippedPhotos: 0,
          duplicatePhotos: 0,
          photosNeedingReview: 0,
          mappingErrors: 0,
          powerpointErrors: 1,
          pdfErrors: 1
        }
      });
    } finally {
      cleanup.forEach((p) => fs.unlink(p, () => {}));
    }
  }
);

app.get('/exports/:projectId/:fileName', (req, res) => {
  const projectId = path.basename(req.params.projectId);
  const fileName = path.basename(req.params.fileName);
  const filePath = path.join(GENERATED, projectId, fileName);
  if (!fs.existsSync(filePath)) {
    return res.status(404).send('Not found');
  }
  res.download(filePath, fileName);
});

const PORT = Number(process.env.PORT || 3000);
app.listen(PORT, '0.0.0.0', () => {
  console.log(`RIN Repair Agent backend listening on http://0.0.0.0:${PORT}`);
  console.log('Default Android emulator URL: http://10.0.2.2:3000');
});
