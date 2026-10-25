# GSI downloads / Образы GSI

Архивы находятся во вложениях [v0.2.0-coloros-preview](https://github.com/Murk1ins-ka/gsionlocked/releases/tag/v0.2.0-coloros-preview).
Они не входят в APK или архив исходников. Наличие образа не заменяет подготовку
runtime для поддерживаемого телефона.

| Система | Файл после распаковки | Размер `.img` |
| --- | --- | --- |
| ColorOS 17 / Find X9 Ultra IN, SDK 37 | `OneOS_ColorOS17_FindX9Ultra_IN.img` | 12 069 945 344 байта |
| Evolution X 11.11 / Android 16, GAPPS, arm64-ab | `EvoX-11.11-GSI_treble_arm64-ab-GAPPS-20260920.img` | 3 863 474 176 байт |

SHA-256 распакованной ColorOS:

```text
a36ef550f4b2530fb9c7c6d363180d9eb4437e0497ebc87292697e4bf2a580b0
```

SHA-256 распакованной Evolution X:

```text
5e8333e15798592bae059bcb78ba85394418afc8f035a13fc0e97c086b8c1e49
```

## Скачать и распаковать

Для Evolution X скачай единственный `.img.xz`. Для ColorOS скачай **все** части
`OneOS_ColorOS17_FindX9Ultra_IN.img.zip.001`, `.002` и далее, если они есть.
Набор частей указан в приложенном `GSI-MANIFEST.json`. Отдельная часть не является
готовым образом. Не выбирай `.xz`, `.zip` или `.001` в приложении вместо `.img`.

Положи `restore-gsi.py`, `GSI-MANIFEST.json` и архив/все части в одну папку.
С установленным Python 3 выполни из этой папки:

```powershell
python restore-gsi.py coloros
python restore-gsi.py evolution
```

Выбери команду для нужной системы. Скрипт использует только стандартную библиотеку,
проверяет SHA-256 архивов и итогового `.img`, собирает части ColorOS и распаковывает
образ. Существующий `.img` не перезаписывается. Для ColorOS требуется свободное
место под полный ZIP и распакованные 12,07 ГБ, помимо скачанных частей.
Контрольные суммы вложений опубликованы в `GSI-SHA256SUMS.txt`.

Архивы также можно распаковать подходящим архиватором. После распаковки сравни
SHA-256 итогового `.img` с указанным выше. Пароль и биометрия ColorOS остаются
неподдерживаемыми; ограничения порта перечислены в [COLOROS.md](COLOROS.md).

The release contains separate image archives. ColorOS is a split ZIP; download
every part listed in GSI-MANIFEST.json before joining/extracting it. Evolution X
is a single XZ archive. The Python helper verifies both archive and raw-image
digests. These downloads do not bootstrap an unprepared device.
