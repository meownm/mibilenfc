# MRZ OCR: перечень файлов и назначение

_Дата: 2026-01-22_

Этот файл фиксирует, где именно находится логика MRZ OCR в репозитории и зачем нужен каждый файл.

## Быстрый ответ на вопрос про Kotlin

В текущем архиве `.kt` файлов нет. Проект целиком на Java.

## MRZ OCR (UI + SDK)

- `eMRTDReader/app/src/main/java/com/example/emrtdreader/MRZScanActivity.java`
  - UI-экран сканирования MRZ. Запуск CameraX, выбор режима OCR, отображение логов, подсветка состояния, ручной ввод MRZ-полей.
- `eMRTDReader/app/src/main/res/layout/activity_mrz_scan.xml`
  - Разметка экрана MRZScanActivity (PreviewView, overlay, лог, элементы ручного ввода).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/analyzer/MrzImageAnalyzer.java`
  - CameraX ImageAnalysis.Analyzer: конвертация кадра, детект ROI MRZ, запуск OCR (DualOcrRunner), агрегация результатов, выдача ScanState.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/analyzer/ImageProxyUtils.java`
  - Надёжная конвертация ImageProxy(YUV_420_888) в Bitmap через NV21+JPEG. Критична для корректной обработки кадров.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/analyzer/MrzPipelineExecutor.java`
  - Executor для фонового MRZ-пайплайна в пакете analyzer. Сейчас реализован как «не принимать новые задачи, пока выполняется текущая».
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/analyzer/YuvBitmapConverter.java`
  - Альтернативный конвертер YUV->Bitmap для тестов/служебных целей (используется в юнит-тестах).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/analyzer/RateLimiter.java`
  - Ограничение частоты запуска тяжёлых операций (OCR) на потоке анализатора.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/analyzer/FrameEnvelope.java`
  - Контейнер для данных кадра/ROI/таймингов (используется в тестах и внутренней логике).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/DualOcrRunner.java`
  - Оркестратор OCR: ML Kit / Tesseract / AUTO_DUAL. Политика: MRZ формируется только из Tesseract, ML Kit используется для «быстрого текста/фидбека».
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/MlKitOcrEngine.java`
  - Движок OCR на базе ML Kit. Возвращает сырой текст, используется как быстрый источник текста.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/TesseractOcrEngine.java`
  - Движок OCR на базе tess-two. Вызывает TesseractDataManager для подготовки traineddata, выполняет распознавание.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/TesseractMrzEngine.java`
  - MRZ-специфичный режим Tesseract (настройки whitelist/psm, конфигурация под MRZ).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/TesseractDataManager.java`
  - Подготовка traineddata: при отсутствии скачивает файл с GitHub tessdata_best в /files/tessdata. Для оффлайна требуется локальная поставка traineddata.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/MrzAutoDetector.java`
  - Детерминированный детектор полосы MRZ по энергии границ (без ML). Возвращает Rect ROI.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/RectAverager.java`
  - Стабилизация ROI между кадрами (усреднение/сглаживание прямоугольника).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/MrzPreprocessor.java`
  - Предобработка ROI перед OCR (градации серого, бинаризация, масштабирование и т.п.).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/ThresholdSelector.java`
  - Подбор порога/параметров бинаризации под MRZ (адаптивные варианты).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/AdaptiveThreshold.java`
  - Алгоритм адаптивной бинаризации для подготовительного этапа.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/OcrRouter.java`
  - Маршрутизация между движками OCR (в т.ч. выбор режима, параметры).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/FrameStats.java`
  - Метрики качества изображения (яркость/контраст/резкость/шум) по пикселям Bitmap. Используется для логов и эвристик.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/MrzTextProcessor.java`
  - Постобработка OCR-текста под MRZ (очистка, нормализация, извлечение кандидатов строк).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/MrzCandidateValidator.java`
  - Фильтрация кандидатов MRZ (длина, допустимые символы, базовые проверки).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/ocr/MrzScore.java`
  - Скоринг кандидатов MRZ (в т.ч. на основе checksum/формата).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/mrz/MrzTextNormalizer.java`
  - Нормализация MRZ-текста в канонический вид (замены, очистка, приведение длины строк).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/utils/MrzBurstAggregator.java`
  - Агрегация результатов по нескольким кадрам (voting/burst) и выдача «зафиксированного» MRZ.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/utils/MrzChecksum.java`
  - Реализация checksum ICAO 9303.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/utils/MrzValidation.java`
  - Проверка контрольных сумм для TD3/TD1 и получение MrzChecksums.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/utils/MrzRepair.java`
  - Checksum-guided repair: точечные замены O/0, I/1 и др. в числовых полях MRZ.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/utils/MrzNormalizer.java`
  - Утилиты общего уровня для нормализации MRZ (взаимодействует с MrzTextNormalizer).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/utils/MrzParserValidator.java`
  - Валидация результатов парсинга MRZ (форматы, длины, минимальные проверки).
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/utils/MrzParser.java`
  - Преобразование MrzResult -> AccessKey.Mrz (docNumber/dob/doe) для NFC доступа. Требует корректной длины строк MRZ.

## Примечания по runtime-зависимостям

- `TesseractDataManager` при отсутствии `*.traineddata` скачивает их с GitHub в каталог приложения. Для закрытого контура нужно заранее положить traineddata локально или заменить источник загрузки на внутренний.
- В SDK предусмотрена загрузка CSCA сертификатов из `assets/csca`, но в архиве нет самих сертификатов, только README.
