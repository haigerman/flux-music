<img src="docs/icon.png" width="128" alt="App-Icon: Keimling, der aus einer Klangwelle wächst">

# Flux Music

Android-App, die **live Musik komponiert** – passend zu Stimmung und Zweck. Tonart,
Akkordfolgen, Rhythmen, Melodien und der Aufbau der Stücke entstehen in Echtzeit auf dem
Handy; gespielt wird mit **Aufnahmen echter Instrumente** (Konzertflügel, Streicher,
Schlagzeug, Bass, Gitarre …). Die Musik läuft endlos und wiederholt sich nie exakt.

| Stimmung | Klang | Tempo |
|---|---|---|
| 🔥 **Sport · Bass & Groove** | Treibende elektronische Beats: Supersaw-Flächen, die mit der Bassdrum pumpen, rollender Bass, Arpeggios; echte Hi-Hats, Becken, Handclaps und Snare-Wirbel; Steigerung → „Drop“ | 110–175 BPM |
| 🤘 **Sport · Rock** | Geradliniger Hardrock im Stil der großen Pub-Rock-Bands: Studio-Schlagzeug, E-Bass in Achteln, zwei echte Gitarren links/rechts über Röhrenverstärker mit Powerchords und Pausen zwischen den Akkorden, Fills, Blues-Solo mit Bendings; Strophe → Refrain → Solo | 100–170 BPM |
| 🏜️ **Sport · Desert Rock** | Instrumentaler, schwerer Wüsten-/Post-Rock: tief gestimmte Gitarren mit dickem Fuzz, Halftime-Riffs, cleane Arpeggien mit viel Hall und Echo, zweistimmige Gitarrenmelodien, lange Spannungsbögen bis zum massiven Höhepunkt; Intro → Steigerung → Riff → Thema → Atempause → Höhepunkt | 70–140 BPM |
| 🎧 **Fokus** | Lo-Fi-Hip-Hop zum Arbeiten & Lernen: Schlagzeug übers Lo-Fi-Mikrofon mit Swing, Wurlitzer-E-Piano mit Jazz-Akkorden, gezupfter Kontrabass, Glockenspiel oder Flöte, Vinyl-Knistern und leichtes Band-Leiern; bewusst gleichmäßig | 65–100 BPM |
| 🎨 **Kreativ** | „Organic House“: Congas, Bongos, Shaker, Handclaps, Flügel-Akkorde, E-Bass, Marimba-Arpeggios, Flötenmelodie, Streicher | 96–124 BPM |
| 🌿 **Entspannen** | Kammermusik ohne Schlagzeug: Konzertflügel wie von einem Pianisten gespielt (mit Pedal und Rubato), Streicherensemble, Kontrabass, Harfe, Glockenspiel, leises Meeresrauschen | 50–80 BPM |
| 🌙 **Einschlafen** | Noch langsamer, tiefer und dunkler: Flügel, Celli und Bratschen, Kontrabass, Harfe, Meeresrauschen | 40–60 BPM |

## Funktionen

- **Varianten** – Sport gibt es als „Bass & Groove“, „Rock“ oder „Desert Rock“
- **Tempo** je Stimmung/Variante frei einstellbar (beim Laufen z. B. passend zur Schrittfrequenz)
- **Intensität** von sanft bis kraftvoll – blendet Instrumente weich ein und aus
  (z. B. Fokus: von „nur Klavier“ bis „mit Beat & Melodie“)
- **Variabilität** von gleichbleibend bis abwechslungsreich: Muster entwickeln sich alle
  paar Takte weiter, Akkorde werden durch verwandte ersetzt, neue Melodiemotive, wechselnde
  Besetzung, eine Dynamik-Kurve von Phrase zu Phrase, kürzere Teile und mehr Fills.
  Ganz links klingt es exakt wie die ursprüngliche, gleichbleibende Fassung.
- **Neues Stück** 🎲 – blendet in ein frisch komponiertes Stück über
- Wechsel der Stimmung während der Wiedergabe mit weicher Überblendung
- Stücke entwickeln sich selbst weiter: Intro → Groove → Thema/Steigerung → Höhepunkt →
  Atempause …; danach beginnt ein neuer Teil mit neuen Mustern und oft neuer Tonart
- Anzeige, was gerade erklingt (Tonart · Akkord · Teil) und ein Play-Knopf, der im Takt pulsiert
- Wiedergabe im Hintergrund mit Benachrichtigung (Pause / Neues Stück)
- Pause bei Anruf/anderer Audio-App und beim Abziehen der Kopfhörer
- Einstellungen werden je Stimmung und Variante gespeichert

Mindestens Android 8.0 (API 26), Ziel-API 36.

## Wie die Musik entsteht

Ein Taktgeber läuft in Sechzehnteln (mit Swing, wo es passt). Für jede Stimmung gibt es
einen eigenen „Komponisten“, der festlegt, welche Instrumente wann was spielen:

- **Harmonie:** Tonart und Akkordfolge werden aus stiltypischen Folgen gewählt
  (z. B. i–VI–III–VII für Sport, ii–V–I–vi mit Septakkorden für Fokus). Die Akkorde werden
  mit Stimmführung gesetzt, d. h. die Töne wandern beim Akkordwechsel möglichst wenig.
- **Melodie:** Auf betonten Zählzeiten Akkordtöne, dazwischen meist kleine Schritte,
  gelegentlich ein Sprung. Die zweite Hälfte einer Phrase wiederholt Rhythmus und Kontur
  der ersten (Frage → Antwort) und endet ruhig auf einem Akkordton. So entstehen
  wiedererkennbare Motive.
- **Rhythmus:** stiltypische Muster (Four-on-the-Floor, Boom-Bap, House-Offbeats …),
  je Stück neu kombiniert, mit leichten menschlichen Ungenauigkeiten.
- **Riffs (Rock):** Powerchords auf I, IV, ♭VII, ♭III und V, rhythmisch aus kleinen
  Bausteinen zusammengesetzt (klingen lassen, kurz anschlagen, abdämpfen, Pause),
  gelegentlich ein Blues-Lauf auf der tiefen Saite. Ein Riff wird – wie im echten Rock –
  wiederholt, bis der nächste Songteil kommt. Nachgespielt wird nichts.
- **Spielweise:** Instrumente werden wie von Musikern gespielt – Klavier mit Pedal,
  Melodiebögen mit Crescendo/Decrescendo und leichtem Rubato, Akkorde leicht gebrochen
  angeschlagen, Streicher, die beim Akkordwechsel ineinander übergehen, Gitarren, deren
  Saiten beim neuen Anschlag verstummen, Schlagzeug minimal hinter dem Beat.
- **Klang:** echte Aufnahmen aus freien Sample-Bibliotheken (siehe [CREDITS.md](CREDITS.md)):
  mehrere Anschlagstärken je Ton und mehrere Varianten pro Schlag, damit sich nichts
  maschinell wiederholt. Ein eigener Sampler spielt sie tonhöhengenau (kubische
  Interpolation, nahtlose Loops für gehaltene Töne). Die Gitarren laufen durch eine
  Verstärker-Simulation (Röhren-Sättigung mit 2-fach-Überabtastung, Klangregelung,
  4×12-Box), die elektronischen Stile nutzen Synthesizer (Supersaw mit 7 Stimmen).
- **Mischung:** wie im Studio – EQ und Kompressor je Instrument, Gruppen-Busse
  (z. B. Schlagzeug-Bus mit Parallel-Kompression), Sidechain-„Pumpen“, ein dichter
  FDN-Hall mit frühen Reflexionen, Echo, Summenkompressor, Bandsättigung und ein
  Peak-Limiter.

Die APK ist ca. 36 MB groß (davon 33 MB Instrumente). Beim ersten Abspielen eines Stils
werden dessen Instrumente einmalig dekodiert und im App-Cache abgelegt (je nach Stil
50–260 MB, für alle Stile zusammen ca. 370 MB); danach starten sie sofort. Der Cache kann
jederzeit in den Android-Einstellungen geleert werden.

## Aufbau

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Startet die Oberfläche |
| `ui/MusicScreen.kt` | Hauptbildschirm: Stimmungen, Tempo, Intensität, Play |
| `PlaybackService.kt` | Wiedergabe im Hintergrund, Benachrichtigung, Audiofokus |
| `data/MusicRepository.kt` | Gemeinsamer Zustand von UI und Dienst, gespeicherte Einstellungen |
| `audio/AudioEngine.kt` | Audio-Thread mit `AudioTrack` |
| `audio/MusicMixer.kt` | Überblendung zwischen Stücken, Limiter |
| `audio/Mood.kt` | Die Stimmungen und ihre Varianten (Stile) mit Tempobereichen |
| `audio/songs/Song.kt` | Taktgeber, Mischpult, Songaufbau, Melodie-Hooks |
| `audio/songs/SportSong.kt`, `RockSong.kt`, `DesertSong.kt` … `AmbientSong.kt` | Die Komponisten je Stimmung/Variante |
| `audio/music/Theory.kt` | Tonarten, Akkorde, Stimmführung |
| `audio/music/Patterns.kt` | Melodie- und Arpeggio-Generator |
| `audio/instruments/Sampler.kt` | Spielt echte Instrumentenaufnahmen (Anschlagstärken, Varianten, Loops, Bendings) |
| `audio/instruments/GuitarRig.kt` | Gitarrenverstärker-Simulation |
| `audio/instruments/` (übrige) | Synthesizer, elektronische Bassdrum, Riser, Texturen |
| `audio/AssetSampleLoader.kt` | Lädt die Instrumente aus den Assets (MediaCodec → Cache → mmap) |
| `audio/samples/SampleData.kt` | Format der Instrumente |
| `audio/dsp/Production.kt` | EQ, Kompressor, FDN-Hall, Bandsättigung |
| `assets/instruments/<id>/` | Die Instrumente: `samples.ogg` + `inst.txt` |
| `tools/build_instruments.py` | Baut die Instrumente aus den SFZ-Bibliotheken |
| `audio/dsp/Dsp.kt` | Filter, Hüllkurven, Hall, Echo, Oszillatoren |

## Lokal bauen

Voraussetzungen: JDK 17+ und das Android SDK (Platform 36).

```bash
./gradlew :app:assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

Für die Tests muss `ffmpeg` installiert sein (dekodiert die Instrumente).
`./gradlew :app:testDebugUnitTest` rendert außerdem jede Stimmung/Variante als WAV nach
`app/build/sound-previews/` – praktisch zum Probehören ohne Handy. Die Länge lässt sich
einstellen (`-PpreviewSeconds=180`). Die Tests prüfen auch Übersteuerung, Knackser beim
Wechsel, einen 15-Minuten-Dauerlauf je Stimmung, die Tonhöhe aller Instrumente über
ihren ganzen Umfang und die Loops. Zusätzlich entsteht `instrumente.wav`, in der jedes
Instrument einzeln zu hören ist.

### Instrumente neu bauen

Die fertigen Instrumente liegen im Repository; neu bauen muss man sie nur, wenn man etwas
an der Auswahl ändert. Dafür braucht es Python 3 mit numpy und ffmpeg:

```bash
python3 tools/build_instruments.py            # alle
python3 tools/build_instruments.py piano drums # einzelne
```

Das Skript lädt die benötigten Aufnahmen von GitHub (bzw. nutzt lokale Klone unter
`~/samplesrc/`), mischt die Schlagzeug-Mikrofone, kürzt, erzeugt Loops, misst die
Tonhöhe jedes Samples nach und schreibt `assets/instruments/<id>/`.

## Release (Installation über Obtainium)

1. Einmalig Repository-Secrets anlegen (*Settings → Secrets and variables → Actions*):
   - `KEYSTORE_BASE64` – Keystore, base64-kodiert (`base64 -w0 release.jks`)
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

   Alternativ funktionieren auch die Namen wie bei First Light (`SIGNING_KEYSTORE_BASE64`,
   `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`).

   Keystore erzeugen (falls noch keiner vorhanden):
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias fluxmusic -keyalg RSA -keysize 4096 -validity 36500
   ```
   Den Keystore gut aufbewahren – Updates funktionieren nur mit derselben Signatur.
2. Tag pushen:
   ```bash
   git tag v0.1.0 && git push origin v0.1.0
   ```
   Der Workflow baut die signierte APK und hängt sie an ein GitHub-Release.
3. In Obtainium: *App hinzufügen* → `https://github.com/haigerman/organic-music`.

Versionscode wird aus dem Tag berechnet (`v1.2.3` → 10203).
