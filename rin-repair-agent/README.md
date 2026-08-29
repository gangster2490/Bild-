# RIN Repair Agent

Android-приложение для создания ремонтных инструкций RIN по фотографиям.

**Backend и адрес сервера не требуются.** AI (OpenAI / Gemini), PowerPoint и PDF работают на телефоне.

Пользователь снимает ремонт камерой (или выбирает фото / ZIP). AI анализирует каждое фото и формирует пошаговую инструкцию для новичка. На выходе:

- `RIN_Repair_Instruction_RU.pptx`
- `RIN_Repair_Instruction_EN.pptx`
- `RIN_Repair_Instruction_RU.pdf`
- `RIN_Repair_Instruction_EN.pdf`

**RIN-шаблон не встроен в APK.** Его нужно загрузить после установки через Storage Access Framework.

## Структура

```
rin-repair-agent/
├── android/     # Kotlin + Jetpack Compose (всё на устройстве)
├── backend/     # устаревший опциональный модуль (не нужен приложению)
├── prompts/     # исходники промптов (копия в android assets)
├── output/
└── README.md
```

## 1. Как открыть проект

1. Откройте папку `rin-repair-agent/android` в Android Studio (File → Open).
2. Дождитесь синхронизации Gradle.

## 2. Backend

Не нужен. Приложение ходит напрямую в OpenAI/Gemini и генерирует PPTX/PDF локально.

Папка `backend/` сохранена только для справки и не используется APK.

## 3. Как собрать APK

```bash
cd rin-repair-agent/android
./gradlew assembleDebug
```

Готовый файл:

```
rin-repair-agent/android/app/build/outputs/apk/debug/app-debug.apk
```

## 4. Как установить APK на телефон

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Или скопируйте `app-debug.apk` на телефон и установите вручную.

## 5. Как ввести API-ключ

При первом запуске:

1. Введите API-ключ OpenAI или Gemini.
2. Нажмите **Проверить ключ** (нужен интернет до провайдера).
3. Нажмите **Сохранить**.

Ключ хранится через **Android Keystore** (не в APK, не в логах, на экране только последние 4 символа).

## 6. Как добавить RIN-шаблон

1. **Добавить RIN-шаблон** → выбрать `.pptx` (также ZIP/JSON/PDF).
2. Шаблон копируется во внутреннее хранилище.
3. Доступны замена и удаление с подтверждением.

## 7. Как сделать первый ремонт

1. Добавьте RIN-шаблон и API-ключ.
2. **Новый ремонт** → название, модель, язык.
3. Фото: камера / галерея / ZIP.
4. После каждого фото — локальное сохранение и AI-анализ.
5. Экран **Проверка** — правки, порядок, подтверждение.

Без завершения проверки экспорт недоступен. При потере интернета фото и описания сохраняются локально.

## 8. Как получить PowerPoint и PDF

1. Подтвердите фото на проверке.
2. Экспорт покажет счётчики и остановится при ошибках.
3. **Создать PowerPoint и PDF** — файлы создаются **на телефоне** (без сервера).

API-ключ не попадает в PPTX/PDF/логи экспорта.

## Требования

- Android API 26+
- Интернет только для вызовов OpenAI/Gemini
- Kotlin, Jetpack Compose, Material 3, CameraX, Photo Picker, Coroutines, OkHttp
- Android Keystore + Encrypted storage
