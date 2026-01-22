# NFC eMRTD: перечень файлов и назначение

_Дата: 2026-01-22_

## NFC (UI + SDK)

- `eMRTDReader/app/src/main/java/com/example/emrtdreader/NFCReadActivity.java`
  - UI-экран чтения чипа eMRTD: получение MRZ/CAN, запуск чтения NFC, вывод данных DG1/DG2 и результата проверки пассивной аутентификации.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/data/NfcPassportReader.java`
  - Основной ридер eMRTD через IsoDep + JMRTD: select applet, доступ (PACE/BAC), чтение DG1/DG2/SOD, сбор результата.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/data/JmrtdAccessController.java`
  - Контроллер доступа: best-effort PACE (CAN предпочтительно), fallback BAC. Реализован через reflection для совместимости с версиями JMRTD.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/crypto/PassiveAuthVerifier.java`
  - Проверка пассивной аутентификации: сверка SOD и хэшей DG, валидация подписей по CSCA.
- `eMRTDReader/sdk/src/main/java/com/example/emrtdreader/sdk/crypto/CscaStore.java`
  - Загрузка CSCA сертификатов из assets/csca/*.cer|*.crt|*.der. Сейчас в assets лежит только README, сертификаты нужно положить отдельно.

## Примечания

- `NfcPassportReader` читает DG1 обязательно, DG2 и SOD опционально.
- Пассивная аутентификация (`PassiveAuthVerifier`) требует CSCA сертификаты. В текущем состоянии без `assets/csca/*.cer` проверка, как правило, будет возвращать «нет доверенной цепочки».
