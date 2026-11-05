import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_hi.dart';

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

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
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
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('hi'),
  ];

  /// No description provided for @appName.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro'**
  String get appName;

  /// No description provided for @appTagline.
  ///
  /// In en, this message translates to:
  /// **'Offline AI photo upscaler'**
  String get appTagline;

  /// No description provided for @guideSkip.
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get guideSkip;

  /// No description provided for @guideNext.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get guideNext;

  /// No description provided for @guideGetStarted.
  ///
  /// In en, this message translates to:
  /// **'Get started'**
  String get guideGetStarted;

  /// No description provided for @guide1Title.
  ///
  /// In en, this message translates to:
  /// **'Make small photos big'**
  String get guide1Title;

  /// No description provided for @guide1Body.
  ///
  /// In en, this message translates to:
  /// **'Turn old, small or compressed photos into sharp high-resolution images — up to 5×, right on your phone.'**
  String get guide1Body;

  /// No description provided for @guide2Title.
  ///
  /// In en, this message translates to:
  /// **'Private by design'**
  String get guide2Title;

  /// No description provided for @guide2Body.
  ///
  /// In en, this message translates to:
  /// **'Everything runs on this device. Your photos never leave it. No account, no cloud, no uploads.'**
  String get guide2Body;

  /// No description provided for @guide3Title.
  ///
  /// In en, this message translates to:
  /// **'One tap with Default'**
  String get guide3Title;

  /// No description provided for @guide3Body.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro picks the best model and settings for your photo automatically. Just tap Upscale.'**
  String get guide3Body;

  /// No description provided for @guide4Title.
  ///
  /// In en, this message translates to:
  /// **'Choose your quality'**
  String get guide4Title;

  /// No description provided for @guide4Body.
  ///
  /// In en, this message translates to:
  /// **'Switch to Manual to pick a specific model, scale and face restoration when you want full control.'**
  String get guide4Body;

  /// No description provided for @permissionPhotosTitle.
  ///
  /// In en, this message translates to:
  /// **'Allow photo access?'**
  String get permissionPhotosTitle;

  /// No description provided for @permissionPhotosBody.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro uses your photo library to show recent photos and to save finished results into your gallery.'**
  String get permissionPhotosBody;

  /// No description provided for @permissionPhotosAllow.
  ///
  /// In en, this message translates to:
  /// **'Allow photo access'**
  String get permissionPhotosAllow;

  /// No description provided for @permissionPhotosNotNow.
  ///
  /// In en, this message translates to:
  /// **'Not now'**
  String get permissionPhotosNotNow;

  /// No description provided for @permissionPhotosDeniedCardTitle.
  ///
  /// In en, this message translates to:
  /// **'Allow photo access'**
  String get permissionPhotosDeniedCardTitle;

  /// No description provided for @permissionPhotosDeniedCardBody.
  ///
  /// In en, this message translates to:
  /// **'Without photo access you can still import with Files, Camera or the system picker.'**
  String get permissionPhotosDeniedCardBody;

  /// No description provided for @permissionPhotosOpenSettings.
  ///
  /// In en, this message translates to:
  /// **'Open settings'**
  String get permissionPhotosOpenSettings;

  /// No description provided for @permissionNotificationsTitle.
  ///
  /// In en, this message translates to:
  /// **'Stay notified during upscaling'**
  String get permissionNotificationsTitle;

  /// No description provided for @permissionNotificationsBody.
  ///
  /// In en, this message translates to:
  /// **'A notification shows progress when you keep upscaling running in the background.'**
  String get permissionNotificationsBody;

  /// No description provided for @permissionNotificationsAllow.
  ///
  /// In en, this message translates to:
  /// **'Allow notifications'**
  String get permissionNotificationsAllow;

  /// No description provided for @permissionNotificationsNotNow.
  ///
  /// In en, this message translates to:
  /// **'Not now'**
  String get permissionNotificationsNotNow;

  /// No description provided for @permissionSelectedPhotos.
  ///
  /// In en, this message translates to:
  /// **'Selected photos only — you can add more in system settings.'**
  String get permissionSelectedPhotos;

  /// No description provided for @deviceCheckTitle.
  ///
  /// In en, this message translates to:
  /// **'Checking your device'**
  String get deviceCheckTitle;

  /// No description provided for @deviceCheckBody.
  ///
  /// In en, this message translates to:
  /// **'We measure a small sample to pick the right quality for your phone. This takes a moment.'**
  String get deviceCheckBody;

  /// No description provided for @deviceCheckDone.
  ///
  /// In en, this message translates to:
  /// **'Your device is ready for'**
  String get deviceCheckDone;

  /// No description provided for @deviceCheckTierLight.
  ///
  /// In en, this message translates to:
  /// **'Light quality'**
  String get deviceCheckTierLight;

  /// No description provided for @deviceCheckTierStandard.
  ///
  /// In en, this message translates to:
  /// **'Standard quality'**
  String get deviceCheckTierStandard;

  /// No description provided for @deviceCheckTierPowerful.
  ///
  /// In en, this message translates to:
  /// **'Powerful quality'**
  String get deviceCheckTierPowerful;

  /// No description provided for @setupTitle.
  ///
  /// In en, this message translates to:
  /// **'Setup'**
  String get setupTitle;

  /// No description provided for @setupStarterPack.
  ///
  /// In en, this message translates to:
  /// **'Starter Pack'**
  String get setupStarterPack;

  /// No description provided for @setupStarterSubtitle.
  ///
  /// In en, this message translates to:
  /// **'The models needed for Default quality'**
  String get setupStarterSubtitle;

  /// No description provided for @setupIncluded.
  ///
  /// In en, this message translates to:
  /// **'Included'**
  String get setupIncluded;

  /// No description provided for @setupDownload.
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get setupDownload;

  /// No description provided for @setupDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading…'**
  String get setupDownloading;

  /// No description provided for @setupPaused.
  ///
  /// In en, this message translates to:
  /// **'Paused'**
  String get setupPaused;

  /// No description provided for @setupInstalled.
  ///
  /// In en, this message translates to:
  /// **'Installed'**
  String get setupInstalled;

  /// No description provided for @setupOverallProgress.
  ///
  /// In en, this message translates to:
  /// **'{percent} · {mb} MB · {speed} · ETA {eta}'**
  String setupOverallProgress(
    String percent,
    String mb,
    String speed,
    String eta,
  );

  /// No description provided for @setupMobileDataTitle.
  ///
  /// In en, this message translates to:
  /// **'Use mobile data?'**
  String get setupMobileDataTitle;

  /// No description provided for @setupMobileDataBody.
  ///
  /// In en, this message translates to:
  /// **'You are not on Wi-Fi. Downloads are set to Wi-Fi-only. Allow mobile data for this download?'**
  String get setupMobileDataBody;

  /// No description provided for @setupAllowMobileData.
  ///
  /// In en, this message translates to:
  /// **'Allow this time'**
  String get setupAllowMobileData;

  /// No description provided for @setupKeepWifiOnly.
  ///
  /// In en, this message translates to:
  /// **'Keep Wi-Fi only'**
  String get setupKeepWifiOnly;

  /// No description provided for @setupContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get setupContinue;

  /// No description provided for @setupResumeHint.
  ///
  /// In en, this message translates to:
  /// **'Downloads resume automatically if the app closes.'**
  String get setupResumeHint;

  /// No description provided for @homeGreeting.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro'**
  String get homeGreeting;

  /// No description provided for @homeSettingsTooltip.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get homeSettingsTooltip;

  /// No description provided for @homeRecents.
  ///
  /// In en, this message translates to:
  /// **'Recent photos'**
  String get homeRecents;

  /// No description provided for @homeSeeAll.
  ///
  /// In en, this message translates to:
  /// **'See all'**
  String get homeSeeAll;

  /// No description provided for @homeRecentsEmptyTitle.
  ///
  /// In en, this message translates to:
  /// **'No photos yet'**
  String get homeRecentsEmptyTitle;

  /// No description provided for @homeRecentsEmptyBody.
  ///
  /// In en, this message translates to:
  /// **'Import a photo to get started.'**
  String get homeRecentsEmptyBody;

  /// No description provided for @homeImportSection.
  ///
  /// In en, this message translates to:
  /// **'Import'**
  String get homeImportSection;

  /// No description provided for @homeImportGallery.
  ///
  /// In en, this message translates to:
  /// **'Gallery'**
  String get homeImportGallery;

  /// No description provided for @homeImportFiles.
  ///
  /// In en, this message translates to:
  /// **'Files'**
  String get homeImportFiles;

  /// No description provided for @homeImportCamera.
  ///
  /// In en, this message translates to:
  /// **'Camera'**
  String get homeImportCamera;

  /// No description provided for @homeImportMore.
  ///
  /// In en, this message translates to:
  /// **'More'**
  String get homeImportMore;

  /// No description provided for @homeOptionsSection.
  ///
  /// In en, this message translates to:
  /// **'Options'**
  String get homeOptionsSection;

  /// No description provided for @homeModeUpscaleTitle.
  ///
  /// In en, this message translates to:
  /// **'Upscale'**
  String get homeModeUpscaleTitle;

  /// No description provided for @homeModeUpscaleBody.
  ///
  /// In en, this message translates to:
  /// **'Fast, smart, default quality'**
  String get homeModeUpscaleBody;

  /// No description provided for @homeModeSrTitle.
  ///
  /// In en, this message translates to:
  /// **'Super Resolution'**
  String get homeModeSrTitle;

  /// No description provided for @homeModeSrBody.
  ///
  /// In en, this message translates to:
  /// **'Maximum quality, no recompression'**
  String get homeModeSrBody;

  /// No description provided for @homeModeSrBadge.
  ///
  /// In en, this message translates to:
  /// **'PRO'**
  String get homeModeSrBadge;

  /// No description provided for @homeOptionMyUpscales.
  ///
  /// In en, this message translates to:
  /// **'My Upscales'**
  String get homeOptionMyUpscales;

  /// No description provided for @homeOptionModelManager.
  ///
  /// In en, this message translates to:
  /// **'Model Manager'**
  String get homeOptionModelManager;

  /// No description provided for @homeOptionSettings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get homeOptionSettings;

  /// No description provided for @homeOptionVideo.
  ///
  /// In en, this message translates to:
  /// **'Video Upscale'**
  String get homeOptionVideo;

  /// No description provided for @homeVideoComingSoonTitle.
  ///
  /// In en, this message translates to:
  /// **'Video upscaling is coming'**
  String get homeVideoComingSoonTitle;

  /// No description provided for @homeVideoComingSoonBody.
  ///
  /// In en, this message translates to:
  /// **'We are finishing video support. Photos work great already — give one a try!'**
  String get homeVideoComingSoonBody;

  /// No description provided for @homeSrWarningTitle.
  ///
  /// In en, this message translates to:
  /// **'Before you start Super Resolution'**
  String get homeSrWarningTitle;

  /// No description provided for @homeSrWarningBody.
  ///
  /// In en, this message translates to:
  /// **'Super Resolution keeps every original pixel and can take much longer. On {tier} devices we recommend starting with Upscale.'**
  String homeSrWarningBody(Object tier);

  /// No description provided for @homeSrWarningChipFit.
  ///
  /// In en, this message translates to:
  /// **'Fits your device'**
  String get homeSrWarningChipFit;

  /// No description provided for @homeSrWarningChipHeavy.
  ///
  /// In en, this message translates to:
  /// **'Heavy on this device'**
  String get homeSrWarningChipHeavy;

  /// No description provided for @homeSrWarningUseUpscale.
  ///
  /// In en, this message translates to:
  /// **'Use Upscale instead'**
  String get homeSrWarningUseUpscale;

  /// No description provided for @homeSrWarningContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue anyway'**
  String get homeSrWarningContinue;

  /// No description provided for @homePermissionCard.
  ///
  /// In en, this message translates to:
  /// **'Allow photo access to see your recent photos'**
  String get homePermissionCard;

  /// No description provided for @galleryTitle.
  ///
  /// In en, this message translates to:
  /// **'All photos'**
  String get galleryTitle;

  /// No description provided for @galleryEmpty.
  ///
  /// In en, this message translates to:
  /// **'No photos found'**
  String get galleryEmpty;

  /// No description provided for @galleryPermissionNeeded.
  ///
  /// In en, this message translates to:
  /// **'Photo access needed to show your gallery'**
  String get galleryPermissionNeeded;

  /// No description provided for @galleryAllow.
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get galleryAllow;

  /// No description provided for @viewerUpscale.
  ///
  /// In en, this message translates to:
  /// **'Upscale'**
  String get viewerUpscale;

  /// No description provided for @viewerDetails.
  ///
  /// In en, this message translates to:
  /// **'Details'**
  String get viewerDetails;

  /// No description provided for @viewerShare.
  ///
  /// In en, this message translates to:
  /// **'Share'**
  String get viewerShare;

  /// No description provided for @viewerDelete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get viewerDelete;

  /// No description provided for @viewerModeChipUpscale.
  ///
  /// In en, this message translates to:
  /// **'Upscale'**
  String get viewerModeChipUpscale;

  /// No description provided for @viewerModeChipSr.
  ///
  /// In en, this message translates to:
  /// **'Super Res'**
  String get viewerModeChipSr;

  /// No description provided for @detailsResolution.
  ///
  /// In en, this message translates to:
  /// **'Resolution'**
  String get detailsResolution;

  /// No description provided for @detailsSize.
  ///
  /// In en, this message translates to:
  /// **'File size'**
  String get detailsSize;

  /// No description provided for @detailsModified.
  ///
  /// In en, this message translates to:
  /// **'Modified'**
  String get detailsModified;

  /// No description provided for @moreSheetTitle.
  ///
  /// In en, this message translates to:
  /// **'Import from'**
  String get moreSheetTitle;

  /// No description provided for @moreClipboard.
  ///
  /// In en, this message translates to:
  /// **'Paste from clipboard'**
  String get moreClipboard;

  /// No description provided for @moreClipboardEmpty.
  ///
  /// In en, this message translates to:
  /// **'No image found in clipboard'**
  String get moreClipboardEmpty;

  /// No description provided for @moreSystemPicker.
  ///
  /// In en, this message translates to:
  /// **'System photo picker'**
  String get moreSystemPicker;

  /// No description provided for @moreCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get moreCancel;

  /// No description provided for @configTitle.
  ///
  /// In en, this message translates to:
  /// **'Upscale'**
  String get configTitle;

  /// No description provided for @configPreviewSwap.
  ///
  /// In en, this message translates to:
  /// **'Compare'**
  String get configPreviewSwap;

  /// No description provided for @configLevelDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get configLevelDefault;

  /// No description provided for @configLevelManual.
  ///
  /// In en, this message translates to:
  /// **'Manual'**
  String get configLevelManual;

  /// No description provided for @configLevelAdvanced.
  ///
  /// In en, this message translates to:
  /// **'Advanced'**
  String get configLevelAdvanced;

  /// No description provided for @configAdvancedComingSoon.
  ///
  /// In en, this message translates to:
  /// **'Advanced settings arrive in a later version.'**
  String get configAdvancedComingSoon;

  /// No description provided for @configDetectedPhoto.
  ///
  /// In en, this message translates to:
  /// **'Photo'**
  String get configDetectedPhoto;

  /// No description provided for @configDetectedAnime.
  ///
  /// In en, this message translates to:
  /// **'Anime'**
  String get configDetectedAnime;

  /// No description provided for @configDetectedPrefix.
  ///
  /// In en, this message translates to:
  /// **'Detected:'**
  String get configDetectedPrefix;

  /// No description provided for @configScaleLabel.
  ///
  /// In en, this message translates to:
  /// **'{scale}× · Auto'**
  String configScaleLabel(int scale);

  /// No description provided for @configNotSure.
  ///
  /// In en, this message translates to:
  /// **'Not sure — Change?'**
  String get configNotSure;

  /// No description provided for @configChangeLink.
  ///
  /// In en, this message translates to:
  /// **'Change'**
  String get configChangeLink;

  /// No description provided for @configManualComingSoon.
  ///
  /// In en, this message translates to:
  /// **'Manual configuration is coming in this version.'**
  String get configManualComingSoon;

  /// No description provided for @configFaceToggle.
  ///
  /// In en, this message translates to:
  /// **'Restore faces'**
  String get configFaceToggle;

  /// No description provided for @configFaceHint.
  ///
  /// In en, this message translates to:
  /// **'Finds faces and restores them after upscaling.'**
  String get configFaceHint;

  /// No description provided for @configFaceDetectorHint.
  ///
  /// In en, this message translates to:
  /// **'{count} face(s) detected'**
  String configFaceDetectorHint(int count);

  /// No description provided for @configFaceNone.
  ///
  /// In en, this message translates to:
  /// **'No faces detected'**
  String get configFaceNone;

  /// No description provided for @configFaceUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Face restoration needs the Face Pack.'**
  String get configFaceUnavailable;

  /// No description provided for @configFaceDownload.
  ///
  /// In en, this message translates to:
  /// **'Download Face Pack ({size})'**
  String configFaceDownload(Object size);

  /// No description provided for @configOutputSize.
  ///
  /// In en, this message translates to:
  /// **'{width} × {height} px'**
  String configOutputSize(int width, int height);

  /// No description provided for @configEta.
  ///
  /// In en, this message translates to:
  /// **'about {duration}'**
  String configEta(String duration);

  /// No description provided for @configUpscaleButton.
  ///
  /// In en, this message translates to:
  /// **'Upscale'**
  String get configUpscaleButton;

  /// No description provided for @configHeavyTitle.
  ///
  /// In en, this message translates to:
  /// **'Heavy model on this device'**
  String get configHeavyTitle;

  /// No description provided for @configHeavyBody.
  ///
  /// In en, this message translates to:
  /// **'{model} is large for your device and may take longer or stall. Continue anyway?'**
  String configHeavyBody(String model);

  /// No description provided for @configHeavyContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get configHeavyContinue;

  /// No description provided for @configHeavyCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get configHeavyCancel;

  /// No description provided for @configManualSection.
  ///
  /// In en, this message translates to:
  /// **'Model'**
  String get configManualSection;

  /// No description provided for @configScaleSection.
  ///
  /// In en, this message translates to:
  /// **'Scale'**
  String get configScaleSection;

  /// No description provided for @configScaleExtraStep.
  ///
  /// In en, this message translates to:
  /// **'5× uses an extra resize step'**
  String get configScaleExtraStep;

  /// No description provided for @configModelRecommended.
  ///
  /// In en, this message translates to:
  /// **'Recommended'**
  String get configModelRecommended;

  /// No description provided for @configModelDeviceFit.
  ///
  /// In en, this message translates to:
  /// **'Fits device'**
  String get configModelDeviceFit;

  /// No description provided for @configModelHeavy.
  ///
  /// In en, this message translates to:
  /// **'Heavy'**
  String get configModelHeavy;

  /// No description provided for @configModelDownload.
  ///
  /// In en, this message translates to:
  /// **'Download ({size})'**
  String configModelDownload(Object size);

  /// No description provided for @configModelDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading… {percent}'**
  String configModelDownloading(int percent);

  /// No description provided for @configModelUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Unavailable'**
  String get configModelUnavailable;

  /// No description provided for @processingTitle.
  ///
  /// In en, this message translates to:
  /// **'Upscaling'**
  String get processingTitle;

  /// No description provided for @processingStagePreparing.
  ///
  /// In en, this message translates to:
  /// **'Preparing image'**
  String get processingStagePreparing;

  /// No description provided for @processingStageUpscaling.
  ///
  /// In en, this message translates to:
  /// **'Upscaling tiles'**
  String get processingStageUpscaling;

  /// No description provided for @processingStageFaces.
  ///
  /// In en, this message translates to:
  /// **'Restoring faces'**
  String get processingStageFaces;

  /// No description provided for @processingStageSaving.
  ///
  /// In en, this message translates to:
  /// **'Saving result'**
  String get processingStageSaving;

  /// No description provided for @processingTileCount.
  ///
  /// In en, this message translates to:
  /// **'{index} / {total} tiles'**
  String processingTileCount(int index, int total);

  /// No description provided for @processingEtaEstimating.
  ///
  /// In en, this message translates to:
  /// **'Estimating…'**
  String get processingEtaEstimating;

  /// No description provided for @processingEta.
  ///
  /// In en, this message translates to:
  /// **'{duration} left'**
  String processingEta(String duration);

  /// No description provided for @processingCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get processingCancel;

  /// No description provided for @processingCancelConfirmTitle.
  ///
  /// In en, this message translates to:
  /// **'Cancel this upscale?'**
  String get processingCancelConfirmTitle;

  /// No description provided for @processingCancelConfirmBody.
  ///
  /// In en, this message translates to:
  /// **'Progress will be discarded and temporary files removed.'**
  String get processingCancelConfirmBody;

  /// No description provided for @processingCancelYes.
  ///
  /// In en, this message translates to:
  /// **'Cancel upscale'**
  String get processingCancelYes;

  /// No description provided for @processingCancelNo.
  ///
  /// In en, this message translates to:
  /// **'Keep going'**
  String get processingCancelNo;

  /// No description provided for @processingBackground.
  ///
  /// In en, this message translates to:
  /// **'Continue in background'**
  String get processingBackground;

  /// No description provided for @processingBackgroundNote.
  ///
  /// In en, this message translates to:
  /// **'You can leave this screen — a notification shows progress.'**
  String get processingBackgroundNote;

  /// No description provided for @processingTip1.
  ///
  /// In en, this message translates to:
  /// **'Tip: Default quality is one tap and fits every photo.'**
  String get processingTip1;

  /// No description provided for @processingTip2.
  ///
  /// In en, this message translates to:
  /// **'Tip: Super Resolution keeps original quality but takes longer.'**
  String get processingTip2;

  /// No description provided for @processingTip3.
  ///
  /// In en, this message translates to:
  /// **'Tip: Restore faces after upscaling group photos.'**
  String get processingTip3;

  /// No description provided for @processingFailedTitle.
  ///
  /// In en, this message translates to:
  /// **'The upscale didn\'t finish'**
  String get processingFailedTitle;

  /// No description provided for @processingFailedBody.
  ///
  /// In en, this message translates to:
  /// **'This can happen on large photos or low memory.'**
  String get processingFailedBody;

  /// No description provided for @processingTrySafer.
  ///
  /// In en, this message translates to:
  /// **'Try safer settings'**
  String get processingTrySafer;

  /// No description provided for @processingCancelled.
  ///
  /// In en, this message translates to:
  /// **'Cancelled'**
  String get processingCancelled;

  /// No description provided for @processingSlowNote.
  ///
  /// In en, this message translates to:
  /// **'Running slower than usual — battery saver or heat can slow the device.'**
  String get processingSlowNote;

  /// No description provided for @resultTitle.
  ///
  /// In en, this message translates to:
  /// **'Result'**
  String get resultTitle;

  /// No description provided for @resultSavedTo.
  ///
  /// In en, this message translates to:
  /// **'Saved to gallery'**
  String get resultSavedTo;

  /// No description provided for @resultInfoResolution.
  ///
  /// In en, this message translates to:
  /// **'{width} × {height}'**
  String resultInfoResolution(int width, int height);

  /// No description provided for @resultInfoScale.
  ///
  /// In en, this message translates to:
  /// **'{scale}× {model}'**
  String resultInfoScale(int scale, String model);

  /// No description provided for @resultInfoTime.
  ///
  /// In en, this message translates to:
  /// **'{seconds} s'**
  String resultInfoTime(int seconds);

  /// No description provided for @resultBefore.
  ///
  /// In en, this message translates to:
  /// **'Before'**
  String get resultBefore;

  /// No description provided for @resultAfter.
  ///
  /// In en, this message translates to:
  /// **'After'**
  String get resultAfter;

  /// No description provided for @resultShare.
  ///
  /// In en, this message translates to:
  /// **'Share'**
  String get resultShare;

  /// No description provided for @resultUpscaleAgain.
  ///
  /// In en, this message translates to:
  /// **'Upscale again'**
  String get resultUpscaleAgain;

  /// No description provided for @resultNewImage.
  ///
  /// In en, this message translates to:
  /// **'New image'**
  String get resultNewImage;

  /// No description provided for @resultMore.
  ///
  /// In en, this message translates to:
  /// **'More'**
  String get resultMore;

  /// No description provided for @resultHoldToCompare.
  ///
  /// In en, this message translates to:
  /// **'Hold to compare'**
  String get resultHoldToCompare;

  /// No description provided for @resultFacesRestored.
  ///
  /// In en, this message translates to:
  /// **'{count} face(s) restored'**
  String resultFacesRestored(int count);

  /// No description provided for @resultRecoveredNote.
  ///
  /// In en, this message translates to:
  /// **'Finished with adjusted settings for this device'**
  String get resultRecoveredNote;

  /// No description provided for @resultTempInputNote.
  ///
  /// In en, this message translates to:
  /// **'Large input processed via a quality-90 copy (original untouched)'**
  String get resultTempInputNote;

  /// No description provided for @myUpscalesTitle.
  ///
  /// In en, this message translates to:
  /// **'My Upscales'**
  String get myUpscalesTitle;

  /// No description provided for @myUpscalesEmptyTitle.
  ///
  /// In en, this message translates to:
  /// **'Nothing upscaled yet'**
  String get myUpscalesEmptyTitle;

  /// No description provided for @myUpscalesEmptyBody.
  ///
  /// In en, this message translates to:
  /// **'Finished images appear here automatically.'**
  String get myUpscalesEmptyBody;

  /// No description provided for @myUpscalesDelete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get myUpscalesDelete;

  /// No description provided for @myUpscalesShare.
  ///
  /// In en, this message translates to:
  /// **'Share'**
  String get myUpscalesShare;

  /// No description provided for @myUpscalesOpen.
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get myUpscalesOpen;

  /// No description provided for @myUpscalesSelect.
  ///
  /// In en, this message translates to:
  /// **'Select'**
  String get myUpscalesSelect;

  /// No description provided for @myUpscalesSelectedCount.
  ///
  /// In en, this message translates to:
  /// **'{count} selected'**
  String myUpscalesSelectedCount(int count);

  /// No description provided for @myUpscalesDeleteConfirm.
  ///
  /// In en, this message translates to:
  /// **'Delete {count} image(s)? This also removes them from your gallery folder.'**
  String myUpscalesDeleteConfirm(int count);

  /// No description provided for @myUpscalesDeleteConfirmTitle.
  ///
  /// In en, this message translates to:
  /// **'Delete upscaled images?'**
  String get myUpscalesDeleteConfirmTitle;

  /// No description provided for @modelManagerTitle.
  ///
  /// In en, this message translates to:
  /// **'Model Manager'**
  String get modelManagerTitle;

  /// No description provided for @modelManagerStorage.
  ///
  /// In en, this message translates to:
  /// **'{used} of {total} used'**
  String modelManagerStorage(String used, String total);

  /// No description provided for @modelManagerFreeSpace.
  ///
  /// In en, this message translates to:
  /// **'{free} free'**
  String modelManagerFreeSpace(String free);

  /// No description provided for @modelManagerPackStarter.
  ///
  /// In en, this message translates to:
  /// **'Starter Pack'**
  String get modelManagerPackStarter;

  /// No description provided for @modelManagerPackPro.
  ///
  /// In en, this message translates to:
  /// **'Pro Pack'**
  String get modelManagerPackPro;

  /// No description provided for @modelManagerPackFace.
  ///
  /// In en, this message translates to:
  /// **'Face Pack'**
  String get modelManagerPackFace;

  /// No description provided for @modelManagerPackUltra.
  ///
  /// In en, this message translates to:
  /// **'Ultra Pack (coming soon)'**
  String get modelManagerPackUltra;

  /// No description provided for @modelManagerPackCommunity.
  ///
  /// In en, this message translates to:
  /// **'Community Pack'**
  String get modelManagerPackCommunity;

  /// No description provided for @modelManagerFilterAll.
  ///
  /// In en, this message translates to:
  /// **'All'**
  String get modelManagerFilterAll;

  /// No description provided for @modelManagerFilterPhoto.
  ///
  /// In en, this message translates to:
  /// **'Photo'**
  String get modelManagerFilterPhoto;

  /// No description provided for @modelManagerFilterAnime.
  ///
  /// In en, this message translates to:
  /// **'Anime'**
  String get modelManagerFilterAnime;

  /// No description provided for @modelManagerFilterFace.
  ///
  /// In en, this message translates to:
  /// **'Face'**
  String get modelManagerFilterFace;

  /// No description provided for @modelManagerDownloadAll.
  ///
  /// In en, this message translates to:
  /// **'Download pack'**
  String get modelManagerDownloadAll;

  /// No description provided for @modelManagerDeleteAll.
  ///
  /// In en, this message translates to:
  /// **'Delete pack'**
  String get modelManagerDeleteAll;

  /// No description provided for @modelManagerUpdate.
  ///
  /// In en, this message translates to:
  /// **'Update available'**
  String get modelManagerUpdate;

  /// No description provided for @modelManagerStatusInstalled.
  ///
  /// In en, this message translates to:
  /// **'Installed'**
  String get modelManagerStatusInstalled;

  /// No description provided for @modelManagerStatusNotInstalled.
  ///
  /// In en, this message translates to:
  /// **'Not installed'**
  String get modelManagerStatusNotInstalled;

  /// No description provided for @modelManagerStatusDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading…'**
  String get modelManagerStatusDownloading;

  /// No description provided for @modelManagerStatusPaused.
  ///
  /// In en, this message translates to:
  /// **'Paused'**
  String get modelManagerStatusPaused;

  /// No description provided for @modelManagerStatusError.
  ///
  /// In en, this message translates to:
  /// **'Error — tap to retry'**
  String get modelManagerStatusError;

  /// No description provided for @modelManagerStatusUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Unavailable'**
  String get modelManagerStatusUnavailable;

  /// No description provided for @modelManagerUnavailableChecksum.
  ///
  /// In en, this message translates to:
  /// **'Source verification pending'**
  String get modelManagerUnavailableChecksum;

  /// No description provided for @modelManagerUnavailableLicense.
  ///
  /// In en, this message translates to:
  /// **'License does not permit use'**
  String get modelManagerUnavailableLicense;

  /// No description provided for @modelManagerUnavailableSoon.
  ///
  /// In en, this message translates to:
  /// **'Coming in a later version'**
  String get modelManagerUnavailableSoon;

  /// No description provided for @modelManagerLicense.
  ///
  /// In en, this message translates to:
  /// **'License'**
  String get modelManagerLicense;

  /// No description provided for @modelManagerSource.
  ///
  /// In en, this message translates to:
  /// **'Source'**
  String get modelManagerSource;

  /// No description provided for @modelManagerDeleteFallback.
  ///
  /// In en, this message translates to:
  /// **'Default model will be used again for this photo type.'**
  String get modelManagerDeleteFallback;

  /// No description provided for @modelManagerDeleteConfirmTitle.
  ///
  /// In en, this message translates to:
  /// **'Delete this model?'**
  String get modelManagerDeleteConfirmTitle;

  /// No description provided for @modelManagerDeleteConfirmBody.
  ///
  /// In en, this message translates to:
  /// **'You can download it again any time.'**
  String get modelManagerDeleteConfirmBody;

  /// No description provided for @modelManagerSpeed.
  ///
  /// In en, this message translates to:
  /// **'{speed}/s'**
  String modelManagerSpeed(String speed);

  /// No description provided for @modelManagerSize.
  ///
  /// In en, this message translates to:
  /// **'{size}'**
  String modelManagerSize(String size);

  /// No description provided for @settingsTitle.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsTitle;

  /// No description provided for @settingsAppearance.
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get settingsAppearance;

  /// No description provided for @settingsTheme.
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get settingsTheme;

  /// No description provided for @settingsThemeSystem.
  ///
  /// In en, this message translates to:
  /// **'System'**
  String get settingsThemeSystem;

  /// No description provided for @settingsThemeLight.
  ///
  /// In en, this message translates to:
  /// **'Light'**
  String get settingsThemeLight;

  /// No description provided for @settingsThemeDark.
  ///
  /// In en, this message translates to:
  /// **'Dark'**
  String get settingsThemeDark;

  /// No description provided for @settingsDynamicColor.
  ///
  /// In en, this message translates to:
  /// **'Dynamic color'**
  String get settingsDynamicColor;

  /// No description provided for @settingsDynamicColorBody.
  ///
  /// In en, this message translates to:
  /// **'Match app colors to your wallpaper (Material You).'**
  String get settingsDynamicColorBody;

  /// No description provided for @settingsFeel.
  ///
  /// In en, this message translates to:
  /// **'Feel'**
  String get settingsFeel;

  /// No description provided for @settingsHaptics.
  ///
  /// In en, this message translates to:
  /// **'Haptics'**
  String get settingsHaptics;

  /// No description provided for @settingsKeepAwake.
  ///
  /// In en, this message translates to:
  /// **'Keep screen awake'**
  String get settingsKeepAwake;

  /// No description provided for @settingsKeepAwakeBody.
  ///
  /// In en, this message translates to:
  /// **'Prevents sleep while upscaling.'**
  String get settingsKeepAwakeBody;

  /// No description provided for @settingsLanguage.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get settingsLanguage;

  /// No description provided for @settingsLanguageEnglish.
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get settingsLanguageEnglish;

  /// No description provided for @settingsLanguageMore.
  ///
  /// In en, this message translates to:
  /// **'More languages coming'**
  String get settingsLanguageMore;

  /// No description provided for @settingsDefaults.
  ///
  /// In en, this message translates to:
  /// **'Upscaling defaults'**
  String get settingsDefaults;

  /// No description provided for @settingsDefaultMode.
  ///
  /// In en, this message translates to:
  /// **'Default mode'**
  String get settingsDefaultMode;

  /// No description provided for @settingsCompute.
  ///
  /// In en, this message translates to:
  /// **'Compute preference'**
  String get settingsCompute;

  /// No description provided for @settingsComputeAuto.
  ///
  /// In en, this message translates to:
  /// **'Auto'**
  String get settingsComputeAuto;

  /// No description provided for @settingsComputeGpu.
  ///
  /// In en, this message translates to:
  /// **'GPU'**
  String get settingsComputeGpu;

  /// No description provided for @settingsComputeCpu.
  ///
  /// In en, this message translates to:
  /// **'CPU'**
  String get settingsComputeCpu;

  /// No description provided for @settingsThrottle.
  ///
  /// In en, this message translates to:
  /// **'Slow down on battery or heat'**
  String get settingsThrottle;

  /// No description provided for @settingsOutput.
  ///
  /// In en, this message translates to:
  /// **'Output'**
  String get settingsOutput;

  /// No description provided for @settingsOutputFormat.
  ///
  /// In en, this message translates to:
  /// **'Format'**
  String get settingsOutputFormat;

  /// No description provided for @settingsOutputFormatSame.
  ///
  /// In en, this message translates to:
  /// **'Same as source'**
  String get settingsOutputFormatSame;

  /// No description provided for @settingsOutputMetadata.
  ///
  /// In en, this message translates to:
  /// **'Keep photo metadata'**
  String get settingsOutputMetadata;

  /// No description provided for @settingsOutputNaming.
  ///
  /// In en, this message translates to:
  /// **'File names'**
  String get settingsOutputNaming;

  /// No description provided for @settingsOutputNamingRealscale.
  ///
  /// In en, this message translates to:
  /// **'RealScale_<name>'**
  String get settingsOutputNamingRealscale;

  /// No description provided for @settingsOutputNamingOriginal.
  ///
  /// In en, this message translates to:
  /// **'Keep original name'**
  String get settingsOutputNamingOriginal;

  /// No description provided for @settingsDownloads.
  ///
  /// In en, this message translates to:
  /// **'Downloads'**
  String get settingsDownloads;

  /// No description provided for @settingsWifiOnly.
  ///
  /// In en, this message translates to:
  /// **'Download only on Wi-Fi'**
  String get settingsWifiOnly;

  /// No description provided for @settingsStorage.
  ///
  /// In en, this message translates to:
  /// **'Storage'**
  String get settingsStorage;

  /// No description provided for @settingsClearCache.
  ///
  /// In en, this message translates to:
  /// **'Clear temporary files'**
  String get settingsClearCache;

  /// No description provided for @settingsClearCacheBody.
  ///
  /// In en, this message translates to:
  /// **'Installed models are kept.'**
  String get settingsClearCacheBody;

  /// No description provided for @settingsClearCacheDone.
  ///
  /// In en, this message translates to:
  /// **'Temporary files cleared'**
  String get settingsClearCacheDone;

  /// No description provided for @settingsRerunDeviceCheck.
  ///
  /// In en, this message translates to:
  /// **'Run device check again'**
  String get settingsRerunDeviceCheck;

  /// No description provided for @settingsDeviceTier.
  ///
  /// In en, this message translates to:
  /// **'Device profile'**
  String get settingsDeviceTier;

  /// No description provided for @settingsPrivacy.
  ///
  /// In en, this message translates to:
  /// **'Privacy'**
  String get settingsPrivacy;

  /// No description provided for @settingsPrivacyBody.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro works fully offline. Photos never leave this device.'**
  String get settingsPrivacyBody;

  /// No description provided for @settingsAnonymousStats.
  ///
  /// In en, this message translates to:
  /// **'Share anonymous usage stats'**
  String get settingsAnonymousStats;

  /// No description provided for @settingsCrashReports.
  ///
  /// In en, this message translates to:
  /// **'Send crash reports'**
  String get settingsCrashReports;

  /// No description provided for @settingsSupport.
  ///
  /// In en, this message translates to:
  /// **'Support'**
  String get settingsSupport;

  /// No description provided for @settingsReplayGuide.
  ///
  /// In en, this message translates to:
  /// **'Replay the introduction'**
  String get settingsReplayGuide;

  /// No description provided for @settingsFaq.
  ///
  /// In en, this message translates to:
  /// **'FAQ & feedback'**
  String get settingsFaq;

  /// No description provided for @settingsAbout.
  ///
  /// In en, this message translates to:
  /// **'About RealScale-Pro'**
  String get settingsAbout;

  /// No description provided for @settingsLicenses.
  ///
  /// In en, this message translates to:
  /// **'Open-source licenses'**
  String get settingsLicenses;

  /// No description provided for @settingsPrivacyStatement.
  ///
  /// In en, this message translates to:
  /// **'Privacy statement'**
  String get settingsPrivacyStatement;

  /// No description provided for @settingsModelCredits.
  ///
  /// In en, this message translates to:
  /// **'Model credits'**
  String get settingsModelCredits;

  /// No description provided for @settingsCreditsBody.
  ///
  /// In en, this message translates to:
  /// **'Real-ESRGAN (BSD-3), Real-CUGAN (MIT) and ncnn (BSD-3) power the upscaling engine. Face restoration uses GFPGAN (Apache-2.0).'**
  String get settingsCreditsBody;

  /// No description provided for @aboutVersion.
  ///
  /// In en, this message translates to:
  /// **'Version'**
  String get aboutVersion;

  /// No description provided for @aboutBuiltWith.
  ///
  /// In en, this message translates to:
  /// **'Built with Flutter · ncnn engine'**
  String get aboutBuiltWith;

  /// No description provided for @privacyTitle.
  ///
  /// In en, this message translates to:
  /// **'Privacy statement'**
  String get privacyTitle;

  /// No description provided for @privacyBody1.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro processes every photo entirely on this device. No photo, no result and no personal data is ever uploaded.'**
  String get privacyBody1;

  /// No description provided for @privacyBody2.
  ///
  /// In en, this message translates to:
  /// **'The app uses the network for exactly one thing: downloading AI models when you ask for them. Model downloads check the source and file checksum before installing.'**
  String get privacyBody2;

  /// No description provided for @privacyBody3.
  ///
  /// In en, this message translates to:
  /// **'No account is created, no analytics profile is built, and nothing is shared. Optional anonymous statistics and crash reports are off by default and only send technical data — never images.'**
  String get privacyBody3;

  /// No description provided for @faqTitle.
  ///
  /// In en, this message translates to:
  /// **'FAQ & feedback'**
  String get faqTitle;

  /// No description provided for @faqQ1.
  ///
  /// In en, this message translates to:
  /// **'Why does the first upscale take longer?'**
  String get faqQ1;

  /// No description provided for @faqA1.
  ///
  /// In en, this message translates to:
  /// **'The model loads once and is kept ready afterwards. Later upscales are much faster.'**
  String get faqA1;

  /// No description provided for @faqQ2.
  ///
  /// In en, this message translates to:
  /// **'Where are results saved?'**
  String get faqQ2;

  /// No description provided for @faqA2.
  ///
  /// In en, this message translates to:
  /// **'In your gallery under RealScale-Pro, and in My Upscales inside the app.'**
  String get faqA2;

  /// No description provided for @faqQ3.
  ///
  /// In en, this message translates to:
  /// **'Are my photos uploaded anywhere?'**
  String get faqQ3;

  /// No description provided for @faqA3.
  ///
  /// In en, this message translates to:
  /// **'Never. Upscaling happens fully offline on this device.'**
  String get faqA3;

  /// No description provided for @faqFeedbackSubject.
  ///
  /// In en, this message translates to:
  /// **'RealScale-Pro feedback'**
  String get faqFeedbackSubject;

  /// No description provided for @faqFeedbackBody.
  ///
  /// In en, this message translates to:
  /// **'Tell us what works, what doesn\'t, or what you\'d like next.'**
  String get faqFeedbackBody;

  /// No description provided for @licensesTitle.
  ///
  /// In en, this message translates to:
  /// **'Open-source licenses'**
  String get licensesTitle;

  /// No description provided for @errorGenericTitle.
  ///
  /// In en, this message translates to:
  /// **'Something went wrong'**
  String get errorGenericTitle;

  /// No description provided for @errorGenericBody.
  ///
  /// In en, this message translates to:
  /// **'Please try again.'**
  String get errorGenericBody;

  /// No description provided for @errorRetry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get errorRetry;

  /// No description provided for @errorNoPhotos.
  ///
  /// In en, this message translates to:
  /// **'No photos available'**
  String get errorNoPhotos;

  /// No description provided for @errorImageTooLarge.
  ///
  /// In en, this message translates to:
  /// **'This image is too large for this device'**
  String get errorImageTooLarge;

  /// No description provided for @errorOutOfMemory.
  ///
  /// In en, this message translates to:
  /// **'Not enough memory — try safer settings'**
  String get errorOutOfMemory;

  /// No description provided for @errorDiskFull.
  ///
  /// In en, this message translates to:
  /// **'Not enough free storage'**
  String get errorDiskFull;

  /// No description provided for @errorSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save the result'**
  String get errorSaveFailed;

  /// No description provided for @errorNetwork.
  ///
  /// In en, this message translates to:
  /// **'Network unavailable — check your connection'**
  String get errorNetwork;

  /// No description provided for @errorChecksum.
  ///
  /// In en, this message translates to:
  /// **'Download failed verification and will retry'**
  String get errorChecksum;

  /// No description provided for @loading.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loading;

  /// No description provided for @durationSeconds.
  ///
  /// In en, this message translates to:
  /// **'{n} s'**
  String durationSeconds(int n);

  /// No description provided for @durationMinutes.
  ///
  /// In en, this message translates to:
  /// **'{n} min'**
  String durationMinutes(int n);

  /// No description provided for @durationMinutesSeconds.
  ///
  /// In en, this message translates to:
  /// **'{m} min {s} s'**
  String durationMinutesSeconds(int m, int s);

  /// No description provided for @sizeMb.
  ///
  /// In en, this message translates to:
  /// **'{n} MB'**
  String sizeMb(String n);

  /// No description provided for @sizeKb.
  ///
  /// In en, this message translates to:
  /// **'{n} KB'**
  String sizeKb(String n);

  /// No description provided for @sizeGb.
  ///
  /// In en, this message translates to:
  /// **'{n} GB'**
  String sizeGb(String n);

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @ok.
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get ok;

  /// No description provided for @close.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// No description provided for @delete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete;

  /// No description provided for @share.
  ///
  /// In en, this message translates to:
  /// **'Share'**
  String get share;

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @comingSoon.
  ///
  /// In en, this message translates to:
  /// **'Coming soon'**
  String get comingSoon;

  /// No description provided for @proBadge.
  ///
  /// In en, this message translates to:
  /// **'PRO'**
  String get proBadge;

  /// No description provided for @configAdvancedSection.
  ///
  /// In en, this message translates to:
  /// **'Advanced pipeline'**
  String get configAdvancedSection;

  /// No description provided for @configAdvancedTile.
  ///
  /// In en, this message translates to:
  /// **'Tile size'**
  String get configAdvancedTile;

  /// No description provided for @configAdvancedTileHint.
  ///
  /// In en, this message translates to:
  /// **'Smaller tiles use less memory. Auto picks by device.'**
  String get configAdvancedTileHint;

  /// No description provided for @configAdvancedOverlap.
  ///
  /// In en, this message translates to:
  /// **'Tile overlap'**
  String get configAdvancedOverlap;

  /// No description provided for @configAdvancedOverlapHint.
  ///
  /// In en, this message translates to:
  /// **'Higher overlap hides seams better but costs time.'**
  String get configAdvancedOverlapHint;

  /// No description provided for @configAdvancedThreads.
  ///
  /// In en, this message translates to:
  /// **'CPU threads'**
  String get configAdvancedThreads;

  /// No description provided for @configAdvancedCompute.
  ///
  /// In en, this message translates to:
  /// **'Compute'**
  String get configAdvancedCompute;

  /// No description provided for @configAdvancedComputeGpu.
  ///
  /// In en, this message translates to:
  /// **'GPU (Vulkan)'**
  String get configAdvancedComputeGpu;

  /// No description provided for @configAdvancedComputeCpu.
  ///
  /// In en, this message translates to:
  /// **'CPU'**
  String get configAdvancedComputeCpu;

  /// No description provided for @configAdvancedFormat.
  ///
  /// In en, this message translates to:
  /// **'Output format'**
  String get configAdvancedFormat;

  /// No description provided for @configAdvancedQuality.
  ///
  /// In en, this message translates to:
  /// **'JPEG quality'**
  String get configAdvancedQuality;

  /// No description provided for @configAdvancedCap.
  ///
  /// In en, this message translates to:
  /// **'Output size limit'**
  String get configAdvancedCap;

  /// No description provided for @configAdvancedCapHint.
  ///
  /// In en, this message translates to:
  /// **'Caps output megapixels to protect memory.'**
  String get configAdvancedCapHint;

  /// No description provided for @configAuto.
  ///
  /// In en, this message translates to:
  /// **'Auto'**
  String get configAuto;

  /// No description provided for @configMpUnit.
  ///
  /// In en, this message translates to:
  /// **'MP'**
  String get configMpUnit;

  /// No description provided for @configFormatSameAsSource.
  ///
  /// In en, this message translates to:
  /// **'Same as source'**
  String get configFormatSameAsSource;

  /// No description provided for @configSrBadge.
  ///
  /// In en, this message translates to:
  /// **'Super Resolution'**
  String get configSrBadge;

  /// No description provided for @configSrLosslessInfo.
  ///
  /// In en, this message translates to:
  /// **'No recompression: your original pixels are kept, the result is saved as lossless PNG.'**
  String get configSrLosslessInfo;

  /// No description provided for @configSrFeasibilityTitle.
  ///
  /// In en, this message translates to:
  /// **'Device check'**
  String get configSrFeasibilityTitle;

  /// No description provided for @configSrFeasibilityGreen.
  ///
  /// In en, this message translates to:
  /// **'Fits this device comfortably'**
  String get configSrFeasibilityGreen;

  /// No description provided for @configSrFeasibilityAmber.
  ///
  /// In en, this message translates to:
  /// **'Heavy but possible'**
  String get configSrFeasibilityAmber;

  /// No description provided for @configSrFeasibilityRed.
  ///
  /// In en, this message translates to:
  /// **'Too heavy for this device'**
  String get configSrFeasibilityRed;

  /// No description provided for @configSrFeasibilityMemory.
  ///
  /// In en, this message translates to:
  /// **'Estimated peak memory {mb} of {total} MB'**
  String configSrFeasibilityMemory(int mb, int total);

  /// No description provided for @configSrFeasibilityOutput.
  ///
  /// In en, this message translates to:
  /// **'Output {mp} MP'**
  String configSrFeasibilityOutput(int mp);

  /// No description provided for @configSrFeasibilityOverCap.
  ///
  /// In en, this message translates to:
  /// **'Output exceeds the {cap} MP limit for this device'**
  String configSrFeasibilityOverCap(int cap);

  /// No description provided for @configSrFeasibilitySlow.
  ///
  /// In en, this message translates to:
  /// **'May take a long time on this device'**
  String get configSrFeasibilitySlow;

  /// No description provided for @configSrFeasibilityLargeSource.
  ///
  /// In en, this message translates to:
  /// **'Very large source image'**
  String get configSrFeasibilityLargeSource;

  /// No description provided for @configSrConfirmAmberTitle.
  ///
  /// In en, this message translates to:
  /// **'Continue with Super Resolution?'**
  String get configSrConfirmAmberTitle;

  /// No description provided for @configSrConfirmAmberBody.
  ///
  /// In en, this message translates to:
  /// **'This photo is heavy for your device. It can take a while and may slow other apps down.'**
  String get configSrConfirmAmberBody;

  /// No description provided for @configSrBlockedTitle.
  ///
  /// In en, this message translates to:
  /// **'Super Resolution is too heavy here'**
  String get configSrBlockedTitle;

  /// No description provided for @configSrBlockedBody.
  ///
  /// In en, this message translates to:
  /// **'Try a smaller scale, or preview a crop first.'**
  String get configSrBlockedBody;

  /// No description provided for @configSrBlockedUseUpscale.
  ///
  /// In en, this message translates to:
  /// **'Use Upscale mode instead'**
  String get configSrBlockedUseUpscale;

  /// No description provided for @configPreviewSection.
  ///
  /// In en, this message translates to:
  /// **'Preview first'**
  String get configPreviewSection;

  /// No description provided for @configPreviewHint.
  ///
  /// In en, this message translates to:
  /// **'Pick a small area and upscale only that — see the quality before the long run.'**
  String get configPreviewHint;

  /// No description provided for @configPreviewPick.
  ///
  /// In en, this message translates to:
  /// **'Choose area'**
  String get configPreviewPick;

  /// No description provided for @configPreviewChange.
  ///
  /// In en, this message translates to:
  /// **'Change area'**
  String get configPreviewChange;

  /// No description provided for @configPreviewClear.
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get configPreviewClear;

  /// No description provided for @configPreviewRun.
  ///
  /// In en, this message translates to:
  /// **'Preview'**
  String get configPreviewRun;

  /// No description provided for @configPreviewTitle.
  ///
  /// In en, this message translates to:
  /// **'Preview result'**
  String get configPreviewTitle;

  /// No description provided for @configPreviewRunning.
  ///
  /// In en, this message translates to:
  /// **'Upscaling preview…'**
  String get configPreviewRunning;

  /// No description provided for @configPreviewBefore.
  ///
  /// In en, this message translates to:
  /// **'Original'**
  String get configPreviewBefore;

  /// No description provided for @configPreviewAfter.
  ///
  /// In en, this message translates to:
  /// **'Super Resolution'**
  String get configPreviewAfter;

  /// No description provided for @configPreviewFull.
  ///
  /// In en, this message translates to:
  /// **'Upscale full image'**
  String get configPreviewFull;

  /// No description provided for @configPreviewTime.
  ///
  /// In en, this message translates to:
  /// **'{n} s'**
  String configPreviewTime(int n);

  /// No description provided for @cropTitle.
  ///
  /// In en, this message translates to:
  /// **'Choose preview area'**
  String get cropTitle;

  /// No description provided for @cropHint.
  ///
  /// In en, this message translates to:
  /// **'Drag the box to pick what RealScale-Pro should preview.'**
  String get cropHint;

  /// No description provided for @cropApply.
  ///
  /// In en, this message translates to:
  /// **'Use this area'**
  String get cropApply;

  /// No description provided for @cropReset.
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get cropReset;

  /// No description provided for @settingsLanguageSystem.
  ///
  /// In en, this message translates to:
  /// **'System default'**
  String get settingsLanguageSystem;

  /// No description provided for @settingsLanguageHindi.
  ///
  /// In en, this message translates to:
  /// **'हिन्दी'**
  String get settingsLanguageHindi;

  /// No description provided for @settingsLanguagePick.
  ///
  /// In en, this message translates to:
  /// **'Choose language'**
  String get settingsLanguagePick;
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
      <String>['en', 'hi'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'hi':
      return AppLocalizationsHi();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
