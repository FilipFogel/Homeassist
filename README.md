# Hemma – elpris & smart start för Android

Privat Android-app (Kotlin + Jetpack Compose) med hemskärmswidget. Fas 1:

- **Elpris just nu** för ditt elområde (SE1–SE4), med nivå *Billigt / Normalt / Dyrt*
- **Prisgraf** per kvart för idag och imorgon (morgondagen kommer ca kl 13)
- **De 4 billigaste timmarna** varje dygn (antalet går att ändra)
- **Bästa starttid och kostnad** för varje maskin: tvätt, torktumlare, elbil, disk …
  – vad det kostar då, vad det kostar om du startar nu och hur mycket du sparar
- **Elbil** med "klar senast"-tid (t.ex. 07:00) – appen hittar billigaste laddfönstret före dess
- **Widget** med pris just nu, kommande billigaste timmar och bästa starttid och kostnad för upp till tre maskiner

Priserna är Nord Pools day-ahead-priser från [Elpriset just nu.se](https://www.elprisetjustnu.se) (gratis, ingen API-nyckel). Allt sparas bara i telefonen.

---

## Installera

### Alternativ A – GitHub bygger APK:n åt dig (ingen Android Studio behövs)

Varje push till `main` startar körningen **Bygg APK** under fliken **Actions** (ca 5 min). Den kan också startas för hand där med *Run workflow*.

1. Öppna repots **Releases** på telefonen → **Hemma – senaste bygget**.
2. Ladda ner `hemma.apk`, öppna den och tillåt *Installera okända appar*.

APK:n finns också under **Artifacts** på varje körning (`hemma-apk`, som zip).

Nya versioner installeras ovanpå den gamla (samma signeringsnyckel ingår i projektet), så dina maskiner och inställningar finns kvar.

### Alternativ B – Android Studio

Öppna mappen i Android Studio, vänta på Gradle-synken och tryck **Run** med telefonen inkopplad.

### Alternativ C – bygg lokalt från terminalen

Kräver JDK 17 eller senare och ett Android SDK (platform 35, build-tools 35).

1. Skapa `local.properties` i projektroten (ignoreras av git):
   ```
   sdk.dir=C:/Users/<du>/AppData/Local/Android/Sdk
   ```
2. Se till att `JAVA_HOME` pekar på en JDK ≥ 17 och bygg:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
   .\gradlew.bat assembleDebug
   ```
3. APK:n hamnar i `app/build/outputs/apk/debug/app-debug.apk`. Med telefonen inkopplad (USB-felsökning på):
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
   ```

### Lägg till widgeten

Håll fingret på hemskärmen → **Widgetar** → **Hemma – elpris**. Den är gjord för 4×2 rutor men går att ändra storlek på. Tryck på widgeten för att öppna appen, eller på ↻ för att uppdatera.

---

## Kom igång

1. **Inställningar** → välj elområde och fyll i dina avgifter (exkl. moms):
   - *Elhandelns påslag* – står på elräkningen (ofta 3–8 öre)
   - *Elnätets överföringsavgift* – står på nätfakturan (varierar mycket, 10–50 öre)
   - *Energiskatt* – 36 öre/kWh under 2026 (förifyllt)
2. **Maskiner** → ändra typvärdena till dina egna maskiner/program:
   - *Energi per körning (kWh)* och *programtid*. Står i bruksanvisningen; på energimärkningen anges ofta kWh per 100 cykler – dela med 100.
   - Elbil: *energi att ladda* (t.ex. 75 kWh-batteri från 20 % till 80 % = 45 kWh) och laddboxens effekt (t.ex. 11 kW). Laddförluster på 10 % räknas in.
   - Lägg gärna in flera program som egna rader, t.ex. "Tvätt 40°" och "Tvätt 60°".

## Så räknar appen

- **Totalpris** per kWh = (spotpris + påslag + nätavgift + energiskatt) × moms.
- **Billigaste timmarna** = timmedel av kvartspriserna, de *N* lägsta per dygn.
- **Bästa start** = det sammanhängande fönster (på kvartsgräns, eller nu) med lägst total kostnad inom kända priser och före eventuell "klar senast"-tid. Energin antas fördelas jämnt över programtiden.
- Bakgrundsjobb hämtar priser och uppdaterar widgeten ungefär var 15:e minut.

## Projektstruktur

```
app/src/main/java/se/hemma/app/
  data/      Models.kt (maskiner, inställningar), PriceRepository.kt (API + cache), SettingsStore.kt
  domain/    Planner.kt (billigaste timmar, bästa fönster), Dashboard.kt, Fmt.kt
  ui/        HomeScreen, MachinesScreen, SettingsScreen, PriceChart, MainViewModel, theme/
  widget/    PriceWidgetProvider.kt, WidgetUpdater.kt (RemoteViews)
  work/      RefreshWorker.kt (WorkManager)
```

## Idéer för nästa fas

- Notis när morgondagens priser kommit ("ladda bilen 01–05 i natt")
- Effekttariff från nätbolaget (dyrare timmar vardagar dagtid)
- Styrning via Home Assistant, Tibber, Easee/Zaptec eller smarta uttag
- Egen förbrukningsprofil per program (tvätt drar mest i början när vattnet värms)
