# Jarvis AI – APK bauen NUR mit dem Handy (ohne PC)

Android Studio läuft nicht auf dem Handy. Der Trick: Der Code wird zu
GitHub geschickt, und GitHub baut die APK kostenlos in der Cloud für dich.
Du lädst am Ende nur die fertige APK herunter.

## Was du brauchst
- Einen kostenlosen GitHub-Account (github.com, per Browser oder App anlegen)
- Die App "Termux" (empfohlen: aus F-Droid, NICHT aus dem Play Store,
  da die Play-Store-Version veraltet ist): https://f-droid.org/packages/com.termux/

## Schritt 1: GitHub-Repository anlegen
1. Auf github.com einloggen (im Handy-Browser)
2. Oben rechts "+" → "New repository"
3. Name z.B. "JarvisAI", auf "Public" oder "Private" stellen, NICHT mit
   README initialisieren
4. "Create repository" klicken
5. Merke dir die URL, z.B. https://github.com/DEINNAME/JarvisAI

## Schritt 2: Persönliches Zugriffstoken erstellen (statt Passwort)
1. GitHub → Profilbild → Settings → ganz unten "Developer settings"
2. "Personal access tokens" → "Tokens (classic)" → "Generate new token"
3. Haken bei "repo" setzen, Token erzeugen
4. Token kopieren und sicher speichern (wird nur einmal angezeigt!)

## Schritt 3: Termux einrichten
Öffne Termux und tippe (jede Zeile einzeln, Enter drücken):

```
pkg update -y
pkg install git unzip -y
termux-setup-storage
```

Beim letzten Befehl erscheint eine Berechtigungsanfrage – erlauben.

## Schritt 4: Das Projekt zu Termux holen
Die ZIP-Datei, die Claude dir gegeben hat, liegt vermutlich in deinem
"Downloads"-Ordner. In Termux:

```
cp /sdcard/Download/JarvisAI.zip ~/
cd ~
unzip JarvisAI.zip
cd JarvisAI
```

## Schritt 5: Zu GitHub hochladen
```
git init
git add .
git commit -m "Erste Version"
git branch -M main
git remote add origin https://github.com/DEINNAME/JarvisAI.git
git push -u origin main
```

Bei "Username" deinen GitHub-Namen eingeben, bei "Password" das Token
aus Schritt 2 einfügen (nicht dein normales Passwort!).

## Schritt 6: Build abwarten
1. Öffne im Browser dein Repository auf github.com
2. Tab "Actions" öffnen
3. Dort läuft automatisch der Build (dauert ca. 2-4 Minuten)
4. Wenn ein grüner Haken erscheint: auf den Lauf klicken
5. Ganz unten bei "Artifacts" auf "JarvisAI-APK" tippen → lädt als ZIP
   herunter
6. ZIP entpacken (z.B. mit einer Datei-Manager-App) → app-debug.apk
7. Auf die APK tippen → Installation erlauben ("unbekannte Quellen")
   → installieren

## Nutzung
1. App öffnen, Gemini API-Key eintragen (kostenlos: https://aistudio.google.com/apikey)
2. "🎤 Sprich mit Jarvis" für einzelne Befehle
3. "👂 Dauerzuhören starten" für den Hintergrundmodus – dann reicht:
   "Jarvis, öffne YouTube" oder "Jarvis, ruf 0123456789 an" oder
   einfach eine Frage nach dem Wort "Jarvis"

## Wichtige Hinweise
- Anrufe funktionieren aktuell nur mit gesprochenen Ziffern, nicht mit
  Kontaktnamen (das würde zusätzlichen Kontaktzugriff brauchen – sag
  Bescheid, falls gewünscht)
- Android kann Hintergrund-Apps aus Akkuspargründen abwürgen. Falls
  Jarvis im Hintergrund aufhört zu hören: Einstellungen → Apps →
  Jarvis AI → Akku → "Nicht optimieren" / "Uneingeschränkt" wählen
- Diese App ist nur für dein eigenes Gerät gedacht (Sideload, nicht im
  Play Store) – deshalb der Umweg über GitHub Actions
