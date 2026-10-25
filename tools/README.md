# Hardware Oscilloscope & Telemetry Bridge Guide

Tool ini memungkinkan terminal Linux ini melakukan verifikasi sinyal osiloskop fisik terhadap DAC USB yang terhubung ke ponsel Android.

## Cara Kerja
1. **Host-Side Instrumentation Bridge (`tools/hardware_probe.py`)**:
   - Mendeteksi osiloskop digital yang terhubung melalui USB-TMC (`/dev/usbtmc*`) atau logic analyzer melalui `sigrok-cli` (Saleae, PicoScope, Rigol, Siglent).
   - Menangkap kurva tegangan transien analog output DAC (3.5mm/4.4mm jack atau pin test I2S/DSD) secara langsung.
   - Merender bentuk gelombang ASCII langsung di terminal CLI.
   - Menyediakan mode simulasi `--dry-run` presisi tinggi saat osiloskop belum tercolok ke laptop.

2. **C++ Native Driver Microsecond Timers**:
   - `lastInterfaceSettleUs`: 15,000 µs (15 ms) DMA endpoint settling.
   - `lastPllRelockUs`: 50,000 µs (50 ms) UAC2 PLL crystal stabilization.
   - `lastAntiPopPaddingUs`: 180,000 µs (180 ms) cold pre-roll / 50,000 µs (50 ms) warm resume.
   - `maxIsoJitterNs`: pelacakan jitter sub-mikrodetik antar paket isochronous.

## Penggunaan di Terminal
```bash
# Pindai apakah ada osiloskop USB/LAN yang terpasang di laptop:
python3 tools/hardware_probe.py --scan

# Jalankan pengukuran transien (otomatis dry-run jika osiloskop fisik belum terpasang):
python3 tools/hardware_probe.py --measure-transient

# Baca telemetri fuel-gauge baterai real-time dari ponsel Android via ADB:
python3 tools/hardware_probe.py --poll-battery

# Simulasikan penurunan tegangan rel tembaga VBUS 5.0V pada beban 500mA:
python3 tools/hardware_probe.py --simulate-droop 500 --cable-resistance 0.30
```
