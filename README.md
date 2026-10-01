# eASSistent

Android aplikacija za hiter pregled šolskega urnika iz sistema eAsistent. Aplikacija pridobi urnik neposredno s spletne strani urniki.easistent.com, ga razčleni in prikaže v pregledni obliki.

## Funkcionalnosti

### Pogledi

- **3-dnevni pogled** -- privzeti pogled, ki prikazuje urnik za prejšnji, trenutni in naslednji dan v treh vertikalnih stolpcih. Trenutni dan je poudarjen, stranska dneva pa zatemljena z gradientom proti robovom zaslona.
- **Tedenski tabelarni pogled** -- alternativni pogled, ki prikazuje celoten tedenski urnik v horizontalno drsljivi tabeli. Dostopen preko menija.

### Spremljanje v realnem casu

- Samodejno oznacevanje trenutne solske ure (mocna modra oznaka).
- Oznacevanje naslednje ure med odmorom (sibkejsa vijolicna oznaka).
- Vizualno razlocevanje preteklih in prihodnjih ur.

### Lokalno shranjevanje

Urnik se ob osvezitvi shrani lokalno. Ob ponovnem zagonu aplikacije se prikazejo shranjeni podatki brez cakanja na omrezje. Osvezitev se sprozi izkljucno rocno s pritiskom na gumb.

### Navigacija

- Navigacijske puscice ali vodoravno drsenje za premikanje med dnevi.
- Gumb za takojsen skok na trenutni dan.

### Nastavitve

- Temni in svetli nacin (Material 3).
- Premik gumba za osvezitev na levo ali desno stran zaslona.
- Nastavitev URL povezave do urnika.

## Tehnicne podrobnosti

| Parameter             | Vrednost            |
| --------------------- | ------------------- |
| Jezik                 | Java 11             |
| Min SDK               | 24 (Android 7.0)    |
| Target SDK            | 37                  |
| Razclenjanje HTML     | jsoup 1.23.2        |
| Oblikovni sistem      | Material Design 3   |
| Shranjevanje podatkov | SharedPreferences + notranji pomnilnik |

## Arhitektura

```
com.example.eassistent
├── MainActivity.java        # Glavna aktivnost, pogledi, navigacija
├── data/
│   └── UrnikStorage.java    # Lokalno shranjevanje urnika in nastavitev
├── model/
│   ├── ScheduleData.java    # Podatkovni model celotnega urnika
│   ├── DaySchedule.java     # Model posameznega dne
│   ├── PeriodSchedule.java  # Model posamezne solske ure
│   └── ClassItem.java       # Model posameznega predmeta
├── network/
│   └── UrnikFetcher.java    # HTTP zahteva z laznim brskalniskim profilom
└── parser/
    └── UrnikParser.java     # Razclenjanje HTML urnika z jsoup
```

## Namestitev

### Iz izdaje (Release)

Prenesite najnovejso APK datoteko s strani [GitHub Releases](https://github.com/alentrost/eASSistent/releases).

Za namestitev APK datoteke na Android napravi omogocite namestitev aplikacij iz neznanih virov.

### Iz izvorne kode

```bash
git clone https://github.com/alentrost/eASSistent.git
cd eASSistent
./gradlew assembleDebug
```

Zgrajena APK datoteka se nahaja v `app/build/outputs/apk/debug/`.

## Uporaba

1. Ob prvem zagonu vnesite URL povezavo do vasega urnika na eAsistent (oblika: `https://urniki.easistent.com/urniki/.../oddelki/.../dijak/...`).
2. Pritisnite gumb za osvezitev, da prenesete urnik.
3. Urnik se shrani lokalno in je ob naslednjem zagonu takoj na voljo.

## Licenca

Ta projekt je namenjen osebni uporabi.
