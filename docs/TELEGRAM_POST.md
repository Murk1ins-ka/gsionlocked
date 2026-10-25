# Telegram post / Пост для Telegram

## Русский

🚀 gsionlocked v0.3.0 runtime preview

Добавлена установка runtime для **ColorOS 17 / Find X9 Ultra IN** и **Evolution X 11.11 / Android 16** прямо из приложения. Выбираешь распакованный образ, нажимаешь **Install runtime** — приложение скачивает нужный пакет и проверяет SHA-256.

📥 APK и runtime:
https://github.com/Murk1ins-ka/gsionlocked/releases/tag/v0.3.0-runtime-preview

📦 Образы GSI:
https://github.com/Murk1ins-ka/gsionlocked/releases/tag/v0.2.0-coloros-preview

Как установить:
1. Скачай `gsionlocked.apk` из Assets релиза v0.3.0.
2. Скачай образ из v0.2.0: один `.img.xz` для EvoX или все четыре части `.001`–`.004` для ColorOS. Распакуй до `.img` на компьютере и перенеси на телефон.
3. В One UI восстанови root, открой приложение: **Select GSI → Browse files → .img → Install runtime**.
4. Разреши root, оставь приложение открытым до конца установки, затем нажми **Start**. Runtime скачивается автоматически: EvoX ~255 МБ, ColorOS ~556 МБ. Сам образ скачивается отдельно.

⚠️ Это экспериментальный выпуск. Первые runtime-пакеты требуют совместимой основы **Galaxy S23 / One UI Android 16 FZG1**; снятие проверки модели не означает поддержку всех телефонов. Установщик проверен офлайн, новая установка и запуск на физическом устройстве пока не проверены.

Перед запуском ColorOS отключи пароль/PIN и биометрию в One UI. В ColorOS пароль/PIN, отпечаток и Face Unlock не поддерживаются; входящие SMS и коды Google могут не приходить. Сохранение временного root после возврата не гарантируется.

📖 Инструкция, распаковка и исходники:
https://github.com/Murk1ins-ka/gsionlocked/blob/main/README.ru.md

## English

🚀 gsionlocked v0.3.0 runtime preview

In-app runtime installation is now available for **ColorOS 17 / Find X9 Ultra IN** and **Evolution X 11.11 / Android 16**. Import the extracted image and tap **Install runtime** — the app downloads the matching package and verifies its SHA-256.

📥 APK and runtime packages:
https://github.com/Murk1ins-ka/gsionlocked/releases/tag/v0.3.0-runtime-preview

📦 GSI images:
https://github.com/Murk1ins-ka/gsionlocked/releases/tag/v0.2.0-coloros-preview

Installation:
1. Download `gsionlocked.apk` from the v0.3.0 release Assets.
2. Download the image from v0.2.0: one `.img.xz` for EvoX or all four `.001`–`.004` parts for ColorOS. Extract to `.img` on your computer and copy it to your phone.
3. Restore root in One UI, open the app: **Select GSI → Browse files → .img → Install runtime**.
4. Grant root, keep the app open until installation finishes, then tap **Start**. The runtime downloads automatically: EvoX ~255 MB, ColorOS ~556 MB. Download the GSI image separately.

⚠️ Experimental release. Initial runtime packages require the compatible **Galaxy S23 / One UI Android 16 FZG1** host framework. Removing the model check does not provide support for every phone. The installer passed offline tests; a fresh installation and boot on physical hardware remain unverified.

Before starting ColorOS, disable the One UI PIN/password and biometrics. ColorOS PIN/password, fingerprint and face unlock are unsupported; incoming SMS and Google codes may not arrive. Temporary root retention after returning is not guaranteed.

📖 Instructions, extraction and source:
https://github.com/Murk1ins-ka/gsionlocked/blob/main/README.md
