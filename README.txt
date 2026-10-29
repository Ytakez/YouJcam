YouJcam — ретро-камера (фото и видео) под Android.

Сборка (нужны JDK 17 и Android SDK 34):
  1) Создать local.properties:  sdk.dir=/путь/к/Android/Sdk
  2) Если нет gradlew:  gradle wrapper --gradle-version 8.2
  3) ./gradlew assembleDebug --no-daemon
  APK: app/build/outputs/apk/debug/app-debug.apk

Проект сделан лёгким для сборки на 1.2 ГБ ОЗУ:
без Compose, без CameraX, без сторонних библиотек (только core, appcompat, material).
Параметры памяти Gradle лежат в gradle.properties.

Где что:
  Preset.kt          — 10 пресетов (Мп, цвет, зерно, параметры видео)
  PhotoProcessor.kt  — обработка фото
  MainActivity.kt    — камера, фото, запись видео (MPEG-4 + AMR-NB)
  res/mipmap*        — иконка

v1.1:
  Quality.kt         — 5 уровней качества фото и видео (от худшего к лучшему)
  AppSettings.kt     — хранение настроек
  SettingsActivity   — экран настроек (Material 3)
  Preset.maxZoom     — максимальный зум каждой модели (S5610 = 8x), правится в Preset.kt
