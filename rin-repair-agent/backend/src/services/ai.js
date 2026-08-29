import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROMPT_PATH = path.resolve(__dirname, '../../../prompts/analyze_photo.txt');

function loadPrompt(title, model, photoNumber) {
  let text = fs.readFileSync(PROMPT_PATH, 'utf8');
  return text
    .replaceAll('{{title}}', title || '')
    .replaceAll('{{model}}', model || '')
    .replaceAll('{{photoNumber}}', String(photoNumber || 1));
}

function extractJson(text) {
  if (!text) throw new Error('Пустой ответ AI');
  const cleaned = text.replace(/```json/gi, '').replace(/```/g, '').trim();
  const start = cleaned.indexOf('{');
  const end = cleaned.lastIndexOf('}');
  if (start < 0 || end < 0) throw new Error('AI не вернул JSON');
  return JSON.parse(cleaned.slice(start, end + 1));
}

function normalizeAnalysis(raw, photoNumber) {
  const confidence = Number(raw.confidence ?? 0);
  const needsManualReview = Boolean(raw.needsManualReview) || confidence < 0.55;
  let beginnerInstruction = String(raw.beginnerInstruction || '').trim();
  if (!beginnerInstruction || needsManualReview && confidence < 0.4) {
    beginnerInstruction =
      beginnerInstruction ||
      'Точное действие по этой фотографии определить невозможно. Требуется проверка мастером.';
  }
  return {
    photoNumber: Number(raw.photoNumber || photoNumber || 1),
    visibleObjects: Array.isArray(raw.visibleObjects) ? raw.visibleObjects.map(String) : [],
    visibleAction: String(raw.visibleAction || ''),
    repairStage: String(raw.repairStage || ''),
    tools: Array.isArray(raw.tools) ? raw.tools.map(String) : [],
    beginnerInstruction,
    importantWarning: String(raw.importantWarning || 'Требует проверки'),
    confidence: Math.max(0, Math.min(1, confidence)),
    needsManualReview,
    beginnerInstructionEn: String(raw.beginnerInstructionEn || beginnerInstruction),
    importantWarningEn: String(raw.importantWarningEn || raw.importantWarning || 'Requires verification'),
    repairStageEn: String(raw.repairStageEn || raw.repairStage || ''),
    visibleActionEn: String(raw.visibleActionEn || raw.visibleAction || '')
  };
}

async function checkOpenAI(apiKey) {
  const res = await fetch('https://api.openai.com/v1/models', {
    headers: { Authorization: `Bearer ${apiKey}` }
  });
  if (res.status === 401) {
    return { ok: false, provider: 'OPENAI', message: 'Неверный API-ключ OpenAI' };
  }
  if (!res.ok) {
    return { ok: false, provider: 'OPENAI', message: `OpenAI недоступен (HTTP ${res.status})` };
  }
  return { ok: true, provider: 'OPENAI', message: 'Ключ OpenAI действителен' };
}

async function checkGemini(apiKey) {
  const url = `https://generativelanguage.googleapis.com/v1/models?key=${encodeURIComponent(apiKey)}`;
  const res = await fetch(url);
  if (res.status === 400 || res.status === 403) {
    return { ok: false, provider: 'GEMINI', message: 'Неверный API-ключ Gemini' };
  }
  if (!res.ok) {
    return { ok: false, provider: 'GEMINI', message: `Gemini недоступен (HTTP ${res.status})` };
  }
  return { ok: true, provider: 'GEMINI', message: 'Ключ Gemini действителен' };
}

export async function checkApiKey(apiKey, provider) {
  if (!apiKey || !String(apiKey).trim()) {
    return { ok: false, provider: provider || '', message: 'API-ключ пустой' };
  }
  const p = String(provider || 'OPENAI').toUpperCase();
  if (p === 'GEMINI') return checkGemini(apiKey.trim());
  return checkOpenAI(apiKey.trim());
}

async function analyzeWithOpenAI(apiKey, imageBuffer, mime, meta) {
  const prompt = loadPrompt(meta.projectTitle, meta.productModel, meta.photoNumber);
  const b64 = imageBuffer.toString('base64');
  const res = await fetch('https://api.openai.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model: 'gpt-4o-mini',
      temperature: 0.2,
      response_format: { type: 'json_object' },
      messages: [
        {
          role: 'user',
          content: [
            { type: 'text', text: prompt },
            {
              type: 'image_url',
              image_url: { url: `data:${mime};base64,${b64}` }
            }
          ]
        }
      ]
    })
  });
  if (res.status === 401) throw new Error('Неверный API-ключ OpenAI');
  if (!res.ok) {
    const t = await res.text();
    throw new Error(`OpenAI ошибка: ${res.status} ${t.slice(0, 200)}`);
  }
  const data = await res.json();
  const content = data.choices?.[0]?.message?.content || '';
  return normalizeAnalysis(extractJson(content), meta.photoNumber);
}

async function analyzeWithGemini(apiKey, imageBuffer, mime, meta) {
  const prompt = loadPrompt(meta.projectTitle, meta.productModel, meta.photoNumber);
  const b64 = imageBuffer.toString('base64');
  const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${encodeURIComponent(apiKey)}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [
        {
          parts: [
            { text: prompt },
            { inline_data: { mime_type: mime, data: b64 } }
          ]
        }
      ],
      generationConfig: { temperature: 0.2, responseMimeType: 'application/json' }
    })
  });
  if (res.status === 400 || res.status === 403) throw new Error('Неверный API-ключ Gemini');
  if (!res.ok) {
    const t = await res.text();
    throw new Error(`Gemini ошибка: ${res.status} ${t.slice(0, 200)}`);
  }
  const data = await res.json();
  const content = data.candidates?.[0]?.content?.parts?.map((p) => p.text).join('\n') || '';
  return normalizeAnalysis(extractJson(content), meta.photoNumber);
}

export async function analyzePhoto({ apiKey, provider, imageBuffer, mimeType, meta }) {
  const p = String(provider || 'OPENAI').toUpperCase();
  if (p === 'GEMINI') {
    return analyzeWithGemini(apiKey, imageBuffer, mimeType || 'image/jpeg', meta);
  }
  return analyzeWithOpenAI(apiKey, imageBuffer, mimeType || 'image/jpeg', meta);
}
