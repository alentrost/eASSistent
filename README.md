# eASSistent

Android aplikacija za hiter pregled šolskega urnika iz sistema eAsistent. Aplikacija pridobi urnik neposredno s spletne strani urniki.easistent.com, ga razčleni in prikaže v pregledni obliki.

## Funkcionalnosti

### Pogledi

- **3-dnevni pogled** -- privzeti pogled, ki prikazuje urnik za prejšnji, trenutni in naslednji dan v treh vertikalnih stolpcih. Trenutni dan je poudarjen, stranska dneva pa zatemljena z gradientom proti robovom zaslona.
- **Tedenski tabelarni pogled** -- alternativni pogled, ki prikazuje celoten tedenski urnik v horizontalno drsljivi tabeli. Dostopen preko menija.

### Navigacija med tedni in dnevi

- **Prejšnji in naslednji teden** -- puščici ob oznaki tedna omogočata hiter prehod med preteklimi in prihodnjimi tedni prek uradnega AJAX vmesnika eAsistent.
- **Izbira tedna** -- klik na oznako tedna ali meni odpre izbirnik vseh tednov v šolskem letu (od 1 do 53).
- **Zvezno drsenje** -- podrs preko ponedeljka ali petka samodejno odpre ustrezen sosednji teden.
- **Gumb Danes** -- ponastavi pogled na trenutni dan in tekoči teden.

### Prilagajanje zaslonom

- Celostna odzivnost z namenskimi dimenzijami (`values`, `values-sw320dp`, `values-sw400dp`), ki zagotavljajo čitljivost in pravilna razmerja na različno velikih napravah ter razmerjih stranic.

### Spremljanje v realnem casu in casovni pas

- Čas in datumi so fiksirani na slovenski časovni pas (`Europe/Ljubljana`).
- Samodejno označevanje trenutne šolske ure (modra poudaritev).
- Označevanje naslednje ure med odmorom (vijolična poudaritev).
- Vizualno razločevanje preteklih in prihodnjih ur ter malice.

### Lokalno shranjevanje

Urnik se ob osvežitvi shrani lokalno v pomnilnik naprave. Ob ponovnem zagonu se naloži takoj brez internetne povezave.

### Nastavitve

- Temni in svetli način (Material 3).
- Premik plavajočega gumba za osvežitev (levo ali desno).
- Nastavitev lastne URL povezave do eAsistent urnika.

## Tehnicne podrobnosti

| Parameter             | Vrednost            |
| --------------------- | ------------------- |
| Jezik                 | Java 11             |
| Min SDK               | 24 (Android 7.0)    |
| Target SDK            | 37                  |
| Časovni pas           | Europe/Ljubljana    |
| Razčlenjanje HTML     | jsoup 1.23.2        |
| Oblikovni sistem      | Material Design 3   |
| Shranjevanje podatkov | SharedPreferences + notranji pomnilnik |

## Arhitektura

```
com.example.eassistent
├── MainActivity.java        # Glavna aktivnost, navigacija, odzivni pogledi
├── data/
│   └── UrnikStorage.java    # Lokalna hramba urnika in uporabniških nastavitev
├── model/
│   ├── ScheduleData.java    # Podatkovni model urnika (tedni, metapodatki)
│   ├── DaySchedule.java     # Model posameznega šolskega dne
│   ├── PeriodSchedule.java  # Model posamezne šolske ure
│   └── ClassItem.java       # Model predmeta, profesorja in učilnice
├── network/
│   └── UrnikFetcher.java    # HTTP odjemalec in AJAX nalaganje tednov
└── parser/
    └── UrnikParser.java     # Razčlenjanje celotnega HTML in AJAX odzivov
```

## Namestitev

### Iz izdaje (Release)

Prenesite najnovejšo APK datoteko s strani [GitHub Releases](https://github.com/alentrost/eASSistent/releases).

Za namestitev APK datoteke na Android napravi omogočite namestitev aplikacij iz neznanih virov.

### Iz izvorne kode

```bash
git clone https://github.com/alentrost/eASSistent.git
cd eASSistent
./gradlew assembleDebug
```

Zgrajena APK datoteka se nahaja v `app/build/outputs/apk/debug/`.

## Uporaba

1. Ob prvem zagonu vnesite URL povezavo do vašega urnika na eAsistent (oblika: `https://urniki.easistent.com/urniki/.../oddelki/.../dijak/...` ali `.../razredi/.../dijak/...`).
2. Pritisnite gumb za osvežitev, da prenesete urnik.
3. Za prehod med tedni uporabite navigacijski puščici `<` in `>` ob nazivu tedna ali izberite želeni teden v meniju.
4. Urnik se shrani lokalno in je ob naslednjem zagonu takoj na voljo.

## Licenca

Ta projekt je namenjen osebni uporabi.
