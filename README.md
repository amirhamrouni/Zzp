# ZZP BTW Tracker

Native Android bookkeeping and Dutch VAT assistant for ZZP'ers.

## Core features
- Kotlin + Jetpack Compose
- Room local database
- Dutch BTW rates: 21%, 9%, 0% / exempt
- EU reverse charge support
- Belastingdienst quarterly mapping: 1a, 1b, 1e, 3b, 4a, 4b, 5a, 5b
- Receipt OCR with Google ML Kit (total, VAT, KvK, Dutch date)
- Quarterly PDF and CSV export
- No cloud account required; accounting data stays on-device

## Build
Open the repository in Android Studio (JDK 17). GitHub Actions also builds and tests every push and uploads the debug APK artifact.

Application ID: `com.zzp.btwtracker`

## Tax note
The app prepares an administrative overview. It does not submit VAT returns automatically. Always verify the generated figures against invoices/receipts and current Belastingdienst rules.

## License
Apache License 2.0. See `LICENSE`.
