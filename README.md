# Space

Космическая игра и гравитационная песочница для Android, написанная на Kotlin и Jetpack Compose. Работает без интернета; сохранения находятся на устройстве.

## Режимы и управление

- **Arcade** — защищайте ядро от метеоров, создавая небесные тела. Следите за энергией, жизнями и множителем очков.
- **Sandbox Mode** — экспериментируйте с орбитами, гравитацией и столкновениями. Доступны три слота сохранения и загрузки.
- Удерживайте палец, чтобы увеличить массу создаваемого тела; перетаскивание задаёт начальную скорость. Отпустите палец для запуска.
- Жесты масштабирования и перемещения управляют камерой. **Menu** приостанавливает симуляцию и открывает переключение режимов, сброс и сохранения.

## Структура

```text
src/app/                   Android-модуль :app
  src/main/java/           Kotlin: sim — физика, storage — сохранения, ui — интерфейс
  src/main/res/            Ресурсы Android
  src/main/AndroidManifest.xml
.github/workflows/         Проверки, сборка и публикация релизов
scripts/                   Настройка и запуск эмулятора на Windows
gradle/wrapper/            Gradle Wrapper
```

Gradle-конфигурация и wrapper остаются в корне: команды сборки запускаются отсюда, имя модуля — `:app`.

## Локальная сборка

Требования: **JDK 17**, Android SDK с **Platform 35** и **Build Tools 35.0.0**. Минимальная версия Android на устройстве — **8.0 / API 26**. Gradle 8.9 скачивается wrapper автоматически; Android Gradle Plugin — 8.7.3.

Откройте корень проекта в Android Studio или установите [Android SDK Command-line Tools](https://developer.android.com/studio). Задайте `ANDROID_HOME` либо создайте локальный `local.properties`:

```properties
sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk
```

Установите пакеты SDK. Современные Command-line Tools включают [Android CLI](https://developer.android.com/tools/agents/android-cli); для предыдущих версий используйте `sdkmanager`:

```powershell
$sdk = $env:ANDROID_HOME
if (Test-Path "$sdk/cmdline-tools/latest/bin/android.exe") {
    & "$sdk/cmdline-tools/latest/bin/android.exe" --no-metrics sdk install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
} else {
    & "$sdk/cmdline-tools/latest/bin/sdkmanager.bat" --licenses
    & "$sdk/cmdline-tools/latest/bin/sdkmanager.bat" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
}
.\gradlew.bat :app:check :app:assembleDebug
```

На Linux/macOS используйте `bash ./gradlew` вместо `.\gradlew.bat`. Готовый debug APK: `src/app/build/outputs/apk/debug/app-debug.apk`.

## Эмулятор для отладки на Windows

После установки JDK и SDK выполните из корня проекта:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-emulator.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\debug-android.ps1
```

Первый скрипт принимает лицензии SDK, устанавливает Android Emulator и образ Android 15 / API 35 x86_64, создаёт **Space_API_35** с профилем Pixel 5 и настраивает `local.properties` и пользовательский `ANDROID_HOME`. Повторный запуск сохраняет существующий AVD. SDK по умолчанию находится в `%LOCALAPPDATA%\Android\Sdk`; другой путь можно передать через `-SdkPath`.

Второй скрипт запускает AVD, ждёт загрузки Android, собирает и устанавливает debug APK и открывает Space. Для работы без окна добавьте `-Headless`, для программной эмуляции — `-NoAcceleration`. Если аппаратное ускорение недоступно, скрипт автоматически использует программную эмуляцию. Она заметно медленнее; ожидание загрузки ограничено 10 минутами. Другой эмулятор на порту 5554 можно обойти параметром `-Port 5556`.

SDK и виртуальному устройству нужны несколько гигабайт свободного места. Для хранения AVD на другом диске передайте `-AvdHome E:\Android\avd` скрипту настройки; он сохранит пользовательскую переменную `ANDROID_AVD_HOME`. На текущей локальной машине SDK установлен в `E:\Android\Sdk`, AVD — в `E:\Android\avd`; пути находятся в локальных настройках и не требуются другим разработчикам.

Для быстрой работы включите виртуализацию AMD-V/VT-x в BIOS и **Windows Hypervisor Platform** в компонентах Windows, затем перезагрузите компьютер. [Инструкция Android по аппаратному ускорению](https://developer.android.com/studio/run/emulator-acceleration).

На текущей машине аппаратное ускорение настроено через **Android Emulator Hypervisor Driver (AEHD) 2.2**. Google завершает поддержку AEHD 31 декабря 2026 года; затем используйте Windows Hypervisor Platform по инструкции выше.

```powershell
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
& $adb -s emulator-5554 logcat
& $adb -s emulator-5554 emu kill
```

Для точек останова откройте проект в Android Studio и подключите отладчик к `com.xekep.space` через **Attach debugger to Android process**. Логи самого эмулятора: `%TEMP%\space-emulator-5554.out.log` и `.err.log`.

## GitHub Actions и релизы

**Build** запускается для push в `main`, pull request и вручную. Выполняет `:app:check`, собирает debug и release APK; debug APK и отчёты доступны в artifacts запуска. Без ключа локальная release-сборка создаёт неподписанный APK, который нельзя установить напрямую.

**Release** запускается при push тега **`vMAJOR.MINOR.PATCH`**, например `v1.0.0`. Проверяет проект, собирает подписанные APK и AAB, создаёт GitHub Release с файлами `Space-<version>.apk`, `Space-<version>.aab` и `SHA256SUMS.txt`. При повторном запуске обновляет файлы существующего релиза. AAB предназначен для загрузки в магазин; на устройство устанавливается APK.

Перед первым релизом создайте постоянный ключ подписи вне репозитория:

```powershell
keytool -genkeypair -v -keystore "$env:USERPROFILE/space-release.jks" -alias space -keyalg RSA -keysize 4096 -validity 10000
```

Добавьте в **Settings → Secrets and variables → Actions** четыре repository secrets:

| Secret | Значение |
| --- | --- |
| `SPACE_KEYSTORE_BASE64` | Содержимое `.jks`, закодированное в Base64 |
| `SPACE_KEYSTORE_PASSWORD` | Пароль хранилища |
| `SPACE_KEY_ALIAS` | Псевдоним ключа, например `space` |
| `SPACE_KEY_PASSWORD` | Пароль ключа |

Получить Base64 для переноса в secret можно так:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE/space-release.jks")) | Set-Clipboard
```

Храните резервную копию ключа и паролей: обновления приложения должны использовать тот же ключ. Workflow завершится ошибкой, если secrets отсутствуют, и удаляет временное хранилище после сборки. `GITHUB_TOKEN` выдаётся Actions автоматически, отдельный PAT не нужен.

После коммита и push исходников:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

`versionName` берётся из тега, `versionCode` вычисляется как `MAJOR × 1000000 + MINOR × 1000 + PATCH`. Допустимые значения: major до 2099, minor и patch до 999; код должен быть положительным. Компоненты тега указываются без ведущих нулей. Для следующего релиза повышайте версию. По умолчанию локальная сборка имеет версию `1.0.0` и код `1000000`; переопределить их можно через `'-PversionName=1.0.1' '-PversionCode=1000001'` (кавычки нужны в PowerShell).

Для локальной подписи задайте `SPACE_KEYSTORE_PATH` (абсолютный путь к `.jks`), `SPACE_KEYSTORE_PASSWORD`, `SPACE_KEY_ALIAS` и `SPACE_KEY_PASSWORD`, затем выполните `:app:assembleRelease :app:bundleRelease`. Ключи, SDK, сборки и локальные настройки исключены из Git.
