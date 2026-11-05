// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appName => 'RealScale-Pro';

  @override
  String get appTagline => 'Offline AI photo upscaler';

  @override
  String get guideSkip => 'Skip';

  @override
  String get guideNext => 'Next';

  @override
  String get guideGetStarted => 'Get started';

  @override
  String get guide1Title => 'Make small photos big';

  @override
  String get guide1Body =>
      'Turn old, small or compressed photos into sharp high-resolution images — up to 5×, right on your phone.';

  @override
  String get guide2Title => 'Private by design';

  @override
  String get guide2Body =>
      'Everything runs on this device. Your photos never leave it. No account, no cloud, no uploads.';

  @override
  String get guide3Title => 'One tap with Default';

  @override
  String get guide3Body =>
      'RealScale-Pro picks the best model and settings for your photo automatically. Just tap Upscale.';

  @override
  String get guide4Title => 'Choose your quality';

  @override
  String get guide4Body =>
      'Switch to Manual to pick a specific model, scale and face restoration when you want full control.';

  @override
  String get permissionPhotosTitle => 'Allow photo access?';

  @override
  String get permissionPhotosBody =>
      'RealScale-Pro uses your photo library to show recent photos and to save finished results into your gallery.';

  @override
  String get permissionPhotosAllow => 'Allow photo access';

  @override
  String get permissionPhotosNotNow => 'Not now';

  @override
  String get permissionPhotosDeniedCardTitle => 'Allow photo access';

  @override
  String get permissionPhotosDeniedCardBody =>
      'Without photo access you can still import with Files, Camera or the system picker.';

  @override
  String get permissionPhotosOpenSettings => 'Open settings';

  @override
  String get permissionNotificationsTitle => 'Stay notified during upscaling';

  @override
  String get permissionNotificationsBody =>
      'A notification shows progress when you keep upscaling running in the background.';

  @override
  String get permissionNotificationsAllow => 'Allow notifications';

  @override
  String get permissionNotificationsNotNow => 'Not now';

  @override
  String get permissionSelectedPhotos =>
      'Selected photos only — you can add more in system settings.';

  @override
  String get deviceCheckTitle => 'Checking your device';

  @override
  String get deviceCheckBody =>
      'We measure a small sample to pick the right quality for your phone. This takes a moment.';

  @override
  String get deviceCheckDone => 'Your device is ready for';

  @override
  String get deviceCheckTierLight => 'Light quality';

  @override
  String get deviceCheckTierStandard => 'Standard quality';

  @override
  String get deviceCheckTierPowerful => 'Powerful quality';

  @override
  String get setupTitle => 'Setup';

  @override
  String get setupStarterPack => 'Starter Pack';

  @override
  String get setupStarterSubtitle => 'The models needed for Default quality';

  @override
  String get setupIncluded => 'Included';

  @override
  String get setupDownload => 'Download';

  @override
  String get setupDownloading => 'Downloading…';

  @override
  String get setupPaused => 'Paused';

  @override
  String get setupInstalled => 'Installed';

  @override
  String setupOverallProgress(
    String percent,
    String mb,
    String speed,
    String eta,
  ) {
    return '$percent · $mb MB · $speed · ETA $eta';
  }

  @override
  String get setupMobileDataTitle => 'Use mobile data?';

  @override
  String get setupMobileDataBody =>
      'You are not on Wi-Fi. Downloads are set to Wi-Fi-only. Allow mobile data for this download?';

  @override
  String get setupAllowMobileData => 'Allow this time';

  @override
  String get setupKeepWifiOnly => 'Keep Wi-Fi only';

  @override
  String get setupContinue => 'Continue';

  @override
  String get setupResumeHint =>
      'Downloads resume automatically if the app closes.';

  @override
  String get homeGreeting => 'RealScale-Pro';

  @override
  String get homeSettingsTooltip => 'Settings';

  @override
  String get homeRecents => 'Recent photos';

  @override
  String get homeSeeAll => 'See all';

  @override
  String get homeRecentsEmptyTitle => 'No photos yet';

  @override
  String get homeRecentsEmptyBody => 'Import a photo to get started.';

  @override
  String get homeImportSection => 'Import';

  @override
  String get homeImportGallery => 'Gallery';

  @override
  String get homeImportFiles => 'Files';

  @override
  String get homeImportCamera => 'Camera';

  @override
  String get homeImportMore => 'More';

  @override
  String get homeOptionsSection => 'Options';

  @override
  String get homeModeUpscaleTitle => 'Upscale';

  @override
  String get homeModeUpscaleBody => 'Fast, smart, default quality';

  @override
  String get homeModeSrTitle => 'Super Resolution';

  @override
  String get homeModeSrBody => 'Maximum quality, no recompression';

  @override
  String get homeModeSrBadge => 'PRO';

  @override
  String get homeOptionMyUpscales => 'My Upscales';

  @override
  String get homeOptionModelManager => 'Model Manager';

  @override
  String get homeOptionSettings => 'Settings';

  @override
  String get homeOptionVideo => 'Video Upscale';

  @override
  String get homeVideoComingSoonTitle => 'Video upscaling is coming';

  @override
  String get homeVideoComingSoonBody =>
      'We are finishing video support. Photos work great already — give one a try!';

  @override
  String get homeSrWarningTitle => 'Before you start Super Resolution';

  @override
  String homeSrWarningBody(Object tier) {
    return 'Super Resolution keeps every original pixel and can take much longer. On $tier devices we recommend starting with Upscale.';
  }

  @override
  String get homeSrWarningChipFit => 'Fits your device';

  @override
  String get homeSrWarningChipHeavy => 'Heavy on this device';

  @override
  String get homeSrWarningUseUpscale => 'Use Upscale instead';

  @override
  String get homeSrWarningContinue => 'Continue anyway';

  @override
  String get homePermissionCard =>
      'Allow photo access to see your recent photos';

  @override
  String get galleryTitle => 'All photos';

  @override
  String get galleryEmpty => 'No photos found';

  @override
  String get galleryPermissionNeeded =>
      'Photo access needed to show your gallery';

  @override
  String get galleryAllow => 'Allow';

  @override
  String get viewerUpscale => 'Upscale';

  @override
  String get viewerDetails => 'Details';

  @override
  String get viewerShare => 'Share';

  @override
  String get viewerDelete => 'Delete';

  @override
  String get viewerModeChipUpscale => 'Upscale';

  @override
  String get viewerModeChipSr => 'Super Res';

  @override
  String get detailsResolution => 'Resolution';

  @override
  String get detailsSize => 'File size';

  @override
  String get detailsModified => 'Modified';

  @override
  String get moreSheetTitle => 'Import from';

  @override
  String get moreClipboard => 'Paste from clipboard';

  @override
  String get moreClipboardEmpty => 'No image found in clipboard';

  @override
  String get moreSystemPicker => 'System photo picker';

  @override
  String get moreCancel => 'Cancel';

  @override
  String get configTitle => 'Upscale';

  @override
  String get configPreviewSwap => 'Compare';

  @override
  String get configLevelDefault => 'Default';

  @override
  String get configLevelManual => 'Manual';

  @override
  String get configLevelAdvanced => 'Advanced';

  @override
  String get configAdvancedComingSoon =>
      'Advanced settings arrive in a later version.';

  @override
  String get configDetectedPhoto => 'Photo';

  @override
  String get configDetectedAnime => 'Anime';

  @override
  String get configDetectedPrefix => 'Detected:';

  @override
  String configScaleLabel(int scale) {
    return '$scale× · Auto';
  }

  @override
  String get configNotSure => 'Not sure — Change?';

  @override
  String get configChangeLink => 'Change';

  @override
  String get configManualComingSoon =>
      'Manual configuration is coming in this version.';

  @override
  String get configFaceToggle => 'Restore faces';

  @override
  String get configFaceHint => 'Finds faces and restores them after upscaling.';

  @override
  String configFaceDetectorHint(int count) {
    return '$count face(s) detected';
  }

  @override
  String get configFaceNone => 'No faces detected';

  @override
  String get configFaceUnavailable => 'Face restoration needs the Face Pack.';

  @override
  String configFaceDownload(Object size) {
    return 'Download Face Pack ($size)';
  }

  @override
  String configOutputSize(int width, int height) {
    return '$width × $height px';
  }

  @override
  String configEta(String duration) {
    return 'about $duration';
  }

  @override
  String get configUpscaleButton => 'Upscale';

  @override
  String get configHeavyTitle => 'Heavy model on this device';

  @override
  String configHeavyBody(String model) {
    return '$model is large for your device and may take longer or stall. Continue anyway?';
  }

  @override
  String get configHeavyContinue => 'Continue';

  @override
  String get configHeavyCancel => 'Cancel';

  @override
  String get configManualSection => 'Model';

  @override
  String get configScaleSection => 'Scale';

  @override
  String get configScaleExtraStep => '5× uses an extra resize step';

  @override
  String get configModelRecommended => 'Recommended';

  @override
  String get configModelDeviceFit => 'Fits device';

  @override
  String get configModelHeavy => 'Heavy';

  @override
  String configModelDownload(Object size) {
    return 'Download ($size)';
  }

  @override
  String configModelDownloading(int percent) {
    return 'Downloading… $percent';
  }

  @override
  String get configModelUnavailable => 'Unavailable';

  @override
  String get processingTitle => 'Upscaling';

  @override
  String get processingStagePreparing => 'Preparing image';

  @override
  String get processingStageUpscaling => 'Upscaling tiles';

  @override
  String get processingStageFaces => 'Restoring faces';

  @override
  String get processingStageSaving => 'Saving result';

  @override
  String processingTileCount(int index, int total) {
    return '$index / $total tiles';
  }

  @override
  String get processingEtaEstimating => 'Estimating…';

  @override
  String processingEta(String duration) {
    return '$duration left';
  }

  @override
  String get processingCancel => 'Cancel';

  @override
  String get processingCancelConfirmTitle => 'Cancel this upscale?';

  @override
  String get processingCancelConfirmBody =>
      'Progress will be discarded and temporary files removed.';

  @override
  String get processingCancelYes => 'Cancel upscale';

  @override
  String get processingCancelNo => 'Keep going';

  @override
  String get processingBackground => 'Continue in background';

  @override
  String get processingBackgroundNote =>
      'You can leave this screen — a notification shows progress.';

  @override
  String get processingTip1 =>
      'Tip: Default quality is one tap and fits every photo.';

  @override
  String get processingTip2 =>
      'Tip: Super Resolution keeps original quality but takes longer.';

  @override
  String get processingTip3 =>
      'Tip: Restore faces after upscaling group photos.';

  @override
  String get processingFailedTitle => 'The upscale didn\'t finish';

  @override
  String get processingFailedBody =>
      'This can happen on large photos or low memory.';

  @override
  String get processingTrySafer => 'Try safer settings';

  @override
  String get processingCancelled => 'Cancelled';

  @override
  String get processingSlowNote =>
      'Running slower than usual — battery saver or heat can slow the device.';

  @override
  String get resultTitle => 'Result';

  @override
  String get resultSavedTo => 'Saved to gallery';

  @override
  String resultInfoResolution(int width, int height) {
    return '$width × $height';
  }

  @override
  String resultInfoScale(int scale, String model) {
    return '$scale× $model';
  }

  @override
  String resultInfoTime(int seconds) {
    return '$seconds s';
  }

  @override
  String get resultBefore => 'Before';

  @override
  String get resultAfter => 'After';

  @override
  String get resultShare => 'Share';

  @override
  String get resultUpscaleAgain => 'Upscale again';

  @override
  String get resultNewImage => 'New image';

  @override
  String get resultMore => 'More';

  @override
  String get resultHoldToCompare => 'Hold to compare';

  @override
  String resultFacesRestored(int count) {
    return '$count face(s) restored';
  }

  @override
  String get resultRecoveredNote =>
      'Finished with adjusted settings for this device';

  @override
  String get resultTempInputNote =>
      'Large input processed via a quality-90 copy (original untouched)';

  @override
  String get myUpscalesTitle => 'My Upscales';

  @override
  String get myUpscalesEmptyTitle => 'Nothing upscaled yet';

  @override
  String get myUpscalesEmptyBody =>
      'Finished images appear here automatically.';

  @override
  String get myUpscalesDelete => 'Delete';

  @override
  String get myUpscalesShare => 'Share';

  @override
  String get myUpscalesOpen => 'Open';

  @override
  String get myUpscalesSelect => 'Select';

  @override
  String myUpscalesSelectedCount(int count) {
    return '$count selected';
  }

  @override
  String myUpscalesDeleteConfirm(int count) {
    return 'Delete $count image(s)? This also removes them from your gallery folder.';
  }

  @override
  String get myUpscalesDeleteConfirmTitle => 'Delete upscaled images?';

  @override
  String get modelManagerTitle => 'Model Manager';

  @override
  String modelManagerStorage(String used, String total) {
    return '$used of $total used';
  }

  @override
  String modelManagerFreeSpace(String free) {
    return '$free free';
  }

  @override
  String get modelManagerPackStarter => 'Starter Pack';

  @override
  String get modelManagerPackPro => 'Pro Pack';

  @override
  String get modelManagerPackFace => 'Face Pack';

  @override
  String get modelManagerPackUltra => 'Ultra Pack (coming soon)';

  @override
  String get modelManagerPackCommunity => 'Community Pack';

  @override
  String get modelManagerFilterAll => 'All';

  @override
  String get modelManagerFilterPhoto => 'Photo';

  @override
  String get modelManagerFilterAnime => 'Anime';

  @override
  String get modelManagerFilterFace => 'Face';

  @override
  String get modelManagerDownloadAll => 'Download pack';

  @override
  String get modelManagerDeleteAll => 'Delete pack';

  @override
  String get modelManagerUpdate => 'Update available';

  @override
  String get modelManagerStatusInstalled => 'Installed';

  @override
  String get modelManagerStatusNotInstalled => 'Not installed';

  @override
  String get modelManagerStatusDownloading => 'Downloading…';

  @override
  String get modelManagerStatusPaused => 'Paused';

  @override
  String get modelManagerStatusError => 'Error — tap to retry';

  @override
  String get modelManagerStatusUnavailable => 'Unavailable';

  @override
  String get modelManagerUnavailableChecksum => 'Source verification pending';

  @override
  String get modelManagerUnavailableLicense => 'License does not permit use';

  @override
  String get modelManagerUnavailableSoon => 'Coming in a later version';

  @override
  String get modelManagerLicense => 'License';

  @override
  String get modelManagerSource => 'Source';

  @override
  String get modelManagerDeleteFallback =>
      'Default model will be used again for this photo type.';

  @override
  String get modelManagerDeleteConfirmTitle => 'Delete this model?';

  @override
  String get modelManagerDeleteConfirmBody =>
      'You can download it again any time.';

  @override
  String modelManagerSpeed(String speed) {
    return '$speed/s';
  }

  @override
  String modelManagerSize(String size) {
    return '$size';
  }

  @override
  String get settingsTitle => 'Settings';

  @override
  String get settingsAppearance => 'Appearance';

  @override
  String get settingsTheme => 'Theme';

  @override
  String get settingsThemeSystem => 'System';

  @override
  String get settingsThemeLight => 'Light';

  @override
  String get settingsThemeDark => 'Dark';

  @override
  String get settingsDynamicColor => 'Dynamic color';

  @override
  String get settingsDynamicColorBody =>
      'Match app colors to your wallpaper (Material You).';

  @override
  String get settingsFeel => 'Feel';

  @override
  String get settingsHaptics => 'Haptics';

  @override
  String get settingsKeepAwake => 'Keep screen awake';

  @override
  String get settingsKeepAwakeBody => 'Prevents sleep while upscaling.';

  @override
  String get settingsLanguage => 'Language';

  @override
  String get settingsLanguageEnglish => 'English';

  @override
  String get settingsLanguageMore => 'More languages coming';

  @override
  String get settingsDefaults => 'Upscaling defaults';

  @override
  String get settingsDefaultMode => 'Default mode';

  @override
  String get settingsCompute => 'Compute preference';

  @override
  String get settingsComputeAuto => 'Auto';

  @override
  String get settingsComputeGpu => 'GPU';

  @override
  String get settingsComputeCpu => 'CPU';

  @override
  String get settingsThrottle => 'Slow down on battery or heat';

  @override
  String get settingsOutput => 'Output';

  @override
  String get settingsOutputFormat => 'Format';

  @override
  String get settingsOutputFormatSame => 'Same as source';

  @override
  String get settingsOutputMetadata => 'Keep photo metadata';

  @override
  String get settingsOutputNaming => 'File names';

  @override
  String get settingsOutputNamingRealscale => 'RealScale_<name>';

  @override
  String get settingsOutputNamingOriginal => 'Keep original name';

  @override
  String get settingsDownloads => 'Downloads';

  @override
  String get settingsWifiOnly => 'Download only on Wi-Fi';

  @override
  String get settingsStorage => 'Storage';

  @override
  String get settingsClearCache => 'Clear temporary files';

  @override
  String get settingsClearCacheBody => 'Installed models are kept.';

  @override
  String get settingsClearCacheDone => 'Temporary files cleared';

  @override
  String get settingsRerunDeviceCheck => 'Run device check again';

  @override
  String get settingsDeviceTier => 'Device profile';

  @override
  String get settingsPrivacy => 'Privacy';

  @override
  String get settingsPrivacyBody =>
      'RealScale-Pro works fully offline. Photos never leave this device.';

  @override
  String get settingsAnonymousStats => 'Share anonymous usage stats';

  @override
  String get settingsCrashReports => 'Send crash reports';

  @override
  String get settingsSupport => 'Support';

  @override
  String get settingsReplayGuide => 'Replay the introduction';

  @override
  String get settingsFaq => 'FAQ & feedback';

  @override
  String get settingsAbout => 'About RealScale-Pro';

  @override
  String get settingsLicenses => 'Open-source licenses';

  @override
  String get settingsPrivacyStatement => 'Privacy statement';

  @override
  String get settingsModelCredits => 'Model credits';

  @override
  String get settingsCreditsBody =>
      'Real-ESRGAN (BSD-3), Real-CUGAN (MIT) and ncnn (BSD-3) power the upscaling engine. Face restoration uses GFPGAN (Apache-2.0).';

  @override
  String get aboutVersion => 'Version';

  @override
  String get aboutBuiltWith => 'Built with Flutter · ncnn engine';

  @override
  String get privacyTitle => 'Privacy statement';

  @override
  String get privacyBody1 =>
      'RealScale-Pro processes every photo entirely on this device. No photo, no result and no personal data is ever uploaded.';

  @override
  String get privacyBody2 =>
      'The app uses the network for exactly one thing: downloading AI models when you ask for them. Model downloads check the source and file checksum before installing.';

  @override
  String get privacyBody3 =>
      'No account is created, no analytics profile is built, and nothing is shared. Optional anonymous statistics and crash reports are off by default and only send technical data — never images.';

  @override
  String get faqTitle => 'FAQ & feedback';

  @override
  String get faqQ1 => 'Why does the first upscale take longer?';

  @override
  String get faqA1 =>
      'The model loads once and is kept ready afterwards. Later upscales are much faster.';

  @override
  String get faqQ2 => 'Where are results saved?';

  @override
  String get faqA2 =>
      'In your gallery under RealScale-Pro, and in My Upscales inside the app.';

  @override
  String get faqQ3 => 'Are my photos uploaded anywhere?';

  @override
  String get faqA3 => 'Never. Upscaling happens fully offline on this device.';

  @override
  String get faqFeedbackSubject => 'RealScale-Pro feedback';

  @override
  String get faqFeedbackBody =>
      'Tell us what works, what doesn\'t, or what you\'d like next.';

  @override
  String get licensesTitle => 'Open-source licenses';

  @override
  String get errorGenericTitle => 'Something went wrong';

  @override
  String get errorGenericBody => 'Please try again.';

  @override
  String get errorRetry => 'Retry';

  @override
  String get errorNoPhotos => 'No photos available';

  @override
  String get errorImageTooLarge => 'This image is too large for this device';

  @override
  String get errorOutOfMemory => 'Not enough memory — try safer settings';

  @override
  String get errorDiskFull => 'Not enough free storage';

  @override
  String get errorSaveFailed => 'Could not save the result';

  @override
  String get errorNetwork => 'Network unavailable — check your connection';

  @override
  String get errorChecksum => 'Download failed verification and will retry';

  @override
  String get loading => 'Loading…';

  @override
  String durationSeconds(int n) {
    return '$n s';
  }

  @override
  String durationMinutes(int n) {
    return '$n min';
  }

  @override
  String durationMinutesSeconds(int m, int s) {
    return '$m min $s s';
  }

  @override
  String sizeMb(String n) {
    return '$n MB';
  }

  @override
  String sizeKb(String n) {
    return '$n KB';
  }

  @override
  String sizeGb(String n) {
    return '$n GB';
  }

  @override
  String get cancel => 'Cancel';

  @override
  String get ok => 'OK';

  @override
  String get close => 'Close';

  @override
  String get delete => 'Delete';

  @override
  String get share => 'Share';

  @override
  String get retry => 'Retry';

  @override
  String get comingSoon => 'Coming soon';

  @override
  String get proBadge => 'PRO';

  @override
  String get configAdvancedSection => 'Advanced pipeline';

  @override
  String get configAdvancedTile => 'Tile size';

  @override
  String get configAdvancedTileHint =>
      'Smaller tiles use less memory. Auto picks by device.';

  @override
  String get configAdvancedOverlap => 'Tile overlap';

  @override
  String get configAdvancedOverlapHint =>
      'Higher overlap hides seams better but costs time.';

  @override
  String get configAdvancedThreads => 'CPU threads';

  @override
  String get configAdvancedCompute => 'Compute';

  @override
  String get configAdvancedComputeGpu => 'GPU (Vulkan)';

  @override
  String get configAdvancedComputeCpu => 'CPU';

  @override
  String get configAdvancedFormat => 'Output format';

  @override
  String get configAdvancedQuality => 'JPEG quality';

  @override
  String get configAdvancedCap => 'Output size limit';

  @override
  String get configAdvancedCapHint =>
      'Caps output megapixels to protect memory.';

  @override
  String get configAuto => 'Auto';

  @override
  String get configMpUnit => 'MP';

  @override
  String get configFormatSameAsSource => 'Same as source';

  @override
  String get configSrBadge => 'Super Resolution';

  @override
  String get configSrLosslessInfo =>
      'No recompression: your original pixels are kept, the result is saved as lossless PNG.';

  @override
  String get configSrFeasibilityTitle => 'Device check';

  @override
  String get configSrFeasibilityGreen => 'Fits this device comfortably';

  @override
  String get configSrFeasibilityAmber => 'Heavy but possible';

  @override
  String get configSrFeasibilityRed => 'Too heavy for this device';

  @override
  String configSrFeasibilityMemory(int mb, int total) {
    return 'Estimated peak memory $mb of $total MB';
  }

  @override
  String configSrFeasibilityOutput(int mp) {
    return 'Output $mp MP';
  }

  @override
  String configSrFeasibilityOverCap(int cap) {
    return 'Output exceeds the $cap MP limit for this device';
  }

  @override
  String get configSrFeasibilitySlow => 'May take a long time on this device';

  @override
  String get configSrFeasibilityLargeSource => 'Very large source image';

  @override
  String get configSrConfirmAmberTitle => 'Continue with Super Resolution?';

  @override
  String get configSrConfirmAmberBody =>
      'This photo is heavy for your device. It can take a while and may slow other apps down.';

  @override
  String get configSrBlockedTitle => 'Super Resolution is too heavy here';

  @override
  String get configSrBlockedBody =>
      'Try a smaller scale, or preview a crop first.';

  @override
  String get configSrBlockedUseUpscale => 'Use Upscale mode instead';

  @override
  String get configPreviewSection => 'Preview first';

  @override
  String get configPreviewHint =>
      'Pick a small area and upscale only that — see the quality before the long run.';

  @override
  String get configPreviewPick => 'Choose area';

  @override
  String get configPreviewChange => 'Change area';

  @override
  String get configPreviewClear => 'Clear';

  @override
  String get configPreviewRun => 'Preview';

  @override
  String get configPreviewTitle => 'Preview result';

  @override
  String get configPreviewRunning => 'Upscaling preview…';

  @override
  String get configPreviewBefore => 'Original';

  @override
  String get configPreviewAfter => 'Super Resolution';

  @override
  String get configPreviewFull => 'Upscale full image';

  @override
  String configPreviewTime(int n) {
    return '$n s';
  }

  @override
  String get cropTitle => 'Choose preview area';

  @override
  String get cropHint =>
      'Drag the box to pick what RealScale-Pro should preview.';

  @override
  String get cropApply => 'Use this area';

  @override
  String get cropReset => 'Reset';

  @override
  String get settingsLanguageSystem => 'System default';

  @override
  String get settingsLanguageHindi => 'हिन्दी';

  @override
  String get settingsLanguagePick => 'Choose language';
}
