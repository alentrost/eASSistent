# eASSistent

Android aplikacija za hiter pregled šolskega urnika iz sistema eAsistent. Aplikacija pridobi urnik neposredno s spletne strani urniki.easistent.com, ga razčleni in prikaže v pregledni obliki.

## Novosti v različici 1.2 (V1.2)

- **Popolno simetrično centriranje 3-dnevnega pogleda**: Odpravljena napaka pri robnih odmikih (Android RTL resolucija), ki je povzročala zamik stolpcev in prikaz roba 4. dneva. Fokusirani dan je sedaj vedno v popolnem matematičnem središču zaslona z enakomernimi presledki.
- **Kompakten in pregleden izbirnik tedna**: Prenovljeno pogovorno okno z zaobljenimi vogali, prikazom trenutnega tedna po koledarskem datumu (`Danes po datumu: Teden X (...)`), značko `danes`, kljukico ob izbranem tednu ter datumi od ponedeljka do nedelje za vsak teden.
- **Optimiziran prikaz ur z delitvijo v dve skupini**: Pri predmetih, ki se delijo v dve skupini, se naziv predmeta prikaže le enkrat na vrhu s črtkano ločnico, spodaj pa sta profesor na levi in učilnica na desni ločeni v pregledni vrstici za vsako skupino.
- **Poenotena višina vseh šolskih ur**: Vsi bloki ur v 3-dnevnem pogledu imajo enakomerno razporejeno višino (`rowWeight = 1.0f`).
- **Nova barvna usklajenost malice**:
  - **Temni način (Dark Mode)**: Sodobna sivkasto-vijolična / lavender paleta (`#231C38` z nežno vijolično obrobo `#6B46C1` in besedilom `#E9D5FF`), ki se naravno zlije s temno modrim ozadjem in odpravlja moteče neonske rumene tone.
  - **Svetli način (Light Mode)**: Mehka topla medeno-jantarna krema z nežnimi prehodi in ločenim zatemnjenim videzom za dneve, ki niso danes.

---

## Funkcionalnosti

### Pogledi

- **3-dnevni pogled** -- privzeti pogled, ki prikazuje urnik za prejšnji, trenutni in naslednji dan v treh vertikalnih stolpcih. Trenutni dan je poudarjen, stranska dneva pa zatemnjena z gradientom proti robovom zaslona.
- **Tedenski tabelarni pogled** -- alternativni pogled, ki prikazuje celoten tedenski urnik v horizontalno drsljivi tabeli. Dostopen preko 3-pikčnega menija.

### Navigacija med dnevi in tedni

- **Zvezno drsenje z 0-zakasnitvijo** -- neposredno sledenje prstu med drsenjem s takojšnjim odzivom ter gladko animacijo prehoda med dnevi brez občutka zakasnitve.
- **Prejšnji in naslednji teden** -- navigacijski puščici `<` in `>` ob nazivu tedna v glavi aplikacije omogočata hiter prehod med tedni prek uradnega AJAX vmesnika eAsistent.
- **Kompakten izbirnik tedna** -- klik na naziv tedna odpre hiter izbirnik tednov z razponom datumov in avtomatskim zaznavanjem tekočega tedna.
- **Zvezno drsenje med tedni** -- podrs preko petka samodejno odpre naslednji teden, podrs pred ponedeljkom pa prejšnji teden.
- **Gumb Danes** -- ponastavi pogled na trenutni šolski dan in tekoči teden.

### Prilagajanje zaslonom

- Celostna odzivnost z namenskimi dimenzijami (`values`, `values-sw320dp`, `values-sw400dp`), ki zagotavljajo optimalna razmerja in berljivost na različno velikih napravah in zaslonskih razmerjih.

### Spremljanje v realnem času in časovni pas

- Čas in datumi so usklajeni s slovenskim časovnim pasom (`Europe/Ljubljana`).
- Samodejno označevanje trenutne šolske ure (modra poudaritev).
- Označevanje naslednje ure med odmorom (vijolična poudaritev).
- Vizualno razločevanje preteklih in prihodnjih ur ter malice.

### Lokalno shranjevanje

Urnik se ob osvežitvi shrani lokalno v pomnilnik naprave. Ob ponovnem zagonu se naloži takoj brez internetne povezave.

### Nastavitve

- Temni in svetli način (Material 3).
- Premik plavajočega gumba za osvežitev (levo ali desno).
- Nastavitev lastne URL povezave do eAsistent urnika.

## Tehnične podrobnosti

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
├── MainActivity.java        # Glavna aktivnost, gestna navigacija, 3-dnevni in tabelarni pogled
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
3. Za prehod med dnevi podrsljajte levo ali desno z neposredno animacijo ali uporabite puščici v glavi.
4. Za prehod med tedni uporabite navigacijski puščici `<` in `>` ob nazivu tedna ali kliknite na naziv tedna za izbiro poljubnega tedna.
5. Urnik se shrani lokalno in je ob naslednjem zagonu takoj na voljo.

## Licenca

Ta projekt je namenjen osebni uporabi.
