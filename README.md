<img src="docs/icon.png" width="128" alt="App-Icon: Keimling, der aus einer Klangwelle wächst">

# Organic Music

Android-App, die **live Musik komponiert** – passend zu Stimmung und Zweck. Nichts ist
aufgenommen: Tonart, Akkordfolgen, Rhythmen, Melodien und der Aufbau der Stücke entstehen
in Echtzeit auf dem Handy. Die Musik läuft endlos und wiederholt sich nie exakt.

| Stimmung | Klang | Tempo |
|---|---|---|
| 🔥 **Sport · Bass & Groove** | Treibende elektronische Beats: gerade Bassdrum, pumpende Flächen, rollender Bass, Arpeggios; Spannungsbögen mit Snare-Wirbel, Rauschen und „Drop“ | 110–175 BPM |
| 🤘 **Sport · Rock** | Geradliniger Hardrock im Stil der großen Pub-Rock-Bands: zwei knackig verzerrte Gitarren (links/rechts) mit Powerchords und Pausen zwischen den Akkorden, Achtel-Bass, Rock-Beat mit Fills, Blues-Solo mit Bendings; Strophe → Refrain → Solo | 100–170 BPM |
| 🎧 **Fokus** | Lo-Fi-Hip-Hop zum Arbeiten & Lernen: gedämpfte Drums mit Swing, warmes E-Piano mit Jazz-Akkorden, weicher Bass, Vinyl-Knistern; bewusst gleichmäßig | 65–100 BPM |
| 🎨 **Kreativ** | Leichter, positiver Deep-House-Groove: Piano-Stabs, Conga & Shaker, funky Bass, Mallet-Arpeggios, sonnige Melodie | 96–124 BPM |
| 🌿 **Entspannen** | Ambient ohne Schlagzeug: atmende Klangflächen, frei improvisierendes gedämpftes Klavier, Glocken, leises Meeresrauschen | 50–80 BPM |
| 🌙 **Einschlafen** | Noch langsamer, tiefer und dunkler; tragender Grundton, Meeresrauschen, kaum Melodie | 40–60 BPM |

## Funktionen

- **Varianten** – Sport gibt es als „Bass & Groove“ oder „Rock“
- **Tempo** je Stimmung/Variante frei einstellbar (beim Laufen z. B. passend zur Schrittfrequenz)
- **Intensität** von sanft bis kraftvoll – blendet Instrumente weich ein und aus
  (z. B. Fokus: von „nur Klavier“ bis „mit Beat & Melodie“)
- **Neues Stück** 🎲 – blendet in ein frisch komponiertes Stück über
- Wechsel der Stimmung während der Wiedergabe mit weicher Überblendung
- Stücke entwickeln sich selbst weiter: Intro → Groove → Thema/Steigerung → Höhepunkt →
  Atempause …; danach beginnt ein neuer Teil mit neuen Mustern und oft neuer Tonart
- Anzeige, was gerade erklingt (Tonart · Akkord · Teil) und ein Play-Knopf, der im Takt pulsiert
- **Schlaf-Timer** (blendet in den letzten 30 s sanft aus)
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
- **Klang:** alle Instrumente sind Synthesizer – Bassdrum, Snare, Clap, Hi-Hats, Shaker,
  Toms und Conga aus Oszillatoren und Rauschen; Pads, Bässe und Leads subtraktiv; E-Piano,
  Klavier, Vibraphon und Glocken per FM-Synthese; E-Gitarren als physikalisch modellierte
  Saiten (Karplus-Strong) mit Röhren-Verzerrer und Lautsprecherbox. Dazu Hall,
  Ping-Pong-Echo und Sidechain-„Pumpen“.

Die APK bleibt dadurch klein (ca. 1 MB).

## Aufbau

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Startet die Oberfläche |
| `ui/MusicScreen.kt` | Hauptbildschirm: Stimmungen, Tempo, Intensität, Timer, Play |
| `PlaybackService.kt` | Wiedergabe im Hintergrund, Benachrichtigung, Timer, Audiofokus |
| `data/MusicRepository.kt` | Gemeinsamer Zustand von UI und Dienst, gespeicherte Einstellungen |
| `audio/AudioEngine.kt` | Audio-Thread mit `AudioTrack` |
| `audio/MusicMixer.kt` | Überblendung zwischen Stücken, Limiter |
| `audio/Mood.kt` | Die Stimmungen und ihre Varianten (Stile) mit Tempobereichen |
| `audio/songs/Song.kt` | Taktgeber, Mischpult, Songaufbau, Melodie-Hooks |
| `audio/songs/SportSong.kt`, `RockSong.kt` … `AmbientSong.kt` | Die Komponisten je Stimmung/Variante |
| `audio/music/Theory.kt` | Tonarten, Akkorde, Stimmführung |
| `audio/music/Patterns.kt` | Melodie- und Arpeggio-Generator |
| `audio/instruments/` | Schlagzeug, Synthesizer, E-Gitarre, Texturen |
| `audio/dsp/Dsp.kt` | Filter, Hüllkurven, Hall, Echo, Oszillatoren |

## Lokal bauen

Voraussetzungen: JDK 17+ und das Android SDK (Platform 36).

```bash
./gradlew :app:assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

`./gradlew :app:testDebugUnitTest` rendert außerdem jede Stimmung/Variante als WAV nach
`app/build/sound-previews/` – praktisch zum Probehören ohne Handy. Die Länge lässt sich
einstellen (`-PpreviewSeconds=180`). Die Tests prüfen auch Übersteuerung, Knackser beim
Wechsel und einen 15-Minuten-Dauerlauf je Stimmung.

## Release (Installation über Obtainium)

1. Einmalig Repository-Secrets anlegen (*Settings → Secrets and variables → Actions*):
   - `KEYSTORE_BASE64` – Keystore, base64-kodiert (`base64 -w0 release.jks`)
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

   Keystore erzeugen (falls noch keiner vorhanden):
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias organicmusic -keyalg RSA -keysize 4096 -validity 36500
   ```
   Den Keystore gut aufbewahren – Updates funktionieren nur mit derselben Signatur.
2. Tag pushen:
   ```bash
   git tag v0.1.0 && git push origin v0.1.0
   ```
   Der Workflow baut die signierte APK und hängt sie an ein GitHub-Release.
3. In Obtainium: *App hinzufügen* → `https://github.com/haigerman/organic-music`.

Versionscode wird aus dem Tag berechnet (`v1.2.3` → 10203).
