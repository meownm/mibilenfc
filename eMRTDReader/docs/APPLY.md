# MRZ fix pack

Этот архив содержит полные итоговые версии файлов, которые можно **заменить** в твоём проекте.

## Что внутри

- `sdk/.../ocr/MrzTextProcessor.java` — добавлен `normalizeAndRepair()` и ремонт `<` (включая `KK`, «« и т.п.)
- `sdk/.../utils/MrzBurstAggregator.java` — исправлен доступ к MRZ-тексту (`asMrzText()` вместо `mrz.text`)
- `sdk/.../ui/MrzDebugOverlayView.java` — убраны блокеры `metrics.frameWidth/frameHeight` (через reflection + fallback)
- `sdk/.../ocr/TesseractMrzEngine.java` — убран `OEM_LSTM_ONLY` (через reflection + fallback)
- `app/.../layout/fragment_mrz_scan.xml` — валидный XML без ошибки `AttributePrefixUnbound`

## Важно: TesseractOcrEngine

В `sdk/.../ocr/TesseractOcrEngine.java` нужно убрать прямое использование `TessBaseAPI.OEM_LSTM_ONLY`.

Замена:

- добавь `import java.lang.reflect.Field;`
- добавь методы `resolveTessOemMode()` и `getStaticIntField()` (как в `TesseractMrzEngine`)
- замени `api.init(..., TessBaseAPI.OEM_LSTM_ONLY)` на `api.init(..., resolveTessOemMode())`

