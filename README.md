# milo-photo-uploader

Android app that runs in the background, scans newly taken photos, and checks for a specific cat using an Azure Cognitive Services Custom Vision model.
This scanning behavior is intended as an addition to the repository’s original Milo photo upload workflow, so detected photos can still be forwarded to downstream storage/social automation pipelines.


## What it does
- Schedules a periodic background scan using WorkManager
- Reads new images from MediaStore
- Sends each new image to Azure Custom Vision Prediction API
- Logs matches when the configured target tag exceeds the configured probability threshold

## Configuration
Set these properties in `gradle.properties` (or override through CI/local properties):

- `AZURE_CUSTOM_VISION_PREDICTION_URL` - Custom Vision prediction endpoint URL
- `AZURE_CUSTOM_VISION_PREDICTION_KEY` - prediction key
- `AZURE_TARGET_TAG` - tag name to match (defaults to `cat`)
- `AZURE_TARGET_PROBABILITY` - minimum probability threshold (defaults to `0.8`)

## Permissions
The app requires:
- `READ_MEDIA_IMAGES` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 12 and below)
- `INTERNET`
- `RECEIVE_BOOT_COMPLETED`
