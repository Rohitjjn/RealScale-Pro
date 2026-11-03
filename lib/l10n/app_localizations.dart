import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('en')];

  /// No description provided for @appName.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro'**
  String get appName;

  /// No description provided for @skip.
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get skip;

  /// No description provided for @next.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get next;

  /// No description provided for @getStarted.
  ///
  /// In en, this message translates to:
  /// **'Get started'**
  String get getStarted;

  /// No description provided for @continueLabel.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get continueLabel;

  /// No description provided for @close.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @notNow.
  ///
  /// In en, this message translates to:
  /// **'Not now'**
  String get notNow;

  /// No description provided for @allow.
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get allow;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @back.
  ///
  /// In en, this message translates to:
  /// **'Back'**
  String get back;

  /// No description provided for @guideTitle1.
  ///
  /// In en, this message translates to:
  /// **'Make any photo sharper'**
  String get guideTitle1;

  /// No description provided for @guideBody1.
  ///
  /// In en, this message translates to:
  /// **'Turn small or soft photos into clear, share-ready images.'**
  String get guideBody1;

  /// No description provided for @guideTitle2.
  ///
  /// In en, this message translates to:
  /// **'Works on every device'**
  String get guideTitle2;

  /// No description provided for @guideBody2.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro adapts its work to your phone or computer.'**
  String get guideBody2;

  /// No description provided for @guideTitle3.
  ///
  /// In en, this message translates to:
  /// **'Simple now, powerful later'**
  String get guideTitle3;

  /// No description provided for @guideBody3.
  ///
  /// In en, this message translates to:
  /// **'Upscale Mode is fast and automatic. Pro controls stay out of the way.'**
  String get guideBody3;

  /// No description provided for @guideTitle4.
  ///
  /// In en, this message translates to:
  /// **'Private and offline'**
  String get guideTitle4;

  /// No description provided for @guideBody4.
  ///
  /// In en, this message translates to:
  /// **'Your photos never leave your device.'**
  String get guideBody4;

  /// No description provided for @photoPermissionTitle.
  ///
  /// In en, this message translates to:
  /// **'See your recent photos'**
  String get photoPermissionTitle;

  /// No description provided for @photoPermissionBody.
  ///
  /// In en, this message translates to:
  /// **'Photo access fills the recent photos row. RealScale-Pro never uploads your images.'**
  String get photoPermissionBody;

  /// No description provided for @notificationPermissionTitle.
  ///
  /// In en, this message translates to:
  /// **'Know when it is ready'**
  String get notificationPermissionTitle;

  /// No description provided for @notificationPermissionBody.
  ///
  /// In en, this message translates to:
  /// **'Notifications can show progress in the background. They are never used for advertising.'**
  String get notificationPermissionBody;

  /// No description provided for @checkingDevice.
  ///
  /// In en, this message translates to:
  /// **'Checking your device'**
  String get checkingDevice;

  /// No description provided for @deviceCheckBody.
  ///
  /// In en, this message translates to:
  /// **'Finding the safest and fastest settings for this device.'**
  String get deviceCheckBody;

  /// No description provided for @deviceReady.
  ///
  /// In en, this message translates to:
  /// **'Your device is ready'**
  String get deviceReady;

  /// No description provided for @performanceLabel.
  ///
  /// In en, this message translates to:
  /// **'Performance: {label}'**
  String performanceLabel(String label);

  /// No description provided for @light.
  ///
  /// In en, this message translates to:
  /// **'Light'**
  String get light;

  /// No description provided for @standard.
  ///
  /// In en, this message translates to:
  /// **'Standard'**
  String get standard;

  /// No description provided for @powerful.
  ///
  /// In en, this message translates to:
  /// **'Powerful'**
  String get powerful;

  /// No description provided for @setupTitle.
  ///
  /// In en, this message translates to:
  /// **'Setting up RealScale-Pro'**
  String get setupTitle;

  /// No description provided for @setupBody.
  ///
  /// In en, this message translates to:
  /// **'The two small offline models are ready. Optional helpers can download now or later.'**
  String get setupBody;

  /// No description provided for @queued.
  ///
  /// In en, this message translates to:
  /// **'Queued'**
  String get queued;

  /// No description provided for @downloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading'**
  String get downloading;

  /// No description provided for @verifying.
  ///
  /// In en, this message translates to:
  /// **'Verifying'**
  String get verifying;

  /// No description provided for @done.
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get done;

  /// No description provided for @pause.
  ///
  /// In en, this message translates to:
  /// **'Pause'**
  String get pause;

  /// No description provided for @resume.
  ///
  /// In en, this message translates to:
  /// **'Resume'**
  String get resume;

  /// No description provided for @startUpscaling.
  ///
  /// In en, this message translates to:
  /// **'Start upscaling'**
  String get startUpscaling;

  /// No description provided for @starterPhoto.
  ///
  /// In en, this message translates to:
  /// **'Compact photo model'**
  String get starterPhoto;

  /// No description provided for @starterAnime.
  ///
  /// In en, this message translates to:
  /// **'Compact anime model'**
  String get starterAnime;

  /// No description provided for @starterClassifier.
  ///
  /// In en, this message translates to:
  /// **'Photo type helper'**
  String get starterClassifier;

  /// No description provided for @starterDetector.
  ///
  /// In en, this message translates to:
  /// **'Face finder'**
  String get starterDetector;

  /// No description provided for @home.
  ///
  /// In en, this message translates to:
  /// **'Home'**
  String get home;

  /// No description provided for @recentPhotos.
  ///
  /// In en, this message translates to:
  /// **'Recent photos'**
  String get recentPhotos;

  /// No description provided for @seeAll.
  ///
  /// In en, this message translates to:
  /// **'See all'**
  String get seeAll;

  /// No description provided for @allowPhotoAccess.
  ///
  /// In en, this message translates to:
  /// **'Allow photo access'**
  String get allowPhotoAccess;

  /// No description provided for @recentPermissionMessage.
  ///
  /// In en, this message translates to:
  /// **'Allow access to show your newest photos here.'**
  String get recentPermissionMessage;

  /// No description provided for @noRecentPhotos.
  ///
  /// In en, this message translates to:
  /// **'No recent photos yet'**
  String get noRecentPhotos;

  /// No description provided for @upscale.
  ///
  /// In en, this message translates to:
  /// **'Upscale'**
  String get upscale;

  /// No description provided for @chooseImage.
  ///
  /// In en, this message translates to:
  /// **'Choose another image'**
  String get chooseImage;

  /// No description provided for @gallery.
  ///
  /// In en, this message translates to:
  /// **'Gallery'**
  String get gallery;

  /// No description provided for @gallerySubtitle.
  ///
  /// In en, this message translates to:
  /// **'Albums and all photos'**
  String get gallerySubtitle;

  /// No description provided for @files.
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get files;

  /// No description provided for @filesSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Browse folders'**
  String get filesSubtitle;

  /// No description provided for @camera.
  ///
  /// In en, this message translates to:
  /// **'Camera'**
  String get camera;

  /// No description provided for @cameraSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Take a photo'**
  String get cameraSubtitle;

  /// No description provided for @more.
  ///
  /// In en, this message translates to:
  /// **'More'**
  String get more;

  /// No description provided for @moreSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Clipboard, cloud, other apps'**
  String get moreSubtitle;

  /// No description provided for @mode.
  ///
  /// In en, this message translates to:
  /// **'Mode'**
  String get mode;

  /// No description provided for @upscaleMode.
  ///
  /// In en, this message translates to:
  /// **'Upscale Mode'**
  String get upscaleMode;

  /// No description provided for @upscaleModeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Fast and simple. Everyday photos. Works on any device.'**
  String get upscaleModeSubtitle;

  /// No description provided for @superResolution.
  ///
  /// In en, this message translates to:
  /// **'Super Resolution Mode'**
  String get superResolution;

  /// No description provided for @superResolutionSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Maximum quality. For powerful devices.'**
  String get superResolutionSubtitle;

  /// No description provided for @pro.
  ///
  /// In en, this message translates to:
  /// **'PRO'**
  String get pro;

  /// No description provided for @superResolutionNotice.
  ///
  /// In en, this message translates to:
  /// **'Recommended for high-end devices and advanced users.'**
  String get superResolutionNotice;

  /// No description provided for @stayUpscale.
  ///
  /// In en, this message translates to:
  /// **'Stay in Upscale Mode'**
  String get stayUpscale;

  /// No description provided for @useSuperResolution.
  ///
  /// In en, this message translates to:
  /// **'Use Super Resolution'**
  String get useSuperResolution;

  /// No description provided for @comingLater.
  ///
  /// In en, this message translates to:
  /// **'This mode arrives in a later update.'**
  String get comingLater;

  /// No description provided for @myUpscales.
  ///
  /// In en, this message translates to:
  /// **'My Upscales'**
  String get myUpscales;

  /// No description provided for @modelManager.
  ///
  /// In en, this message translates to:
  /// **'Model Manager'**
  String get modelManager;

  /// No description provided for @videoUpscale.
  ///
  /// In en, this message translates to:
  /// **'Video Upscale'**
  String get videoUpscale;

  /// No description provided for @comingSoon.
  ///
  /// In en, this message translates to:
  /// **'Coming soon'**
  String get comingSoon;

  /// No description provided for @videoNotice.
  ///
  /// In en, this message translates to:
  /// **'Video upscaling is coming in a future update.'**
  String get videoNotice;

  /// No description provided for @settings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// No description provided for @guide.
  ///
  /// In en, this message translates to:
  /// **'Guide'**
  String get guide;

  /// No description provided for @helpFeedback.
  ///
  /// In en, this message translates to:
  /// **'Help and feedback'**
  String get helpFeedback;

  /// No description provided for @aboutLicenses.
  ///
  /// In en, this message translates to:
  /// **'About and open-source licenses'**
  String get aboutLicenses;

  /// No description provided for @privacy.
  ///
  /// In en, this message translates to:
  /// **'Privacy'**
  String get privacy;

  /// No description provided for @rateShare.
  ///
  /// In en, this message translates to:
  /// **'Rate and share the app'**
  String get rateShare;

  /// No description provided for @dragDrop.
  ///
  /// In en, this message translates to:
  /// **'Drop a photo here'**
  String get dragDrop;

  /// No description provided for @viewer.
  ///
  /// In en, this message translates to:
  /// **'Photo viewer'**
  String get viewer;

  /// No description provided for @details.
  ///
  /// In en, this message translates to:
  /// **'Details'**
  String get details;

  /// No description provided for @share.
  ///
  /// In en, this message translates to:
  /// **'Share'**
  String get share;

  /// No description provided for @photoDetails.
  ///
  /// In en, this message translates to:
  /// **'Photo details'**
  String get photoDetails;

  /// No description provided for @configTitle.
  ///
  /// In en, this message translates to:
  /// **'Upscale Mode'**
  String get configTitle;

  /// No description provided for @swap.
  ///
  /// In en, this message translates to:
  /// **'Choose a different image'**
  String get swap;

  /// No description provided for @defaultLevel.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get defaultLevel;

  /// No description provided for @manualLevel.
  ///
  /// In en, this message translates to:
  /// **'Manual'**
  String get manualLevel;

  /// No description provided for @advancedLevel.
  ///
  /// In en, this message translates to:
  /// **'Advanced'**
  String get advancedLevel;

  /// No description provided for @phaseTwoNotice.
  ///
  /// In en, this message translates to:
  /// **'More controls are coming in the next update.'**
  String get phaseTwoNotice;

  /// No description provided for @detectedPhoto.
  ///
  /// In en, this message translates to:
  /// **'Detected: Photo · 2x · Auto settings'**
  String get detectedPhoto;

  /// No description provided for @detectedAnime.
  ///
  /// In en, this message translates to:
  /// **'Detected: Anime · 2x · Auto settings'**
  String get detectedAnime;

  /// No description provided for @change.
  ///
  /// In en, this message translates to:
  /// **'Change'**
  String get change;

  /// No description provided for @facesLater.
  ///
  /// In en, this message translates to:
  /// **'Face enhancement arrives in the next update'**
  String get facesLater;

  /// No description provided for @outputEstimate.
  ///
  /// In en, this message translates to:
  /// **'{width} × {height} · about {minutes} min'**
  String outputEstimate(int width, int height, int minutes);

  /// No description provided for @preparingImage.
  ///
  /// In en, this message translates to:
  /// **'Preparing image'**
  String get preparingImage;

  /// No description provided for @enhancingDetails.
  ///
  /// In en, this message translates to:
  /// **'Enhancing details'**
  String get enhancingDetails;

  /// No description provided for @finishingSaving.
  ///
  /// In en, this message translates to:
  /// **'Finishing and saving'**
  String get finishingSaving;

  /// No description provided for @estimating.
  ///
  /// In en, this message translates to:
  /// **'Estimating…'**
  String get estimating;

  /// No description provided for @etaSeconds.
  ///
  /// In en, this message translates to:
  /// **'About {seconds} seconds left'**
  String etaSeconds(int seconds);

  /// No description provided for @tileCounter.
  ///
  /// In en, this message translates to:
  /// **'Tile {current} of {total}'**
  String tileCounter(int current, int total);

  /// No description provided for @continueBackground.
  ///
  /// In en, this message translates to:
  /// **'Continue in background'**
  String get continueBackground;

  /// No description provided for @cancelQuestion.
  ///
  /// In en, this message translates to:
  /// **'Cancel this upscale?'**
  String get cancelQuestion;

  /// No description provided for @cancelBody.
  ///
  /// In en, this message translates to:
  /// **'Current progress will be removed. Your original photo stays safe.'**
  String get cancelBody;

  /// No description provided for @keepWorking.
  ///
  /// In en, this message translates to:
  /// **'Keep working'**
  String get keepWorking;

  /// No description provided for @tip.
  ///
  /// In en, this message translates to:
  /// **'Tip: 2x is a great choice for everyday photos.'**
  String get tip;

  /// No description provided for @safeRetry.
  ///
  /// In en, this message translates to:
  /// **'Try safer settings'**
  String get safeRetry;

  /// No description provided for @processingError.
  ///
  /// In en, this message translates to:
  /// **'That did not finish, but your original photo is safe.'**
  String get processingError;

  /// No description provided for @result.
  ///
  /// In en, this message translates to:
  /// **'Your upscale is ready'**
  String get result;

  /// No description provided for @savedTo.
  ///
  /// In en, this message translates to:
  /// **'Saved to Pictures/RealScale-Pro'**
  String get savedTo;

  /// No description provided for @upscaleAgain.
  ///
  /// In en, this message translates to:
  /// **'Upscale again'**
  String get upscaleAgain;

  /// No description provided for @newImage.
  ///
  /// In en, this message translates to:
  /// **'New image'**
  String get newImage;

  /// No description provided for @deleteResult.
  ///
  /// In en, this message translates to:
  /// **'Delete result'**
  String get deleteResult;

  /// No description provided for @openGallery.
  ///
  /// In en, this message translates to:
  /// **'Open in gallery'**
  String get openGallery;

  /// No description provided for @before.
  ///
  /// In en, this message translates to:
  /// **'Before'**
  String get before;

  /// No description provided for @after.
  ///
  /// In en, this message translates to:
  /// **'After'**
  String get after;

  /// No description provided for @emptyUpscales.
  ///
  /// In en, this message translates to:
  /// **'Your finished images will appear here'**
  String get emptyUpscales;

  /// No description provided for @emptyUpscalesBody.
  ///
  /// In en, this message translates to:
  /// **'Choose a photo on Home to create your first upscale.'**
  String get emptyUpscalesBody;

  /// No description provided for @delete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete;

  /// No description provided for @theme.
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get theme;

  /// No description provided for @systemTheme.
  ///
  /// In en, this message translates to:
  /// **'System'**
  String get systemTheme;

  /// No description provided for @lightTheme.
  ///
  /// In en, this message translates to:
  /// **'Light'**
  String get lightTheme;

  /// No description provided for @darkTheme.
  ///
  /// In en, this message translates to:
  /// **'Dark'**
  String get darkTheme;

  /// No description provided for @dynamicColor.
  ///
  /// In en, this message translates to:
  /// **'Use device colors'**
  String get dynamicColor;

  /// No description provided for @haptics.
  ///
  /// In en, this message translates to:
  /// **'Haptics'**
  String get haptics;

  /// No description provided for @keepAwake.
  ///
  /// In en, this message translates to:
  /// **'Keep screen awake while processing'**
  String get keepAwake;

  /// No description provided for @language.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// No description provided for @english.
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get english;

  /// No description provided for @replayGuide.
  ///
  /// In en, this message translates to:
  /// **'Replay the guide'**
  String get replayGuide;

  /// No description provided for @openSourceLicenses.
  ///
  /// In en, this message translates to:
  /// **'Open-source licenses'**
  String get openSourceLicenses;

  /// No description provided for @privacyStatement.
  ///
  /// In en, this message translates to:
  /// **'Images stay on this device. There are no accounts, uploads, analytics or crash reports by default.'**
  String get privacyStatement;

  /// No description provided for @appVersion.
  ///
  /// In en, this message translates to:
  /// **'Version {version}'**
  String appVersion(String version);

  /// No description provided for @loading.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loading;

  /// No description provided for @somethingWentWrong.
  ///
  /// In en, this message translates to:
  /// **'Something went wrong'**
  String get somethingWentWrong;

  /// No description provided for @pickAnother.
  ///
  /// In en, this message translates to:
  /// **'Pick another image'**
  String get pickAnother;

  /// No description provided for @unsupportedImage.
  ///
  /// In en, this message translates to:
  /// **'This image cannot be opened. Try JPEG, PNG, WebP, HEIC or BMP.'**
  String get unsupportedImage;

  /// No description provided for @storageLow.
  ///
  /// In en, this message translates to:
  /// **'There is not enough free space to save this result.'**
  String get storageLow;

  /// No description provided for @permissionSettings.
  ///
  /// In en, this message translates to:
  /// **'Open settings'**
  String get permissionSettings;

  /// No description provided for @selectFromDevice.
  ///
  /// In en, this message translates to:
  /// **'Select from this device'**
  String get selectFromDevice;

  /// No description provided for @clipboardUnavailable.
  ///
  /// In en, this message translates to:
  /// **'No supported image is available on the clipboard.'**
  String get clipboardUnavailable;

  /// No description provided for @downloadMobileTitle.
  ///
  /// In en, this message translates to:
  /// **'Use mobile data?'**
  String get downloadMobileTitle;

  /// No description provided for @downloadMobileBody.
  ///
  /// In en, this message translates to:
  /// **'Download optional setup files using mobile data? Wi-Fi only is recommended.'**
  String get downloadMobileBody;

  /// No description provided for @wifiOnly.
  ///
  /// In en, this message translates to:
  /// **'Wait for Wi-Fi'**
  String get wifiOnly;

  /// No description provided for @useMobileData.
  ///
  /// In en, this message translates to:
  /// **'Use mobile data'**
  String get useMobileData;

  /// No description provided for @downloadPaused.
  ///
  /// In en, this message translates to:
  /// **'Download paused'**
  String get downloadPaused;

  /// No description provided for @noInternet.
  ///
  /// In en, this message translates to:
  /// **'No internet connection. Your partial download is saved.'**
  String get noInternet;

  /// No description provided for @savedFallback.
  ///
  /// In en, this message translates to:
  /// **'Saved in the private app folder because Pictures was unavailable.'**
  String get savedFallback;

  /// No description provided for @desktopRecent.
  ///
  /// In en, this message translates to:
  /// **'Recent files'**
  String get desktopRecent;

  /// No description provided for @selectAll.
  ///
  /// In en, this message translates to:
  /// **'Select all'**
  String get selectAll;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
