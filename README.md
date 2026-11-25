# FerDeck

Нативный Stream Deck для управления Windows-ПК со смартфона в одной Wi-Fi сети.

## Структура
- `app/` — Android-приложение на Kotlin + Jetpack Compose, Material 3, StateFlow/ViewModel, OkHttp WebSocket, Kotlinx Serialization.
- `server/server.py` — Windows WebSocket-сервер с PIN, хоткеями, макросами, media-клавишами, громкостью, запуском программ по allowlist и телеметрией.
- `.github/workflows/android-apk.yml` — сборка APK на GitHub Actions.

## Сборка Android
Откройте репозиторий в Android Studio (JDK 17). Используются AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, compile/target SDK 35 и min SDK 26. Запустите Gradle task `assembleDebug`. APK будет в `app/build/outputs/apk/debug/app-debug.apk`. Workflow собирает APK при push в main и при ручном запуске (Actions → Android APK); файл доступен как artifact `FerDeck-debug-apk`.

## Сервер Windows
1. Установите Python 3.10+.
2. Откройте PowerShell в каталоге `server`.
3. Выполните `py -m venv .venv`.
4. Активируйте окружение: `.venv\\Scripts\\Activate.ps1`.
5. Установите пакеты: `pip install -r requirements.txt`.
6. Запустите `python server.py`.

В консоли появятся IP, порт (по умолчанию 8765) и PIN. В приложении откройте ⚙ и введите эти данные. Разрешите Python входящие подключения в Windows Firewall для частной сети. Телефон и ПК должны быть в одной Wi-Fi сети. PIN можно задать переменной окружения `FERDECK_PIN`, порт — `FERDECK_PORT`.

## Безопасность
Команды запуска программ принимаются только по ID из словаря `COMMANDS` в `server.py`. Не добавляйте запуск произвольных shell-строк. Используйте только в доверенной локальной сети.

## Примечания
Реализованы четыре страницы 4×3, свайп-переключение, нижняя навигация, редактор плиток, JSON-сохранение и импорт/экспорт, демо-телеметрия, WebSocket, reconnect, серверные хоткеи/макросы/media/volume/launch и CPU/RAM/GPU telemetry. UI дополнительных сеточных пресетов, drag-and-drop обмена и полноценного volume slider с двусторонней синхронизацией пока нет — это следующий этап разработки.
