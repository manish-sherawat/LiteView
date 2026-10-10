# LibreOffice Runtime Assets Directory

LibreOffice requires bootstrap configuration databases to start up headless without crashing:

### Key Assets:
- `program/` (URE / uno bootstrap config)
- `share/` (registry data, fonts, color palettes)
- `services.rdb` & `types.rdb`

When placed here, `LOKitManager.kt` automatically unpacks them into the app's private files directory (`context.filesDir/libreoffice`) during the first initialization pass.
