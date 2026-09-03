# ZZP BTW Tracker

Native Android bookkeeping and Dutch VAT assistant for ZZP'ers.

## Features
- Kotlin + Jetpack Compose
- Room local database
- Dutch BTW rates: 21%, 9%, 0% / exempt
- EU reverse charge support
- Belastingdienst quarterly mapping: 1a, 1b, 1e, 3b, 4a, 4b, 5a, 5b
- Receipt OCR with Google ML Kit (total, VAT, KvK, Dutch date)
- Quarterly PDF and CSV export
- Editable camera/gallery OCR inbox and expense categories
- Customers, invoices and shareable invoice PDFs
- Hours, business mileage, KOR and VAT reserve monitoring
- Company profile, ZZP Coach and quarterly document archive
- No cloud account required; accounting data stays on-device

## Build
Open the repository in Android Studio (JDK 17), or run `./gradlew testDebugUnitTest assembleDebug bundleRelease`. CI uploads a debug APK and unsigned release APK/AAB. Google Play requires a private upload key; never commit it or its passwords.

Application ID: `com.zzp.btwtracker`

## Tax note
The app prepares an administrative overview. It does not submit VAT returns automatically. Always verify the generated figures against invoices/receipts and current Belastingdienst rules.

## Privacy

See `PRIVACY_POLICY.md`. OCR runs on-device and the app does not request internet permission. Bookkeeping data stays locally on the device.

## License
Apache License 2.0. See `LICENSE`.
