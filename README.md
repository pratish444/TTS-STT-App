#  TTS-STT Voice Assistant
  **(WORK IN PROGRESS)**

A modern **Voice Assistant Android app** built with **Kotlin** and **Jetpack Compose**, supporting both **Speech-to-Text (STT)** and **Text-to-Speech (TTS)** functionality.

This app lets users speak into the microphone, transcribes their speech, and generates a smart assistant reply — which can then be read aloud using TTS.



## ✨ Features

-  **Speech-to-Text (STT)** — Converts spoken words into text in real time  
-  **Text-to-Speech (TTS)** — Reads out the user’s text or assistant’s reply  
-  **Chat-style UI** — Messages displayed in clean bubble format  
-  **Modern Material 3 Design** — Built fully with Jetpack Compose  
-  **Live Partial Results** — Shows intermediate recognition before final result  
-  **Permission Handling** — Prompts for microphone access  
-  **Smart Reply Generation** — Fun, context-aware assistant responses

---

## 🛠 Tech Stack

- **Kotlin**
- **Jetpack Compose** (Material 3)
- **Android SpeechRecognizer API** for STT
- **Android TextToSpeech API** for TTS
- **ViewModel** for state management

## 📋 Permissions

This app requires:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```
## Clone the repository

     git clone https://github.com/your-username/TTS-STT-Voice-Assistant.git

##  Usage

  - Tap the Mic Button 🎤 — Start speaking

   - Watch Text Appear ✍ — Speech is transcribed live

  - Get a Smart Reply 💬 — Assistant sends a response

 - Tap "Read Reply" 🔊 — Assistant reads the message aloud

<h3>Working Screen</h3>
<img src="1000089202.jpg" alt="Home" width="250"/>
     


# Connecting Android Studio to a Real Android Device

This guide explains how to set up your Android phone so you can run and debug apps directly from Android Studio.

---

## 1. Enable Developer Options on Your Phone
1. Open **Settings** → **About phone**.
2. Scroll down to **Build number**.
3. Tap **Build number** 7 times until you see **"You are now a developer!"**.
4. Go back to **Settings** → **System** (or **Additional settings** on some devices) → **Developer options**.

---

## 2. Enable USB Debugging
1. In **Developer options**, find and enable **USB debugging**.
2. (Optional) If you want wireless debugging, also enable **Wireless debugging**.

---

## 3. Install Device Drivers (Windows only)
- On **Windows**, you may need to install USB drivers for your device.
- Download them from your phone manufacturer’s website:
  - [Samsung USB Drivers](https://developer.samsung.com/mobile/android-usb-driver.html)
  - [Google (Pixel) Drivers](https://developer.android.com/studio/run/win-usb)
  - [Xiaomi Drivers](https://c.mi.com/global/miuidownload/index)
  - [Other OEM Drivers](https://developer.android.com/studio/run/oem-usb)

*(On macOS and Linux, drivers are usually not required.)*

---

## 4. Connect Your Phone via USB
1. Use a **high-quality USB cable**.
2. Plug your phone into the computer.
3. On your phone, you may see a popup:  
   - Tap **Allow USB debugging** and check **Always allow from this computer**.

---

## 5. Verify Connection
In your terminal, run:
```bash
adb devices
```
## 6. Run Your App from Android Studio

   Open Android Studio.

  Select your connected device from the device dropdown in the toolbar.

  Click Run ▶ or press Shift + F10.
