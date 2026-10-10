# Разработка и проверка

Все команды запускаются из корня проекта. Модуль `:app` расположен в `src/app`. Нужны JDK 17, Android SDK Platform 36 и Python 3 для проверки переводов. На Windows команды ниже выполняются в PowerShell; на Linux/macOS заменяй `.\gradlew.bat` на `bash ./gradlew`.

## Сборка

Настрой `JAVA_HOME` на JDK 17 и `sdk.dir` в игнорируемом `local.properties` на установленный SDK. `scripts/setup-emulator.ps1` также создаёт этот файл. Для скриптов эмулятора задай `ANDROID_HOME` или передай путь SDK явно через `-SdkPath`. Конкретные пути этой машины записаны в локальной памяти агента.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
python scripts/check-localizations.py
```

| Артефакт | Путь |
| --- | --- |
| Debug APK | `src/app/build/outputs/apk/debug/app-debug.apk` |
| Release без локальной подписи | `src/app/build/outputs/apk/release/app-release-unsigned.apk` |
| Android test APK | `src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Unit XML | `src/app/build/test-results/testDebugUnitTest/`, `testReleaseUnitTest/` |
| Lint XML | `src/app/build/reports/lint-results-debug.xml` |

Release можно подписать переменными `SPACE_KEYSTORE_PATH`, `SPACE_KEYSTORE_PASSWORD`, `SPACE_KEY_ALIAS`, `SPACE_KEY_PASSWORD`. Все четыре должны быть заданы вместе. При настроенной подписи имя release APK будет другим; проверяй каталог outputs. Ключи и пароли хранятся вне Git.

## Эмулятор

Первоначальная установка и обычный запуск:

```powershell
.\scripts\setup-emulator.ps1 -SdkPath $env:ANDROID_HOME
.\scripts\debug-android.ps1 -SdkPath $env:ANDROID_HOME -AvdName Space_API_36 -Port 5556 -Headless
```

`setup-emulator.ps1` устанавливает компоненты SDK и создаёт AVD API 36. Для существующего AVD достаточно команды debug. Флаг `-Headless` удобен для тестов; без него открывается окно эмулятора. Скрипт debug собирает APK, устанавливает обновление и запускает приложение. Проверяй serial через `adb devices`; для порта 5556 это `emulator-5556`. Не закрывай чужие устройства или экземпляры эмулятора.

## Android-тесты с сохранением данных

На устройстве с нужными мирами, настройками и рекордами устанавливай обновления через `-r`. Вместо Gradle connected-тестов запускай instrumentation напрямую: Gradle может переустановить приложение. `adb` находится в `platform-tools` SDK; подставь свой путь и serial.

```powershell
$spaceAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$spaceSerial = 'emulator-5556'
& $spaceAdb -s $spaceSerial install -r src/app/build/outputs/apk/debug/app-debug.apk
& $spaceAdb -s $spaceSerial install -r src/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $spaceAdb -s $spaceSerial shell am instrument -w -r com.xekep.space.test/androidx.test.runner.AndroidJUnitRunner
```

Убедись, что подпись APK совместима с установленным приложением. Ошибка несовместимой подписи не повод автоматически удалять приложение с пользовательскими данными.

Для отдельного класса добавь к `am instrument` перед названием runner:

```text
-e class com.xekep.space.ui.space.ArcadePlaythroughUiTest
```

Проверяй содержимое результата: `OK`, количество тестов и отсутствие `FAILURES`, а не только exit code `adb`. Вывод `println` тестов попадает в logcat. Снимки и результат прохождения сохраняются в `/sdcard/Android/data/com.xekep.space/cache/`; их можно забрать через `adb pull`. Локальные журналы и изображения складывай в игнорируемые `dist/analysis/` и `dist/previews/`.

## Что проверять

Unit-тесты расположены в `src/app/src/test/`, Compose и Android-тесты — в `src/app/src/androidTest/`. Для физики нужны проверки траекторий, столкновений, сохранения массы/импульса и топлива. Для управления — реальные жесты и состояние камеры. В новой итерации аркады проверяй события, энергию, лимиты флота и награды вместе с прохождением.

`ArcadePlaythroughTest` прогоняет смешанную оборону на трёх сложностях с двумя seed. `ArcadePlaythroughUiTest` проходит первые 20 волн Normal через меню, кнопки и жесты, учитывая время удержания перед запуском. Время ускорено; автоматизация не оценивает удобство реакции человека. Показания наклона, вибрацию и ощущение управления дополнительно проверяй на физическом телефоне.

Последняя проверенная итерация на 2026-10-08: 226 unit-тестов для каждого варианта; проверены все 106 Android-сценариев (105 сразу, оставшийся вместе с повторным прогоном 15 тестов затронутого класса после исправления ожидания кадра). Проверены тяжёлые корпуса и цены, выбор наклона/джойстика, реальные жесты управления и зума, сброс управления при новой игре, сохранения, галактика и прохождение аркады. Полные результаты и ограничения находятся в [отчёте](implementation-report.md). Эти числа — датированная фиксация, а не постоянное требование к размеру набора.

Для изменения только Markdown проверь существование относительных ссылок и `git diff --check`; повторно собирать игру не требуется.

## CI и публикация

`.github/workflows/build.yml` проверяет проект и создаёт сборочные артефакты. `.github/workflows/release.yml` запускается по тегу `vMAJOR.MINOR.PATCH`, получает версию из тега, собирает подписанные APK/AAB и публикует релиз. Настройку секретов и ограничения версии смотри в [README](../README.md#github-actions-и-релизы) и самом workflow.

Обычные коммит и push не означают выпуск новой версии: релизный тег создаётся по отдельному запросу. При публикации проверь результат workflow и ссылку на готовый релиз.


## Плейтест с ограниченным темпом действий

`ArcadeHumanPaceUiTest` — отдельная длительная диагностика баланса. Без аргумента `balancePlaytest=true` её шесть методов пропускаются в обычном Android-прогоне. Каждый запуск делает попытку дойти до 21-й волны; проигрыш является измеренным результатом, а не ошибкой теста. Проверки гарантируют конечные координаты, нормальные границы энергии и ограничение выстрелов.

После установки актуального Android test APK:

```powershell
& $spaceAdb -s $spaceSerial shell am instrument -w -r -e class com.xekep.space.ui.space.ArcadeHumanPaceUiTest -e balancePlaytest true -e seed 17 com.xekep.space.test/androidx.test.runner.AndroidJUnitRunner
```

Повтори с `-e seed 73` для второго набора случайных волн. Методы Basic используют более медленную реакцию и обычный автопилот; Familiar — двух Стражей и маршруты через четыре точки. Дополнительный `-e gentleLaunch true` сравнивает более короткие броски без вычисления точной круговой скорости. Для отдельного сценария укажи, например, `-e class com.xekep.space.ui.space.ArcadeHumanPaceUiTest#normalFamiliar`.

Результаты `balance-<сложность>-<стратегия>-<seed>[-gentle].csv`, `*-result.txt` и PNG лежат в external cache приложения. В модели учитываются задержка реакции, выбор кнопки, удержание, бросок и постановка маршрута. Действия используют видимые тела и показатели HUD; точные координаты скрытых угроз, их будущие траектории и бесплатные ресурсы не используются. Автоматизированная модель не заменяет плейтест с людьми: она не обучается, не пилотирует вручную и не использует стрелки для упреждающей защиты.


Для изолированной проверки сопровождения запусти `ConvoyEscortUiTest`. Он начинает обычную 15-ю волну с исходной энергией и жизнями, находит конвой кнопкой, разворачивает два перехватчика реальными жестами с затратой игрового времени и проверяет доставку/награду на всех трёх сложностях. Это отдельный сценарий, а не полное прохождение:

```powershell
& $spaceAdb -s $spaceSerial shell am instrument -w -r -e class com.xekep.space.ui.space.ConvoyEscortUiTest com.xekep.space.test/androidx.test.runner.AndroidJUnitRunner
```

При сравнении коэффициентов сохраняй новые CSV в отдельном локальном каталоге: external cache использует те же имена и следующий прогон перезапишет результаты предыдущей версии. Сравнивай одинаковые seed и политику, учитывая, что изменение количества спавнов меняет последующую последовательность случайных чисел и сам состав волн.

### Профессиональный аудит управления и интерфейса

В `ArcadeHumanPaceUiTest` аргумент `-e heavyFleet true` заменяет удержание перехватчиков и ракет на 2,2 с. Стражи остаются обычными; расходы и время учитываются. `-e stableBodyIds true` сбрасывает счётчик ID перед созданием изолированного тестового мира: иначе порядок методов может менять сторону обхода препятствий. Имена результатов получают суффиксы `-heavy` и `-stable`. Сравнение обычного и тяжёлого флота требует одинаковых seed, ID, ориентации, размера экрана и настроек; это сравнение целых политик, не только урона.

`ProfessionalAuditUiTest` запускается с `-e professionalAudit true`: измеряет реальные жесты двух стиков и снимает меню, HUD 15-й волны, пилотирование и Солнечную систему на 320/360 dp с разными языками и шрифтом. Для отдельной альбомной матрицы укажи метод `#compactLargeFontAndLocalizedLayoutsMeasureBoundsAndClipping`, аргумент `-e landscapeAudit true` и сначала поверни устройство. Затем верни прежние параметры `accelerometer_rotation` и `user_rotation` и проверь фактическую ориентацию перед игровыми сравнениями. Контекст локализаций в матрице тестовый; переключение языка Activity и диалоги проверяет отдельно `RetroLanguageUiTest`. Файлы `professional-*.txt` и PNG сохраняются в external cache. Метод сообщает дефекты раскладки и может завершиться `OK` при обнаруженных обрезаниях — это измерение, не сертификат отсутствия проблем.

Unit-пробы `ArcadeReviewProbeTest` требуют JVM-свойства `professionalAudit=true` и `professionalAuditDir=<каталог>`. Передай их через временный Gradle init script:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'professionalAudit', 'true'
        systemProperty 'professionalAuditDir', rootProject.file('dist/analysis/professional-review').absolutePath
    }
}
```

Запуск: `./gradlew :app:testDebugUnitTest --tests '*ArcadeReviewProbeTest' -I <init-script>`, затем аналогично `:app:testReleaseUnitTest`. Пробы записывают девять коротких боевых сцен, давление в шести точных прохождениях и четыре масштаба времени ручного управления. Это вычислительная диагностика; стоимость шага не равна FPS, а действия с точным состоянием мира не равны человеческому прохождению. [Результаты аудита](professional-gameplay-audit-2026-10-10.md).


Для проверки обеих галактик используйте `SystemGalaxyUiTest`, `RandomSystemsUiTest` и `GalaxyUiTest` через `am instrument`. Новые тесты проверяют 1000 тел, выбор двух пресетов, отсутствие дубликатов генерации в инструментах, API отмены, JSON round-trip и фактический pinch при фоновой симуляции. Снимки `two-galaxies-menu.png`, `system-galaxy-1000.png`, `system-galaxy-family.png` сохраняются в external cache. `SYSTEM_GALAXY_ANDROID_STEP` измеряет 30 последовательных шагов физики по 1/30 с при включённых столкновениях; это стоимость расчёта на эмуляторе, не FPS и не результат физического телефона. Unit `SystemGalaxyTest` дополнительно проверяет четыре начальных seed и две минуты полного расчёта для двух seed.


`ManualClockTest` сравнивает управляемые ракету и корабль при нескольких масштабах времени, проверяет часы естественных тел и дрейф ракеты без топлива. `HudSafetyTest` проверяет измеренные границы указателей, их начало вне центра экрана, статус остаточных угроз и локализованный компактный счёт. `HudFlowUiTest` проверяет цену до запуска и реальное списание энергии, статусы отдыха и скрытие боевого HUD после поражения. В `MenuRedesignUiTest` режим наклона задан явно; перетаскивание скорости проверяется также на паузе песочницы, независимо от настроек предыдущего теста. Общий `PilotSpeedInput` обслуживает вертикальные шкалы FPV и горизонтальную шкалу наклона.


## Коммерческий кандидат и восстановление

API 36 собирается AGP 8.10.1 / Gradle 8.11.1 / JDK 17. SDK Platform 36 и образ `system-images;android-36;google_apis;x86_64` установлены локально; новый AVD `Space_API_36` не заменяет прежний с данными. [Комплект магазинов](store-kit/README.md) содержит локальную упаковку и условия подписи для Play/RuStore. [Полевой протокол](commercial-validation.md) отделяет реальные телефоны и людей от эмулятора.

На **собственном тестовом AVD** проверить два настоящих процесса (подготовка заменяет текущий забег этого AVD, не использовать на устройстве с нужной текущей игрой):

```powershell
& $spaceAdb -s $spaceSerial shell am instrument -w -r -e recoveryProcess prepare -e class com.xekep.space.ui.space.ProcessRecoveryTest#prepareDurableRun com.xekep.space.test/androidx.test.runner.AndroidJUnitRunner
& $spaceAdb -s $spaceSerial shell am force-stop com.xekep.space
& $spaceAdb -s $spaceSerial shell am instrument -w -r -e recoveryProcess verify -e class com.xekep.space.ui.space.ProcessRecoveryTest#verifyAfterForceStopInAnotherProcess com.xekep.space.test/androidx.test.runner.AndroidJUnitRunner
```

`SessionRecoveryTest` проверяет codec/AtomicFile/награды/галактику и отдельные ручные слоты; `CommercialUiTest` — первый сеанс, полёт, итог/бесконечность, испытания, privacy/качество. `StoreCaptureUiTest` запускается только с `-e storeCapture true`, сохраняет реальные подготовленные сцены интерфейса в externalCacheDir/store-capture. Это демонстрационные состояния, не прохождения человека. В обычном запуске process/capture тесты пропускаются.

Для сравнения качества в `WorldStartBenchmarkTest` передать `-e graphicsQuality Full` или `Economy`, `-e worldStartPreset Fleet` и уникальный `-e probeLabel`. Каждый вариант запускать отдельным instrumentation-процессом после завершения сборок; сохраняются интервалы первого/первых пяти/поздних пяти секунд и отдельные кадры. Настройка качества после теста возвращается. Метрики software-эмулятора не переносить на слабый телефон.


Для 16 KB создать отдельный AVD с `system-images;android-36;google_apis_ps16k;x86_64`, убедиться в `adb shell getconf PAGE_SIZE = 16384`. На нём `PageSizeCompatibilityTest` с `-e pageSizeCheck true` проверяет загрузку нативной библиотеки из APK и платформенную итерацию пути. Нужен APK из release AAB (локально допустима явно обозначенная debug-подпись для теста) и совместимый test APK. На обычном образе 4096 тест должен не запускаться с этим флагом. Переустановка с другой подписью и очистка данных для обхода ошибок не используются.


## Проверка финала кампании

`ArcadeCampaignTest` проверяет удержание волны, четырёхсекундную передышку, захват/пропуск трофея, броню и тяжёлый взрыв, однократные награды, дедлайн и победу. `ArcadeCampaignUiTest` выполняет настоящий двухпальцевый маршрут и проверяет фокус событий, поражение/повтор, результат/бесконечность и 10 состояний HUD на пяти языках при 320 dp и шрифте 150%. `SessionRecoveryTest` включает сохранение повреждённого носителя и полученной награды.

Для сенсорного диагностического прохождения через `ArcadeHumanPaceUiTest` добавьте `-e activeSorties true` к существующим `-e balancePlaytest true -e seed 17 -e stableBodyIds true`. Например:

```powershell
& $spaceAdb -s $spaceSerial shell am instrument -w -r -e balancePlaytest true -e seed 17 -e stableBodyIds true -e activeSorties true -e class com.xekep.space.ui.space.ArcadeHumanPaceUiTest#normalFamiliar com.xekep.space.test/androidx.test.runner.AndroidJUnitRunner
```

Вариант отправляет аппарат за видимым трофеем через реальные жесты, а в финале переводит камеру к носителю и атакует видимые узлы. Он использует обычные цены, энергию, лимиты, задержки реакции и неточное наведение; флот и урон не подменяются. Без `activeSorties` остаётся оборона у ядра. Сопоставляйте одинаковые seed/ID и сборку, сохраняйте обе версии файлов из external cache до следующего запуска. Это тестовая политика, не процент побед людей и не оценка интереса. Фиксированные IDs применяются только в изолированном тестовом мире.
