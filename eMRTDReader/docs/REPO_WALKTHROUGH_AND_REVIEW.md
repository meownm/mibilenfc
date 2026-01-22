# Обход репозитория: фактическая архитектура, трассировка пайплайнов, замечания

_Дата: 2026-01-22_

## Структура репозитория

- `eMRTDReader/` — Android проект.
  - `app/` — демо-приложение (экраны MRZ и NFC).
  - `sdk/` — библиотека (OCR/MRZ/NFC/crypto), которую использует `app`.
  - `docs/` — документация (включая исходный `ARCHITECTURE.md`).

## MRZ OCR: фактический поток выполнения (как работает сейчас)

### 1) UI и CameraX

1. `MRZScanActivity` создаёт `ImageAnalysis` и назначает анализатор `MrzImageAnalyzer`.
2. Пользователь выбирает режим OCR в UI (Auto dual / ML Kit / Tesseract). Выбор пробрасывается в анализатор.

### 2) Анализ кадра (SDK)

Файл: `sdk/.../analyzer/MrzImageAnalyzer.java`

1. На каждом кадре `analyze(ImageProxy)`:
   - проверяется частотный лимит (intervalMs);
   - конвертация `ImageProxy -> Bitmap` через `ImageProxyUtils.toBitmap`;
   - поворот в upright-ориентацию по `rotationDeg`;
   - запуск фоновой задачи `pipelineExecutor.submit(() -> runPipeline(upright))`.
2. В `runPipeline`:
   - контролируется флаг `ocrInFlight` (если OCR уже идёт, новые кадры получают состояния `OCR_IN_FLIGHT` или `MRZ_OCR_TIMEOUT`);
   - считаются метрики кадра через `FrameStats.compute` (яркость/контраст/резкость/шум);
   - определяется ROI MRZ: сначала `MrzAutoDetector.detect`, при неуспехе fallback ROI;
   - ROI стабилизируется через `RectAverager`;
   - ROI масштабируется (чтобы строка MRZ имела целевой размер по высоте);
   - запускается `DualOcrRunner.runAsync(...)`.

### 3) OCR и MRZ-постобработка

Файл: `sdk/.../ocr/DualOcrRunner.java`

- В `AUTO_DUAL` оба движка стартуют параллельно.
- Текст для UI выбирается по правилу: предпочесть ML Kit, если он не пустой; иначе взять Tesseract.
- MRZ строится только из Tesseract-результата (так зафиксировано в политике `DualOcrRunner`).
- Затем включаются:
  - нормализация/выделение строк MRZ (`MrzTextProcessor`, `MrzTextNormalizer`);
  - валидация формата и checksum (`MrzCandidateValidator`, `MrzValidation`, `MrzScore`);
  - repair критичных мест (`MrzRepair`);
  - burst-агрегация результатов по нескольким кадрам (`MrzBurstAggregator`).

### 4) Возврат в UI

- Анализатор вызывает `MrzImageAnalyzer.Listener`.
- `MRZScanActivity` переводит обновления на UI-поток через `runOnUiThread` и обновляет:
  - overlay состояния (по `ScanState`),
  - логи,
  - предпросмотр OCR и финальный MRZ.

## NFC: фактический поток выполнения

1. `NFCReadActivity` получает MRZ/CAN и ждёт NFC Tag.
2. `NfcPassportReader.read(ctx, tag, mrz, can)`:
   - открывает `IsoDep`, выставляет timeout;
   - создаёт `PassportService` (JMRTD) и делает `sendSelectApplet` best-effort;
   - `JmrtdAccessController.establish(...)`: PACE (если есть EF.CardAccess и ключ), иначе BAC;
   - читает DG1 (обязательно), DG2 (опционально), SOD (опционально);
   - собирает `PassportChipData` (ФИО, гражданство, даты, фото если доступно);
   - делает `PassiveAuthVerifier.verify(ctx, sodBytes, dgs)`.

## Что важно отметить по текущему состоянию

### 1) Два разных MrzPipelineExecutor

В проекте есть два класса с одинаковым именем:
- `sdk/.../analysis/MrzPipelineExecutor.java` — реализация с очередью 1 и политикой «держать только последнюю задачу».
- `sdk/.../analyzer/MrzPipelineExecutor.java` — реализация, которая просто отбрасывает новые задачи, пока текущая не завершена.

Риски:
- рассинхрон поведения с документацией;
- путаница при развитии пайплайна;
- сложно объяснить ожидаемую модель нагрузки.

Рекомендация: выбрать один вариант и привести naming/package к единому стандарту (это соответствует вашему правилу синхронизации именований).

### 2) Тяжёлая функция FrameStats.compute

`FrameStats.compute(Bitmap)` выделяет массивы размером `width*height` и считает метрики по всему кадру.
Если `intervalMs` выставлен низко, это приведёт к:
- росту аллокаций и GC;
- падению FPS камеры;
- нестабильности задержек OCR.

Рекомендации:
- считать метрики по ROI (а не по целому кадру);
- перейти на вычисления по Y-плоскости без создания ARGB массива;
- переиспользовать буферы (pool) и избегать новых `int[]/double[]` на каждом запуске.

### 3) Конвертация ImageProxy->Bitmap через JPEG

`ImageProxyUtils.toBitmap` делает NV21->JPEG->Bitmap, что является тяжёлой операцией.
Плюсы: совместимость и простота.
Минусы: CPU и аллокации.

Рекомендации:
- оставить как fallback, но для «боевого» режима добавить YUV->RGB конвертер без JPEG (например, через `ScriptIntrinsicYuvToRGB` на старых API или через `RenderEffect`/`Allocation` альтернативы),
- либо полностью уйти в работу по Y-плоскости до момента, когда нужен Bitmap.

### 4) Runtime-артефакты для закрытого контура

- Tesseract traineddata скачивается из GitHub. В закрытом контуре это не сработает.
- CSCA сертификаты не лежат в `assets/csca` (только README), из-за чего пассивная аутентификация не сможет строить доверенную цепочку.

Рекомендации:
- сделать режим поставки traineddata в составе приложения или через внутренний репозиторий;
- добавить механизм обновления/управления CSCA-набором (пусть даже вручную через assets в демо).

### 5) Неиспользуемый анализ-пайплайн (analysis/MrzPipelineFacade)

В `sdk/analysis` есть полноценный пайплайн (`MrzFrameGate`, `MrzLocalizer`, `MrzTracker`, `MrzPipelineFacade`, `MrzStateMachine`) и тесты на него, но `app` его не использует.

Риски:
- две параллельные реализации логики;
- тесты покрывают один пайплайн, UI работает на другом;
- сложнее диагностировать деградации.

Рекомендация: определить, какая реализация является «боевой», и либо интегрировать facade в `MrzImageAnalyzer`, либо явно зафиксировать границы (facade как будущий refactor) в docs.

## Минимальный перечень решений, которые стоит зафиксировать (decision log)

1. Политика источника MRZ: «MRZ только из Tesseract» (уже отражено в `DualOcrRunner`).
2. Стратегия обработки кадров: «keep latest» или «drop while busy» (сейчас есть оба варианта).
3. Поставка traineddata для Tesseract в закрытом контуре: embedded или внутренний URL.
4. Поставка CSCA сертификатов и их обновление.
