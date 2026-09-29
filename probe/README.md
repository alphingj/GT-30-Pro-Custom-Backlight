# ADB LED Probe

This is a reversible USB ADB test harness for the Infinix GT 30 Pro (`X6873`).

```sh
python3 probe/adb_led.py status
python3 probe/adb_led.py off
python3 probe/adb_led.py on
```

The probe uses the OEM global setting observed by the system LED controller.
This works on the production phone without root or Shizuku. Every operation
is recorded in `probe/adb-led.log`.

The `off` command is the documented OEM reset frame:

```text
00 01 00 00 00 00 00 00
```

The `frame` command is retained only for protocol research. Direct writes to
the raw command node are blocked by SELinux for the ADB shell user, so normal
desktop control should use the settings path instead.
