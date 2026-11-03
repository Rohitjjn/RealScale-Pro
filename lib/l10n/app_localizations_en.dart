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
  String get skip => 'Skip';

  @override
  String get next => 'Next';

  @override
  String get getStarted => 'Get started';

  @override
  String get continueLabel => 'Continue';

  @override
  String get close => 'Close';

  @override
  String get retry => 'Retry';

  @override
  String get notNow => 'Not now';

  @override
  String get allow => 'Allow';

  @override
  String get cancel => 'Cancel';

  @override
  String get back => 'Back';

  @override
  String get guideTitle1 => 'Make any photo sharper';

  @override
  String get guideBody1 =>
      'Turn small or soft photos into clear, share-ready images.';

  @override
  String get guideTitle2 => 'Works on every device';

  @override
  String get guideBody2 =>
      'RealScale-Pro adapts its work to your phone or computer.';

  @override
  String get guideTitle3 => 'Simple now, powerful later';

  @override
  String get guideBody3 =>
      'Upscale Mode is fast and automatic. Pro controls stay out of the way.';

  @override
  String get guideTitle4 => 'Private and offline';

  @override
  String get guideBody4 => 'Your photos never leave your device.';

  @override
  String get photoPermissionTitle => 'See your recent photos';

  @override
  String get photoPermissionBody =>
      'Photo access fills the recent photos row. RealScale-Pro never uploads your images.';

  @override
  String get notificationPermissionTitle => 'Know when it is ready';

  @override
  String get notificationPermissionBody =>
      'Notifications can show progress in the background. They are never used for advertising.';

  @override
  String get checkingDevice => 'Checking your device';

  @override
  String get deviceCheckBody =>
      'Finding the safest and fastest settings for this device.';

  @override
  String get deviceReady => 'Your device is ready';

  @override
  String performanceLabel(String label) {
    return 'Performance: $label';
  }

  @override
  String get light => 'Light';

  @override
  String get standard => 'Standard';

  @override
  String get powerful => 'Powerful';

  @override
  String get setupTitle => 'Setting up RealScale-Pro';

  @override
  String get setupBody =>
      'The two small offline models are ready. Optional helpers can download now or later.';

  @override
  String get queued => 'Queued';

  @override
  String get downloading => 'Downloading';

  @override
  String get verifying => 'Verifying';

  @override
  String get done => 'Done';

  @override
  String get pause => 'Pause';

  @override
  String get resume => 'Resume';

  @override
  String get startUpscaling => 'Start upscaling';

  @override
  String get starterPhoto => 'Compact photo model';

  @override
  String get starterAnime => 'Compact anime model';

  @override
  String get starterClassifier => 'Photo type helper';

  @override
  String get starterDetector => 'Face finder';

  @override
  String get home => 'Home';

  @override
  String get recentPhotos => 'Recent photos';

  @override
  String get seeAll => 'See all';

  @override
  String get allowPhotoAccess => 'Allow photo access';

  @override
  String get recentPermissionMessage =>
      'Allow access to show your newest photos here.';

  @override
  String get noRecentPhotos => 'No recent photos yet';

  @override
  String get upscale => 'Upscale';

  @override
  String get chooseImage => 'Choose another image';

  @override
  String get gallery => 'Gallery';

  @override
  String get gallerySubtitle => 'Albums and all photos';

  @override
  String get files => 'Files';

  @override
  String get filesSubtitle => 'Browse folders';

  @override
  String get camera => 'Camera';

  @override
  String get cameraSubtitle => 'Take a photo';

  @override
  String get more => 'More';

  @override
  String get moreSubtitle => 'Clipboard, cloud, other apps';

  @override
  String get mode => 'Mode';

  @override
  String get upscaleMode => 'Upscale Mode';

  @override
  String get upscaleModeSubtitle =>
      'Fast and simple. Everyday photos. Works on any device.';

  @override
  String get superResolution => 'Super Resolution Mode';

  @override
  String get superResolutionSubtitle =>
      'Maximum quality. For powerful devices.';

  @override
  String get pro => 'PRO';

  @override
  String get superResolutionNotice =>
      'Recommended for high-end devices and advanced users.';

  @override
  String get stayUpscale => 'Stay in Upscale Mode';

  @override
  String get useSuperResolution => 'Use Super Resolution';

  @override
  String get comingLater => 'This mode arrives in a later update.';

  @override
  String get myUpscales => 'My Upscales';

  @override
  String get modelManager => 'Model Manager';

  @override
  String get videoUpscale => 'Video Upscale';

  @override
  String get comingSoon => 'Coming soon';

  @override
  String get videoNotice => 'Video upscaling is coming in a future update.';

  @override
  String get settings => 'Settings';

  @override
  String get guide => 'Guide';

  @override
  String get helpFeedback => 'Help and feedback';

  @override
  String get aboutLicenses => 'About and open-source licenses';

  @override
  String get privacy => 'Privacy';

  @override
  String get rateShare => 'Rate and share the app';

  @override
  String get dragDrop => 'Drop a photo here';

  @override
  String get viewer => 'Photo viewer';

  @override
  String get details => 'Details';

  @override
  String get share => 'Share';

  @override
  String get photoDetails => 'Photo details';

  @override
  String get configTitle => 'Upscale Mode';

  @override
  String get swap => 'Choose a different image';

  @override
  String get defaultLevel => 'Default';

  @override
  String get manualLevel => 'Manual';

  @override
  String get advancedLevel => 'Advanced';

  @override
  String get phaseTwoNotice => 'More controls are coming in the next update.';

  @override
  String get detectedPhoto => 'Detected: Photo · 2x · Auto settings';

  @override
  String get detectedAnime => 'Detected: Anime · 2x · Auto settings';

  @override
  String get change => 'Change';

  @override
  String get facesLater => 'Face enhancement arrives in the next update';

  @override
  String outputEstimate(int width, int height, int minutes) {
    return '$width × $height · about $minutes min';
  }

  @override
  String get preparingImage => 'Preparing image';

  @override
  String get enhancingDetails => 'Enhancing details';

  @override
  String get finishingSaving => 'Finishing and saving';

  @override
  String get estimating => 'Estimating…';

  @override
  String etaSeconds(int seconds) {
    return 'About $seconds seconds left';
  }

  @override
  String tileCounter(int current, int total) {
    return 'Tile $current of $total';
  }

  @override
  String get continueBackground => 'Continue in background';

  @override
  String get cancelQuestion => 'Cancel this upscale?';

  @override
  String get cancelBody =>
      'Current progress will be removed. Your original photo stays safe.';

  @override
  String get keepWorking => 'Keep working';

  @override
  String get tip => 'Tip: 2x is a great choice for everyday photos.';

  @override
  String get safeRetry => 'Try safer settings';

  @override
  String get processingError =>
      'That did not finish, but your original photo is safe.';

  @override
  String get result => 'Your upscale is ready';

  @override
  String get savedTo => 'Saved to Pictures/RealScale-Pro';

  @override
  String get upscaleAgain => 'Upscale again';

  @override
  String get newImage => 'New image';

  @override
  String get deleteResult => 'Delete result';

  @override
  String get openGallery => 'Open in gallery';

  @override
  String get before => 'Before';

  @override
  String get after => 'After';

  @override
  String get emptyUpscales => 'Your finished images will appear here';

  @override
  String get emptyUpscalesBody =>
      'Choose a photo on Home to create your first upscale.';

  @override
  String get delete => 'Delete';

  @override
  String get theme => 'Theme';

  @override
  String get systemTheme => 'System';

  @override
  String get lightTheme => 'Light';

  @override
  String get darkTheme => 'Dark';

  @override
  String get dynamicColor => 'Use device colors';

  @override
  String get haptics => 'Haptics';

  @override
  String get keepAwake => 'Keep screen awake while processing';

  @override
  String get language => 'Language';

  @override
  String get english => 'English';

  @override
  String get replayGuide => 'Replay the guide';

  @override
  String get openSourceLicenses => 'Open-source licenses';

  @override
  String get privacyStatement =>
      'Images stay on this device. There are no accounts, uploads, analytics or crash reports by default.';

  @override
  String appVersion(String version) {
    return 'Version $version';
  }

  @override
  String get loading => 'Loading…';

  @override
  String get somethingWentWrong => 'Something went wrong';

  @override
  String get pickAnother => 'Pick another image';

  @override
  String get unsupportedImage =>
      'This image cannot be opened. Try JPEG, PNG, WebP, HEIC or BMP.';

  @override
  String get storageLow =>
      'There is not enough free space to save this result.';

  @override
  String get permissionSettings => 'Open settings';

  @override
  String get selectFromDevice => 'Select from this device';

  @override
  String get clipboardUnavailable =>
      'No supported image is available on the clipboard.';

  @override
  String get downloadMobileTitle => 'Use mobile data?';

  @override
  String get downloadMobileBody =>
      'Download optional setup files using mobile data? Wi-Fi only is recommended.';

  @override
  String get wifiOnly => 'Wait for Wi-Fi';

  @override
  String get useMobileData => 'Use mobile data';

  @override
  String get downloadPaused => 'Download paused';

  @override
  String get noInternet =>
      'No internet connection. Your partial download is saved.';

  @override
  String get savedFallback =>
      'Saved in the private app folder because Pictures was unavailable.';

  @override
  String get desktopRecent => 'Recent files';

  @override
  String get selectAll => 'Select all';
}
