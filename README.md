<div align="center">

<p align="center">
  <img src="resources/design/ic_launcher-playstore.png" width="128" height="128" alt="PillSOS Logo" />
</p>

  # 💊 PillSOS - Pill Reminder and SOS Manager
  **Your assistant for therapy management, and private SOS emergency protection between loved ones.**

  [![License](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE)
  [![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://android.com)
  [![Privacy](https://img.shields.io/badge/Privacy-E2E_Encrypted-success.svg?style=flat-square&logo=signal&logoColor=white)](#-security--privacy-first)

  <br />

  <!-- Download & Support Badges -->
  <a href="https://play.google.com/store/apps/details?id=swimminsparrow.ricordamelo">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="70" alt="Get it on Google Play" />
  </a>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <a href="https://f-droid.org/packages/swimminsparrow.ricordamelo/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="70" alt="Get it on F-Droid" />
  </a>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <a href="https://buymeacoffee.com/swimminsparrow" target="_blank">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" height="70" alt="Buy Me A Coffee" />
  </a>

  <br />
  <p align="center">
    <i>Available as <b>100% Free & Open Source Software (FOSS)</b> on F-Droid and on Google Play Store.</i>
  </p>

</div>

---

## 🌟 Overview

**PillSOS** (formerly known as *Avvertimi / Adviseme*) is an open-source, privacy-first Android app that combines medication reminders with a private family emergency network:

- ⏰ **Medication Reminders:** Never miss a dose with reliable full-screen alarms, custom schedules, and pill photos.
- 🚨 **SOS & Fall Detection:** Instantly alert paired family members during emergencies or if a fall is detected.
- 🔒 **100% Private:** Zero accounts, no tracking, and AES-256 end-to-end encrypted messaging.

---

## 📱 Screenshots

<div align="center">
  <img src="resources/stores/screens/en/mockup/Samsung%20Galaxy%20S21%20Ultra%20Screenshot%201_alarm%20full%20screen%20notification.png" width="23%" alt="Full Screen Alarm" />
  &nbsp;
  <img src="resources/stores/screens/en/mockup/Samsung%20Galaxy%20S21%20Ultra%20Screenshot%202%20sos%20fall%20detection%20screen.png" width="23%" alt="SOS Fall Detection" />
  &nbsp;
  <img src="resources/stores/screens/en/mockup/Samsung%20Galaxy%20S21%20Ultra%20Screenshot%203.png" width="23%" alt="Medication Management" />
  &nbsp;
  <img src="resources/stores/screens/en/mockup/Samsung%20Galaxy%20S21%20Ultra%20Screenshot%204.png" width="23%" alt="Instant PIN Pairing" />
</div>

<br />

<div align="center">
  <img src="resources/stores/screens/en/mockup/Samsung%20Galaxy%20S21%20Ultra%20Screenshot%205.png" width="23%" alt="Caregiver SOS History" />
  &nbsp;
  <img src="resources/stores/screens/en/mockup/Samsung%20Galaxy%20S21%20Ultra%20Screenshot%206.png" width="23%" alt="Therapy Details" />
  &nbsp;
  <img src="resources/stores/screens/en/mockup/Samsung%20Galaxy%20S21%20Ultra%20Screenshot%207.png" width="23%" alt="Settings and Protection" />
</div>

---

## ✨ Key Features

### ⏰ Medication & Therapy Reminders
- **Exact Alarms:** High-precision reminders that sound at the exact minute, waking your phone even in deep Doze mode or on lock screen.
- **Full-Screen Dismiss/Snooze:** Interactive lock-screen dialog with one-tap *Take*, *Snooze* (15 min default), and *Dismiss*.
- **Visual Customization:** Attach photos of pill shapes and blister packs; configure multiple daily times or custom schedules.
- **Custom Sounds & Vibration:** Dedicated ringtones and vibration patterns to ensure alarms are never overlooked.

### 🛡️ Private Family SOS Network
- **Instant 6-Digit PIN Pairing:** Connect directly with family members or caregivers in seconds without accounts, emails, or phone numbers.
- **Mutual Confirmation:** Pairing requires explicit confirmation on both devices to prevent unverified connections.
- **High-Priority SOS Alert:** Sound an emergency alarm that bypasses silent mode on your paired contacts' devices.
- **Delivery Confirmations & "I'm OK":** Real-time status updates when an alert is received, plus one-tap "I'm OK" check-ins.
- **Offline Queue & Retry:** Alerts triggered without network reception are queued locally and automatically retried when back online.

### 🚨 Automatic Fall Detection
- Continuous background sensor monitoring using device accelerometer.
- Analyzes rapid deceleration and free-fall impact patterns to automatically initiate the emergency procedure and alert paired contacts.

---

## 🔒 Security & Privacy First

| Feature | PillSOS | Typical Commercial Apps |
| :--- | :---: | :---: |
| **Mandatory Registration** | ❌ None (Zero Account) | ⚠️ Email / Phone / Password required |
| **User Tracking & Profiling** | ❌ Zero telemetry or analytics | ⚠️ Third-party tracking SDKs |
| **Communication Encryption** | 🛡️ **AES-256-CBC End-to-End** | ⚠️ Often plaintext or unverified |
| **Network Architecture** | 🌐 **Decentralized (ntfy.sh / SSE)** | ⚠️ Centralized proprietary servers |
| **FOSS Availability** | ✅ **Available on F-Droid** | ❌ Google Play only |

---

## 🛠️ Tech Stack

- **Core & Storage:** Java, Native SQLite, AndroidX Security Crypto (`MasterKeys`).
- **Real-Time Messaging:** Ultra-lightweight Server-Sent Events (SSE) over HTTP/JSON (via [ntfy](https://ntfy.sh)).
- **Background Engine:** Android `ForegroundService` paired with `WorkManager` for reliable offline retries.

---

## 💻 Building from Source

### Prerequisites
- **Android SDK** Minimum SDK: Android 8.0
- **JDK** 17+

### Build Flavors
- **`free` (F-Droid / FOSS):** 100% Free and Open Source. Completely free of Google Mobile Services, proprietary libraries, or ad trackers.
- **`nonfree` (Google Play):** Includes optional Google Mobile Ads (AdMob) to support independent development.

### Command-line Build

Clone the repository:
```bash
git clone https://github.com/swimminsparrow/pillsos-reminder
cd pillsos-reminder
```

To compile the **100% Free / F-Droid** build:
```bash
# Build Free Debug APK
./gradlew assembleFreeDebug
```

---

## ☕ Support the Project

> **PillSOS** is 100% free, open-source, and contains zero trackers or mandatory paid subscriptions. If this app brings peace of mind to you or helps protect your loved ones, buying a coffee is the best way to support its independent development and keep it thriving!

<br />

<div align="center">
  <a href="https://buymeacoffee.com/swimminsparrow" target="_blank">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="60" />
  </a>
  <br /><br />
  <b>Every coffee makes a difference. Thank you for your support! 💛</b>
</div>
<br />

---

## 🌍 Supported Languages

PillSOS is translated into **over 20 languages and regional dialects**, including:
- 🇮🇹 Italian
- 🇬🇧 English
- 🇪🇸 Spanish
- 🇫🇷 French
- 🇩🇪 German
- 🇵🇹 Portuguese
- 🇷🇺 Russian
- 🇺🇦 Ukrainian
- 🇯🇵 Japanese
- 🇰🇷 Korean
- 🇸🇦 Arabic
- 🇮🇳 Hindi
- 🇮🇩 Indonesian
- 🇹🇷 Turkish
- 🍕 *Regionals:* Neapolitan, Sicilian, Romanesco

---

## 📄 License

This project is licensed under the **GPLv3 Open Source License**.  
For complete terms and conditions, see the [LICENSE](LICENSE) file.

<br />

<div align="center">
  Crafted with ❤️ by <b>SwimminSparrow</b> for global health, safety, and peace of mind.
</div>
