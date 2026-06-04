# 📶 SignalMonitor

**Monitor de señal en tiempo real para Android** — mide la intensidad de la señal
**móvil** (dBm de la portadora, con detalle LTE/5G y selección de SIM) o de **WiFi**
(RSSI + SSID), la grafica en una línea de tiempo coloreada por calidad y permite
**exportar las lecturas a CSV**.

<p align="center">
  <img src="docs/screenshots/main.png" width="30%" alt="Pantalla principal">
  <img src="docs/screenshots/mobile.png" width="30%" alt="Modo móvil">
  <img src="docs/screenshots/wifi.png" width="30%" alt="Modo WiFi">
</p>

> ℹ️ Las capturas van en `docs/screenshots/`. Reemplazá los nombres de arriba por
> los tuyos cuando las agregues.

---

## ✨ Qué hace

- **Dos modos con un toque** — botón **Móvil / WiFi**:
  - **Móvil:** dBm de la celda servidora + **nivel 0–4** (sin señal → excelente),
    tipo de red (**2G/3G/4G LTE/5G**) y detalle fino de radio:
    **RSRP · RSRQ · SINR** para LTE y **5G NR**.
  - **WiFi:** **RSSI** en dBm, **nivel 0–4** y **SSID** de la red conectada.
- **Gráfico en tiempo real** — línea que se desplaza, coloreada según la calidad de
  cada muestra.
- **Multi-SIM** — si hay más de una SIM activa, un selector permite elegir cuál
  monitorear (cada una con su propia suscripción de telefonía).
- **Intervalo de muestreo configurable** — 0,5 s / 1 s / 2 s / 5 s.
- **Exportar a CSV** — guarda hasta 10.000 lecturas
  (`timestamp_ms, fecha_hora, tipo, valor_dbm, nivel, detalle`) y las comparte con
  cualquier app vía `FileProvider`.

---

## 📦 Cómo instalarlo en Android

La app **no está en Google Play** (se distribuye como APK). Es un *debug build* firmado
con la clave de depuración; perfecto para uso personal.

### Opción A — Instalar la APK ya compilada (la más simple)
1. Descargá el APK: **[`dist/signalmonitor-1.1.apk`](dist/signalmonitor-1.1.apk)**
   (desde el teléfono, abrí el repo en el navegador → entrá al archivo → **Download**).
2. Pasalo al teléfono si lo bajaste en la PC (cable USB, Google Drive, Telegram, etc.).
3. Abrilo con el explorador de archivos → Android pedirá habilitar
   **"Instalar apps desconocidas"** para esa app: permitilo.
4. Tocá **Instalar**.

### Opción B — Compilar desde el código
Requiere **JDK 17** y el **Android SDK (API 34)**.
```bash
git clone git@github.com:lpapa1977/SignalMonitor.git
cd SignalMonitor
./gradlew assembleDebug
# APK resultante:
#   app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Permisos que pide y por qué
- **`READ_PHONE_STATE`** y **`ACCESS_FINE_LOCATION`** — Android exige ubicación para
  exponer los detalles de la señal móvil (RSRP/RSRQ/SINR, identidad de celda).
- **`ACCESS_WIFI_STATE`** / **`ACCESS_NETWORK_STATE`** — leer RSSI y estado de la red WiFi.

No usa internet ni envía datos a ningún servidor: todo corre **en el dispositivo** y el
CSV solo se comparte si vos tocás **Exportar CSV**.

---

## 🧰 Detalles técnicos
- **Lenguaje:** Kotlin · **UI:** XML + View Binding · vista de gráfico propia (`GraphView`).
- **minSdk 29** · **targetSdk 34** · `applicationId = com.lpapa.signalmonitor` · versión 1.1.
- **API de señal:** `TelephonyCallback.SignalStrengthsListener` en Android 12+
  (`PhoneStateListener` como *fallback* en versiones anteriores).
- **Dependencias:** `androidx.core`, `androidx.appcompat` y `com.google.android.material`.

## 🗂️ Estructura
```
app/src/main/java/com/lpapa/signalmonitor/
  MainActivity.kt   # modos móvil/WiFi, multi-SIM, muestreo, exportación a CSV
  GraphView.kt      # vista de gráfico en tiempo real, coloreada por nivel
app/src/main/res/   # layout, strings, drawables, file_paths del FileProvider
```

## 📄 Licencia
Proyecto personal. Usalo bajo tu responsabilidad.
