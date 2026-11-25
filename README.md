# FerDeck

Нативный Android Stream Deck для управления Windows-ПК по локальной Wi-Fi сети.

## Реализовано

### Android
- Kotlin, Jetpack Compose, Material 3, ViewModel + StateFlow.
- AMOLED-интерфейс, анимация нажатий и haptic feedback.
- 4 профиля-страницы: «Основное», «Игры», «Медиа», «Система»; свайпы и нижняя навигация.
- Адаптивная сетка: в портретном режиме используется заданное число колонок, в альбомном — минимум 5 колонок. Пресеты 4×3, 5×3 и 6×3.
- Редактор плиток: хоткей, макрос, запуск программы, media, громкость, телеметрия; заголовок, emoji, акцентный цвет, параметры.
- Обмен плиток через режим «Обмен», очистка ячейки.
- Макросы с задержками в формате `ctrl+c;wait:250;ctrl+v`.
- Общая громкость Windows с debounce-передачей значений.
- CPU/RAM/GPU telemetry, история последних 60 сэмплов и sparkline на Canvas (CPU/RAM/GPU).
- WebSocket + PIN, автоматическое переподключение, статус соединения.
- Демо-режим с симуляцией метрик и JSON-команд в логе/Snackbar.
- Локальное JSON-сохранение конфигурации и импорт/экспорт через системный файловый пикер.
- Опции «Не выключать экран» и полноэкранный режим.

### Windows companion
- Python 3.10+, WebSocket-сервер с PIN.
- Хоткеи и макросы, системные media-команды.
- Общая громкость через pycaw; fallback через Windows media keys.
- Запуск приложений только по фиксированному allowlist ID.
- CPU/RAM через psutil, NVIDIA GPU через nvidia-smi; при отсутствии NVIDIA возвращается -1.

## Структура

- `app/` — нативное Android-приложение.
- `server/server.py` — сервер-компаньон Windows.
- `server/requirements.txt` — Python-зависимости.
- `.github/workflows/android-apk.yml` — сборка APK при push и вручную.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/` — Gradle Wrapper 8.9.

## Android Studio и APK

1. Откройте репозиторий в Android Studio.
2. Используйте JDK 17; Gradle Wrapper автоматически скачает Gradle 8.9.
3. Выполните задачу `assembleDebug` (или Build → Build Bundle(s) / APK(s) → Build APK(s)).
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Параметры: minSdk 26, targetSdk 35, compileSdk 35, AGP 8.7.3, Kotlin 2.0.21. Зависимости Compose Material 3, OkHttp 4.12.0 и kotlinx.serialization.

GitHub Actions: вкладка Actions → Android APK. После успешной сборки скачайте artifact `FerDeck-debug-apk`.

## Запуск Windows-сервера

В PowerShell из каталога репозитория:

```powershell
cd server
py -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python server.py
```

В консоли появятся IP компьютера, порт (по умолчанию 8765) и шестизначный PIN. В приложении откройте ⚙, введите IP/порт/PIN и нажмите «Подключить». Телефон и ПК должны быть в одной Wi-Fi сети. Если Windows Firewall спросит разрешение, разрешите входящие подключения Python для частной сети.

Переменные окружения: `FERDECK_PORT` и `FERDECK_PIN`.

## Команды запуска

В редакторе плитки «Запуск программы» доступны ID: `notepad`, `calculator`, `steam`, `browser`, `explorer`. Изменить список можно в словаре `COMMANDS` файла `server.py`. Клиент не может передать произвольный путь или shell-команду.

## Важно

- Сервер предназначен для доверенной локальной сети; не пробрасывайте порт в интернет.
- Для глобальных хоткеев библиотеке keyboard на некоторых конфигурациях Windows могут потребоваться права администратора.
- GPU-показатель доступен при наличии NVIDIA-драйвера и `nvidia-smi`; для других GPU отображается «—».
- Fallback громкости через media keys приблизительный; для точного уровня используется pycaw.
