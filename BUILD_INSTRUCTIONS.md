# Інструкції зі збірки vrYo

## Передумови

1. **Android Studio** (Arctic Fox або новіша версія)
2. **Android SDK** (API 26+)
3. **JDK 17** або новіша версія
4. **Пристрій або емулятор** з Android 8.0+ (API 26+)

## Крок 1: Налаштування Android SDK

1. Відкрийте Android Studio
2. Перейдіть до `File → Settings → Appearance & Behavior → System Settings → Android SDK`
3. Переконайтеся що встановлено:
   - Android SDK Platform 34
   - Android SDK Build-Tools
   - Android SDK Platform-Tools

## Крок 2: Клонування та відкриття проєкту

```bash
git clone <repository-url>
cd vrYo
```

Або відкрийте Android Studio → `File → Open` → оберіть папку `vrYo`

## Крок 3: Налаштування local.properties

Створіть файл `local.properties` в корені проєкту:

```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

Замініть `YourUsername` на ваше ім'я користувача.

## Крок 4: Синхронізація Gradle

Android Studio автоматично запропонує синхронізувати проєкт. Натисніть "Sync Now" або:
- `File → Sync Project with Gradle Files`

## Крок 5: Збірка проєкту

### Варіант A: Через Android Studio
1. `Build → Make Project` (Ctrl+F9 / Cmd+F9)
2. Після успішної збірки: `Build → Build Bundle(s) / APK(s) → Build APK(s)`

### Варіант B: Через командний рядок

#### Windows:
```cmd
gradlew.bat assembleDebug
```

#### Linux/Mac:
```bash
./gradlew assembleDebug
```

APK буде створено в: `app/build/outputs/apk/debug/app-debug.apk`

## Крок 6: Встановлення на пристрій

### Варіант A: Через USB
1. Увімкніть USB debugging на пристрої:
   - `Settings → About phone` → натисніть "Build number" 7 разів
   - `Settings → Developer options` → увімкніть "USB debugging"
2. Підключіть пристрій до комп'ютера
3. Через Android Studio: `Run → Run 'app'` (Shift+F10 / Ctrl+R)
4. Або вручну: `adb install app/build/outputs/apk/debug/app-debug.apk`

### Варіант B: Через Gradle
```bash
./gradlew installDebug
```

## Крок 7: Перевірка дозволів

При першому запуску додаток автоматично запросить дозволи:
- ✅ Камера
- ✅ Мікрофон (для голосових команд)

Дозвольте всі дозволи для повної функціональності.

## Відомі проблеми та вирішення

### Проблема: "SDK location not found"
**Рішення**: Перевірте `local.properties` та шлях до Android SDK

### Проблема: "Gradle sync failed"
**Рішення**: 
1. Перевірте інтернет-з'єднання (завантаження залежностей)
2. `File → Invalidate Caches / Restart`

### Проблема: "Camera not available"
**Рішення**: 
- Перевірте дозвіл камери в `Settings → Apps → vrYo → Permissions`
- Переконайтеся що пристрій має камеру

### Проблема: "Voice commands not working"
**Рішення**:
- Перевірте дозвіл мікрофона
- Переконайтеся що Google Speech Services встановлено
- Спробуйте перезапустити додаток

## Тестування на різних пристроях

### Samsung Galaxy A35 5G (рекомендований)
- ✅ Android 14
- ✅ 120Hz дисплей (fallback до 60Hz)
- ✅ Камера та датчики працюють добре

### Емулятор (не рекомендовано)
- ⚠️ Камера може не працювати
- ⚠️ Датчики руху обмежені
- ⚠️ Використовуйте для тестування UI

## Розробка та дебаг

### Увімкнення логування
В Android Studio: `View → Tool Windows → Logcat`

Фільтр за тегом:
- `CameraRenderer`
- `VoiceController`
- `InputController`
- `SensorHelper`

### Профілювання продуктивності
`View → Tool Windows → Profiler`

Перевірте:
- CPU usage
- Memory usage
- Frame rate (повинен бути ~60 FPS)

## Публікація Release версії

1. Створіть keystore:
```bash
keytool -genkey -v -keystore vrYo-release.keystore -alias vrYo -keyalg RSA -keysize 2048 -validity 10000
```

2. Створіть `keystore.properties`:
```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=vrYo
storeFile=../vrYo-release.keystore
```

3. Оновіть `app/build.gradle.kts` для release signing

4. Зберіть release APK:
```bash
./gradlew assembleRelease
```

APK буде в: `app/build/outputs/apk/release/app-release.apk`

---

**Готово!** Додаток готовий до використання. Для питань звертайтеся до README.md

