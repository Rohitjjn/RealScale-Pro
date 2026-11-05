// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Hindi (`hi`).
class AppLocalizationsHi extends AppLocalizations {
  AppLocalizationsHi([String locale = 'hi']) : super(locale);

  @override
  String get appName => 'RealScale-Pro';

  @override
  String get appTagline => 'ऑफ़लाइन AI फ़ोटो अपस्केलर';

  @override
  String get guideSkip => 'छोड़ें';

  @override
  String get guideNext => 'आगे';

  @override
  String get guideGetStarted => 'शुरू करें';

  @override
  String get guide1Title => 'छोटी फ़ोटो को बड़ा बनाएं';

  @override
  String get guide1Body =>
      'पुरानी, छोटी या कंप्रेस्ड फ़ोटो को शार्प हाई-रेज़ोल्यूशन इमेज में बदलें — आपके फ़ोन पर ही, 5× तक।';

  @override
  String get guide2Title => 'प्राइवेसी के साथ बना';

  @override
  String get guide2Body =>
      'सब कुछ इसी डिवाइस पर चलता है। आपकी फ़ोटो कभी बाहर नहीं जाती। कोई अकाउंट नहीं, कोई क्लाउड नहीं, कोई अपलोड नहीं।';

  @override
  String get guide3Title => 'Default में एक टैप';

  @override
  String get guide3Body =>
      'RealScale-Pro आपकी फ़ोटो के लिए सबसे अच्छा मॉडल और सेटिंग्स खुद चुनता है। बस Upscale दबाएं।';

  @override
  String get guide4Title => 'अपनी क्वालिटी चुनें';

  @override
  String get guide4Body =>
      'पूरा नियंत्रण चाहिए तो Manual पर जाकर मॉडल, स्केल और फेस रीस्टोरेशन खुद चुनें।';

  @override
  String get permissionPhotosTitle => 'फ़ोटो एक्सेस की अनुमति दें?';

  @override
  String get permissionPhotosBody =>
      'RealScale-Pro हाल की फ़ोटो दिखाने और तैयार नतीजों को आपकी गैलरी में सेव करने के लिए फ़ोटो लाइब्रेरी का उपयोग करता है।';

  @override
  String get permissionPhotosAllow => 'फ़ोटो एक्सेस की अनुमति दें';

  @override
  String get permissionPhotosNotNow => 'अभी नहीं';

  @override
  String get permissionPhotosDeniedCardTitle => 'फ़ोटो एक्सेस की अनुमति दें';

  @override
  String get permissionPhotosDeniedCardBody =>
      'फ़ोटो एक्सेस के बिना भी आप Files, Camera या सिस्टम पिकर से इम्पोर्ट कर सकते हैं।';

  @override
  String get permissionPhotosOpenSettings => 'सेटिंग्स खोलें';

  @override
  String get permissionNotificationsTitle => 'अपस्केलिंग के दौरान सूचनाएं पाएं';

  @override
  String get permissionNotificationsBody =>
      'बैकग्राउंड में अपस्केलिंग चलने पर एक सूचना प्रगति दिखाती है।';

  @override
  String get permissionNotificationsAllow => 'सूचनाओं की अनुमति दें';

  @override
  String get permissionNotificationsNotNow => 'अभी नहीं';

  @override
  String get permissionSelectedPhotos =>
      'केवल चुनी गई फ़ोटो — सिस्टम सेटिंग्स में और जोड़ सकते हैं।';

  @override
  String get deviceCheckTitle => 'आपका डिवाइस जांचा जा रहा है';

  @override
  String get deviceCheckBody =>
      'आपके फ़ोन के लिए सही क्वालिटी चुनने के लिए हम एक छोटा सैंपल मापते हैं। इसमें एक पल लगेगा।';

  @override
  String get deviceCheckDone => 'आपका डिवाइस तैयार है —';

  @override
  String get deviceCheckTierLight => 'लाइट क्वालिटी';

  @override
  String get deviceCheckTierStandard => 'स्टैंडर्ड क्वालिटी';

  @override
  String get deviceCheckTierPowerful => 'पावरफ़ुल क्वालिटी';

  @override
  String get setupTitle => 'सेटअप';

  @override
  String get setupStarterPack => 'स्टार्टर पैक';

  @override
  String get setupStarterSubtitle => 'Default क्वालिटी के लिए ज़रूरी मॉडल';

  @override
  String get setupIncluded => 'शामिल है';

  @override
  String get setupDownload => 'डाउनलोड करें';

  @override
  String get setupDownloading => 'डाउनलोड हो रहा है…';

  @override
  String get setupPaused => 'रुका हुआ';

  @override
  String get setupInstalled => 'इंस्टॉल है';

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
  String get setupMobileDataTitle => 'मोबाइल डेटा उपयोग करें?';

  @override
  String get setupMobileDataBody =>
      'आप Wi-Fi पर नहीं हैं। डाउनलोड केवल Wi-Fi के लिए सेट हैं। इस डाउनलोड के लिए मोबाइल डेटा की अनुमति दें?';

  @override
  String get setupAllowMobileData => 'इस बार अनुमति दें';

  @override
  String get setupKeepWifiOnly => 'Wi-Fi-ओनली ही रखें';

  @override
  String get setupContinue => 'जारी रखें';

  @override
  String get setupResumeHint =>
      'ऐप बंद होने पर डाउनलोड अपने आप फिर से शुरू होते हैं।';

  @override
  String get homeGreeting => 'RealScale-Pro';

  @override
  String get homeSettingsTooltip => 'सेटिंग्स';

  @override
  String get homeRecents => 'हाल की फ़ोटो';

  @override
  String get homeSeeAll => 'सभी देखें';

  @override
  String get homeRecentsEmptyTitle => 'अभी कोई फ़ोटो नहीं';

  @override
  String get homeRecentsEmptyBody => 'शुरू करने के लिए एक फ़ोटो इम्पोर्ट करें।';

  @override
  String get homeImportSection => 'इम्पोर्ट';

  @override
  String get homeImportGallery => 'गैलरी';

  @override
  String get homeImportFiles => 'फ़ाइलें';

  @override
  String get homeImportCamera => 'कैमरा';

  @override
  String get homeImportMore => 'और';

  @override
  String get homeOptionsSection => 'विकल्प';

  @override
  String get homeModeUpscaleTitle => 'अपस्केल';

  @override
  String get homeModeUpscaleBody => 'तेज़, स्मार्ट, डिफ़ॉल्ट क्वालिटी';

  @override
  String get homeModeSrTitle => 'सुपर रेज़ोल्यूशन';

  @override
  String get homeModeSrBody => 'अधिकतम क्वालिटी, बिना री-कंप्रेशन';

  @override
  String get homeModeSrBadge => 'PRO';

  @override
  String get homeOptionMyUpscales => 'मेरे अपस्केल';

  @override
  String get homeOptionModelManager => 'मॉडल मैनेजर';

  @override
  String get homeOptionSettings => 'सेटिंग्स';

  @override
  String get homeOptionVideo => 'वीडियो अपस्केल';

  @override
  String get homeVideoComingSoonTitle => 'वीडियो अपस्केलिंग आ रही है';

  @override
  String get homeVideoComingSoonBody =>
      'हम वीडियो सपोर्ट पूरा कर रहे हैं। फ़ोटो पहले से बढ़िया चलती हैं — एक आज़माएं!';

  @override
  String get homeSrWarningTitle => 'सुपर रेज़ोल्यूशन शुरू करने से पहले';

  @override
  String homeSrWarningBody(Object tier) {
    return 'सुपर रेज़ोल्यूशन हर ओरिजिनल पिक्सेल बनाए रखता है और काफ़ी ज़्यादा समय ले सकता है। $tier डिवाइस पर हम Upscale से शुरू करने की सलाह देते हैं।';
  }

  @override
  String get homeSrWarningChipFit => 'आपके डिवाइस के लिए ठीक';

  @override
  String get homeSrWarningChipHeavy => 'इस डिवाइस पर भारी';

  @override
  String get homeSrWarningUseUpscale => 'इसके बजाय Upscale उपयोग करें';

  @override
  String get homeSrWarningContinue => 'फिर भी जारी रखें';

  @override
  String get homePermissionCard =>
      'हाल की फ़ोटो देखने के लिए फ़ोटो एक्सेस की अनुमति दें';

  @override
  String get galleryTitle => 'सभी फ़ोटो';

  @override
  String get galleryEmpty => 'कोई फ़ोटो नहीं मिली';

  @override
  String get galleryPermissionNeeded =>
      'गैलरी दिखाने के लिए फ़ोटो एक्सेस चाहिए';

  @override
  String get galleryAllow => 'अनुमति दें';

  @override
  String get viewerUpscale => 'अपस्केल';

  @override
  String get viewerDetails => 'विवरण';

  @override
  String get viewerShare => 'शेयर';

  @override
  String get viewerDelete => 'हटाएं';

  @override
  String get viewerModeChipUpscale => 'अपस्केल';

  @override
  String get viewerModeChipSr => 'सुपर रेज़';

  @override
  String get detailsResolution => 'रेज़ोल्यूशन';

  @override
  String get detailsSize => 'फ़ाइल साइज़';

  @override
  String get detailsModified => 'बदला गया';

  @override
  String get moreSheetTitle => 'इससे इम्पोर्ट करें';

  @override
  String get moreClipboard => 'क्लिपबोर्ड से पेस्ट करें';

  @override
  String get moreClipboardEmpty => 'क्लिपबोर्ड में कोई इमेज नहीं मिली';

  @override
  String get moreSystemPicker => 'सिस्टम फ़ोटो पिकर';

  @override
  String get moreCancel => 'रद्द करें';

  @override
  String get configTitle => 'अपस्केल';

  @override
  String get configPreviewSwap => 'तुलना करें';

  @override
  String get configLevelDefault => 'Default';

  @override
  String get configLevelManual => 'Manual';

  @override
  String get configLevelAdvanced => 'Advanced';

  @override
  String get configAdvancedComingSoon =>
      'Advanced सेटिंग्स अगले वर्ज़न में आएंगी।';

  @override
  String get configDetectedPhoto => 'फ़ोटो';

  @override
  String get configDetectedAnime => 'एनिमे';

  @override
  String get configDetectedPrefix => 'पहचाना गया:';

  @override
  String configScaleLabel(int scale) {
    return '$scale× · Auto';
  }

  @override
  String get configNotSure => 'पक्का नहीं — बदलें?';

  @override
  String get configChangeLink => 'बदलें';

  @override
  String get configManualComingSoon =>
      'Manual कॉन्फ़िगरेशन इसी वर्ज़न में आ रहा है।';

  @override
  String get configFaceToggle => 'चेहरे रीस्टोर करें';

  @override
  String get configFaceHint =>
      'चेहरे ढूंढकर अपस्केलिंग के बाद उन्हें रीस्टोर करता है।';

  @override
  String configFaceDetectorHint(int count) {
    return '$count चेहरे पहचाने गए';
  }

  @override
  String get configFaceNone => 'कोई चेहरा नहीं पहचाना गया';

  @override
  String get configFaceUnavailable => 'फेस रीस्टोरेशन के लिए Face Pack चाहिए।';

  @override
  String configFaceDownload(Object size) {
    return 'Face Pack डाउनलोड करें ($size)';
  }

  @override
  String configOutputSize(int width, int height) {
    return '$width × $height px';
  }

  @override
  String configEta(String duration) {
    return 'लगभग $duration';
  }

  @override
  String get configUpscaleButton => 'अपस्केल';

  @override
  String get configHeavyTitle => 'इस डिवाइस पर भारी मॉडल';

  @override
  String configHeavyBody(String model) {
    return '$model आपके डिवाइस के लिए बड़ा है — ज़्यादा समय ले सकता है या अटक सकता है। फिर भी जारी रखें?';
  }

  @override
  String get configHeavyContinue => 'जारी रखें';

  @override
  String get configHeavyCancel => 'रद्द करें';

  @override
  String get configManualSection => 'मॉडल';

  @override
  String get configScaleSection => 'स्केल';

  @override
  String get configScaleExtraStep => '5× में एक अतिरिक्त रीसाइज़ स्टेप लगता है';

  @override
  String get configModelRecommended => 'सुझाया गया';

  @override
  String get configModelDeviceFit => 'डिवाइस के लिए ठीक';

  @override
  String get configModelHeavy => 'भारी';

  @override
  String configModelDownload(Object size) {
    return 'डाउनलोड करें ($size)';
  }

  @override
  String configModelDownloading(int percent) {
    return 'डाउनलोड हो रहा है… $percent';
  }

  @override
  String get configModelUnavailable => 'उपलब्ध नहीं';

  @override
  String get processingTitle => 'अपस्केल हो रहा है';

  @override
  String get processingStagePreparing => 'इमेज तैयार हो रही है';

  @override
  String get processingStageUpscaling => 'टाइल अपस्केल हो रहे हैं';

  @override
  String get processingStageFaces => 'चेहरे रीस्टोर हो रहे हैं';

  @override
  String get processingStageSaving => 'नतीजा सेव हो रहा है';

  @override
  String processingTileCount(int index, int total) {
    return '$index / $total टाइल';
  }

  @override
  String get processingEtaEstimating => 'अनुमान लग रहा है…';

  @override
  String processingEta(String duration) {
    return '$duration बाकी';
  }

  @override
  String get processingCancel => 'रद्द करें';

  @override
  String get processingCancelConfirmTitle => 'यह अपस्केल रद्द करें?';

  @override
  String get processingCancelConfirmBody =>
      'प्रगति हट जाएगी और अस्थायी फ़ाइलें मिटा दी जाएंगी।';

  @override
  String get processingCancelYes => 'अपस्केल रद्द करें';

  @override
  String get processingCancelNo => 'जारी रखें';

  @override
  String get processingBackground => 'बैकग्राउंड में जारी रखें';

  @override
  String get processingBackgroundNote =>
      'आप यह स्क्रीन छोड़ सकते हैं — एक सूचना प्रगति दिखाएगी।';

  @override
  String get processingTip1 =>
      'सुझाव: Default क्वालिटी एक टैप है और हर फ़ोटो पर चलती है।';

  @override
  String get processingTip2 =>
      'सुझाव: सुपर रेज़ोल्यूशन ओरिजिनल क्वालिटी रखता है, पर समय ज़्यादा लगता है।';

  @override
  String get processingTip3 =>
      'सुझाव: ग्रुप फ़ोटो अपस्केल करने के बाद चेहरे रीस्टोर करें।';

  @override
  String get processingFailedTitle => 'अपस्केल पूरा नहीं हुआ';

  @override
  String get processingFailedBody =>
      'बड़ी फ़ोटो या कम मेमोरी में ऐसा हो सकता है।';

  @override
  String get processingTrySafer => 'सुरक्षित सेटिंग्स आज़माएं';

  @override
  String get processingCancelled => 'रद्द किया गया';

  @override
  String get processingSlowNote =>
      'आम तौर से धीमा चल रहा है — बैटरी सेवर या गर्मी डिवाइस को धीमा कर सकते हैं।';

  @override
  String get resultTitle => 'नतीजा';

  @override
  String get resultSavedTo => 'गैलरी में सेव हुआ';

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
    return '$seconds से';
  }

  @override
  String get resultBefore => 'पहले';

  @override
  String get resultAfter => 'बाद में';

  @override
  String get resultShare => 'शेयर';

  @override
  String get resultUpscaleAgain => 'दोबारा अपस्केल करें';

  @override
  String get resultNewImage => 'नई इमेज';

  @override
  String get resultMore => 'और';

  @override
  String get resultHoldToCompare => 'तुलना के लिए दबाए रखें';

  @override
  String resultFacesRestored(int count) {
    return '$count चेहरे रीस्टोर हुए';
  }

  @override
  String get resultRecoveredNote =>
      'इस डिवाइस के लिए बदली सेटिंग्स के साथ पूरा हुआ';

  @override
  String get resultTempInputNote =>
      'बड़ा इनपुट क्वालिटी-90 कॉपी से प्रोसेस हुआ (ओरिजिनल अनछुआ)';

  @override
  String get myUpscalesTitle => 'मेरे अपस्केल';

  @override
  String get myUpscalesEmptyTitle => 'अभी कुछ अपस्केल नहीं हुआ';

  @override
  String get myUpscalesEmptyBody => 'तैयार इमेज यहां अपने आप दिखती हैं।';

  @override
  String get myUpscalesDelete => 'हटाएं';

  @override
  String get myUpscalesShare => 'शेयर';

  @override
  String get myUpscalesOpen => 'खोलें';

  @override
  String get myUpscalesSelect => 'चुनें';

  @override
  String myUpscalesSelectedCount(int count) {
    return '$count चुने गए';
  }

  @override
  String myUpscalesDeleteConfirm(int count) {
    return '$count इमेज हटाएं? इन्हें आपकी गैलरी फ़ोल्डर से भी हटा दिया जाएगा।';
  }

  @override
  String get myUpscalesDeleteConfirmTitle => 'अपस्केल की गई इमेज हटाएं?';

  @override
  String get modelManagerTitle => 'मॉडल मैनेजर';

  @override
  String modelManagerStorage(String used, String total) {
    return '$total में से $used उपयोग में';
  }

  @override
  String modelManagerFreeSpace(String free) {
    return '$free खाली';
  }

  @override
  String get modelManagerPackStarter => 'स्टार्टर पैक';

  @override
  String get modelManagerPackPro => 'प्रो पैक';

  @override
  String get modelManagerPackFace => 'फेस पैक';

  @override
  String get modelManagerPackUltra => 'अल्ट्रा पैक (जल्द आ रहा है)';

  @override
  String get modelManagerPackCommunity => 'कम्युनिटी पैक';

  @override
  String get modelManagerFilterAll => 'सभी';

  @override
  String get modelManagerFilterPhoto => 'फ़ोटो';

  @override
  String get modelManagerFilterAnime => 'एनिमे';

  @override
  String get modelManagerFilterFace => 'फेस';

  @override
  String get modelManagerDownloadAll => 'पैक डाउनलोड करें';

  @override
  String get modelManagerDeleteAll => 'पैक हटाएं';

  @override
  String get modelManagerUpdate => 'अपडेट उपलब्ध';

  @override
  String get modelManagerStatusInstalled => 'इंस्टॉल है';

  @override
  String get modelManagerStatusNotInstalled => 'इंस्टॉल नहीं है';

  @override
  String get modelManagerStatusDownloading => 'डाउनलोड हो रहा है…';

  @override
  String get modelManagerStatusPaused => 'रुका हुआ';

  @override
  String get modelManagerStatusError =>
      'त्रुटि — फिर कोशिश करने के लिए टैप करें';

  @override
  String get modelManagerStatusUnavailable => 'उपलब्ध नहीं';

  @override
  String get modelManagerUnavailableChecksum => 'सोर्स सत्यापन बाकी';

  @override
  String get modelManagerUnavailableLicense =>
      'लाइसेंस उपयोग की अनुमति नहीं देता';

  @override
  String get modelManagerUnavailableSoon => 'अगले वर्ज़न में आ रहा है';

  @override
  String get modelManagerLicense => 'लाइसेंस';

  @override
  String get modelManagerSource => 'सोर्स';

  @override
  String get modelManagerDeleteFallback =>
      'इस फ़ोटो टाइप के लिए डिफ़ॉल्ट मॉडल फिर उपयोग होगा।';

  @override
  String get modelManagerDeleteConfirmTitle => 'यह मॉडल हटाएं?';

  @override
  String get modelManagerDeleteConfirmBody =>
      'आप इसे कभी भी दोबारा डाउनलोड कर सकते हैं।';

  @override
  String modelManagerSpeed(String speed) {
    return '$speed/से';
  }

  @override
  String modelManagerSize(String size) {
    return '$size';
  }

  @override
  String get settingsTitle => 'सेटिंग्स';

  @override
  String get settingsAppearance => 'दिखावट';

  @override
  String get settingsTheme => 'थीम';

  @override
  String get settingsThemeSystem => 'सिस्टम';

  @override
  String get settingsThemeLight => 'लाइट';

  @override
  String get settingsThemeDark => 'डार्क';

  @override
  String get settingsDynamicColor => 'डाइनैमिक कलर';

  @override
  String get settingsDynamicColorBody =>
      'ऐप के रंग आपकी वॉलपेपर से मिलाएं (Material You)।';

  @override
  String get settingsFeel => 'अनुभव';

  @override
  String get settingsHaptics => 'हैप्टिक्स';

  @override
  String get settingsKeepAwake => 'स्क्रीन जागती रखें';

  @override
  String get settingsKeepAwakeBody =>
      'अपस्केलिंग के दौरान डिवाइस सोने से रोकता है।';

  @override
  String get settingsLanguage => 'भाषा';

  @override
  String get settingsLanguageEnglish => 'English';

  @override
  String get settingsLanguageMore => 'और भाषाएं आ रही हैं';

  @override
  String get settingsDefaults => 'अपस्केलिंग डिफ़ॉल्ट';

  @override
  String get settingsDefaultMode => 'डिफ़ॉल्ट मोड';

  @override
  String get settingsCompute => 'कंप्यूट प्राथमिकता';

  @override
  String get settingsComputeAuto => 'ऑटो';

  @override
  String get settingsComputeGpu => 'GPU';

  @override
  String get settingsComputeCpu => 'CPU';

  @override
  String get settingsThrottle => 'बैटरी या गर्मी पर धीमा करें';

  @override
  String get settingsOutput => 'आउटपुट';

  @override
  String get settingsOutputFormat => 'फ़ॉर्मैट';

  @override
  String get settingsOutputFormatSame => 'सोर्स जैसा ही';

  @override
  String get settingsOutputMetadata => 'फ़ोटो मेटाडेटा रखें';

  @override
  String get settingsOutputNaming => 'फ़ाइल नाम';

  @override
  String get settingsOutputNamingRealscale => 'RealScale_<name>';

  @override
  String get settingsOutputNamingOriginal => 'ओरिजिनल नाम रखें';

  @override
  String get settingsDownloads => 'डाउनलोड';

  @override
  String get settingsWifiOnly => 'केवल Wi-Fi पर डाउनलोड करें';

  @override
  String get settingsStorage => 'स्टोरेज';

  @override
  String get settingsClearCache => 'अस्थायी फ़ाइलें मिटाएं';

  @override
  String get settingsClearCacheBody => 'इंस्टॉल किए मॉडल बने रहते हैं।';

  @override
  String get settingsClearCacheDone => 'अस्थायी फ़ाइलें मिटा दी गईं';

  @override
  String get settingsRerunDeviceCheck => 'डिवाइस जांच दोबारा चलाएं';

  @override
  String get settingsDeviceTier => 'डिवाइस प्रोफ़ाइल';

  @override
  String get settingsPrivacy => 'प्राइवेसी';

  @override
  String get settingsPrivacyBody =>
      'RealScale-Pro पूरी तरह ऑफ़लाइन चलता है। फ़ोटो कभी इस डिवाइस से बाहर नहीं जाती।';

  @override
  String get settingsAnonymousStats => 'गुमनाम उपयोग आंकड़े साझा करें';

  @override
  String get settingsCrashReports => 'क्रैश रिपोर्ट भेजें';

  @override
  String get settingsSupport => 'सहायता';

  @override
  String get settingsReplayGuide => 'परिचय दोबारा देखें';

  @override
  String get settingsFaq => 'सामान्य सवाल और सुझाव';

  @override
  String get settingsAbout => 'RealScale-Pro के बारे में';

  @override
  String get settingsLicenses => 'ओपन-सोर्स लाइसेंस';

  @override
  String get settingsPrivacyStatement => 'प्राइवेसी वक्तव्य';

  @override
  String get settingsModelCredits => 'मॉडल क्रेडिट';

  @override
  String get settingsCreditsBody =>
      'अपस्केलिंग इंजन Real-ESRGAN (BSD-3), Real-CUGAN (MIT) और ncnn (BSD-3) पर चलता है। फेस रीस्टोरेशन GFPGAN (Apache-2.0) उपयोग करता है।';

  @override
  String get aboutVersion => 'वर्ज़न';

  @override
  String get aboutBuiltWith => 'Flutter · ncnn इंजन से बना';

  @override
  String get privacyTitle => 'प्राइवेसी वक्तव्य';

  @override
  String get privacyBody1 =>
      'RealScale-Pro हर फ़ोटो को पूरी तरह इसी डिवाइस पर प्रोसेस करता है। कोई फ़ोटो, कोई नतीजा और कोई निजी डेटा कभी अपलोड नहीं होता।';

  @override
  String get privacyBody2 =>
      'ऐप नेटवर्क का उपयोग सिर्फ़ एक काम के लिए करता है: आपके कहने पर AI मॉडल डाउनलोड करना। इंस्टॉल से पहले हर मॉडल डाउनलोड की सोर्स और फ़ाइल चेकसम जांची जाती है।';

  @override
  String get privacyBody3 =>
      'कोई अकाउंट नहीं बनता, कोई एनालिटिक्स प्रोफ़ाइल नहीं बनती, और कुछ भी साझा नहीं होता। वैकल्पिक गुमनाम आंकड़े और क्रैश रिपोर्ट डिफ़ॉल्ट रूप से बंद रहते हैं और केवल तकनीकी डेटा भेजते हैं — कभी भी इमेज नहीं।';

  @override
  String get faqTitle => 'सामान्य सवाल और सुझाव';

  @override
  String get faqQ1 => 'पहला अपस्केल ज़्यादा समय क्यों लेता है?';

  @override
  String get faqA1 =>
      'मॉडल एक बार लोड होकर तैयार रहता है। बाद के अपस्केल काफ़ी तेज़ होते हैं।';

  @override
  String get faqQ2 => 'नतीजे कहां सेव होते हैं?';

  @override
  String get faqA2 =>
      'आपकी गैलरी में RealScale-Pro फ़ोल्डर के अंदर, और ऐप के My Upscales में।';

  @override
  String get faqQ3 => 'क्या मेरी फ़ोटो कहीं अपलोड होती है?';

  @override
  String get faqA3 =>
      'कभी नहीं। अपस्केलिंग पूरी तरह ऑफ़लाइन, इसी डिवाइस पर होती है।';

  @override
  String get faqFeedbackSubject => 'RealScale-Pro सुझाव';

  @override
  String get faqFeedbackBody =>
      'बताएं क्या अच्छा चलता है, क्या नहीं, या आगे क्या चाहिए।';

  @override
  String get licensesTitle => 'ओपन-सोर्स लाइसेंस';

  @override
  String get errorGenericTitle => 'कुछ गड़बड़ हो गई';

  @override
  String get errorGenericBody => 'कृपया दोबारा कोशिश करें।';

  @override
  String get errorRetry => 'फिर कोशिश करें';

  @override
  String get errorNoPhotos => 'कोई फ़ोटो उपलब्ध नहीं';

  @override
  String get errorImageTooLarge => 'यह इमेज इस डिवाइस के लिए बहुत बड़ी है';

  @override
  String get errorOutOfMemory => 'मेमोरी कम है — सुरक्षित सेटिंग्स आज़माएं';

  @override
  String get errorDiskFull => 'पर्याप्त खाली स्टोरेज नहीं है';

  @override
  String get errorSaveFailed => 'नतीजा सेव नहीं हो सका';

  @override
  String get errorNetwork => 'नेटवर्क उपलब्ध नहीं — कनेक्शन जांचें';

  @override
  String get errorChecksum => 'डाउनलोड सत्यापन में फेल हुआ, फिर कोशिश होगी';

  @override
  String get loading => 'लोड हो रहा है…';

  @override
  String durationSeconds(int n) {
    return '$n से';
  }

  @override
  String durationMinutes(int n) {
    return '$n मिनट';
  }

  @override
  String durationMinutesSeconds(int m, int s) {
    return '$m मिनट $s से';
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
  String get cancel => 'रद्द करें';

  @override
  String get ok => 'ठीक है';

  @override
  String get close => 'बंद करें';

  @override
  String get delete => 'हटाएं';

  @override
  String get share => 'शेयर';

  @override
  String get retry => 'फिर कोशिश करें';

  @override
  String get comingSoon => 'जल्द आ रहा है';

  @override
  String get proBadge => 'PRO';

  @override
  String get configAdvancedSection => 'Advanced पाइपलाइन';

  @override
  String get configAdvancedTile => 'टाइल साइज़';

  @override
  String get configAdvancedTileHint =>
      'छोटी टाइल कम मेमोरी लेती हैं। Auto डिवाइस के हिसाब से चुनता है।';

  @override
  String get configAdvancedOverlap => 'टाइल ओवरलैप';

  @override
  String get configAdvancedOverlapHint =>
      'ज़्यादा ओवरलैप सीवन अच्छे से छिपाता है, पर समय बढ़ाता है।';

  @override
  String get configAdvancedThreads => 'CPU थ्रेड';

  @override
  String get configAdvancedCompute => 'कंप्यूट';

  @override
  String get configAdvancedComputeGpu => 'GPU (Vulkan)';

  @override
  String get configAdvancedComputeCpu => 'CPU';

  @override
  String get configAdvancedFormat => 'आउटपुट फ़ॉर्मैट';

  @override
  String get configAdvancedQuality => 'JPEG क्वालिटी';

  @override
  String get configAdvancedCap => 'आउटपुट साइज़ सीमा';

  @override
  String get configAdvancedCapHint =>
      'मेमोरी बचाने के लिए आउटपुट मेगापिक्सल सीमित करता है।';

  @override
  String get configAuto => 'ऑटो';

  @override
  String get configMpUnit => 'MP';

  @override
  String get configFormatSameAsSource => 'सोर्स जैसा ही';

  @override
  String get configSrBadge => 'सुपर रेज़ोल्यूशन';

  @override
  String get configSrLosslessInfo =>
      'कोई री-कंप्रेशन नहीं: आपके ओरिजिनल पिक्सेल बने रहते हैं, नतीजा लॉसलेस PNG में सेव होता है।';

  @override
  String get configSrFeasibilityTitle => 'डिवाइस जांच';

  @override
  String get configSrFeasibilityGreen => 'इस डिवाइस पर आराम से चलेगा';

  @override
  String get configSrFeasibilityAmber => 'भारी, पर संभव है';

  @override
  String get configSrFeasibilityRed => 'इस डिवाइस के लिए बहुत भारी';

  @override
  String configSrFeasibilityMemory(int mb, int total) {
    return 'अनुमानित चरम मेमोरी $total MB में से $mb MB';
  }

  @override
  String configSrFeasibilityOutput(int mp) {
    return 'आउटपुट $mp MP';
  }

  @override
  String configSrFeasibilityOverCap(int cap) {
    return 'आउटपुट इस डिवाइस की $cap MP सीमा से ज़्यादा है';
  }

  @override
  String get configSrFeasibilitySlow => 'इस डिवाइस पर बहुत समय लग सकता है';

  @override
  String get configSrFeasibilityLargeSource => 'बहुत बड़ी सोर्स इमेज';

  @override
  String get configSrConfirmAmberTitle => 'सुपर रेज़ोल्यूशन जारी रखें?';

  @override
  String get configSrConfirmAmberBody =>
      'यह फ़ोटो आपके डिवाइस के लिए भारी है। इसमें समय लग सकता है और दूसरे ऐप धीमे हो सकते हैं।';

  @override
  String get configSrBlockedTitle => 'यहां सुपर रेज़ोल्यूशन बहुत भारी है';

  @override
  String get configSrBlockedBody =>
      'छोटा स्केल आज़माएं, या पहले एक क्रॉप का प्रीव्यू देखें।';

  @override
  String get configSrBlockedUseUpscale => 'इसके बजाय Upscale मोड उपयोग करें';

  @override
  String get configPreviewSection => 'पहले प्रीव्यू';

  @override
  String get configPreviewHint =>
      'छोटा हिस्सा चुनकर सिर्फ़ उतना ही अपस्केल करें — लंबे रन से पहले क्वालिटी देख लें।';

  @override
  String get configPreviewPick => 'हिस्सा चुनें';

  @override
  String get configPreviewChange => 'हिस्सा बदलें';

  @override
  String get configPreviewClear => 'साफ़ करें';

  @override
  String get configPreviewRun => 'प्रीव्यू';

  @override
  String get configPreviewTitle => 'प्रीव्यू नतीजा';

  @override
  String get configPreviewRunning => 'प्रीव्यू अपस्केल हो रहा है…';

  @override
  String get configPreviewBefore => 'ओरिजिनल';

  @override
  String get configPreviewAfter => 'सुपर रेज़ोल्यूशन';

  @override
  String get configPreviewFull => 'पूरी इमेज अपस्केल करें';

  @override
  String configPreviewTime(int n) {
    return '$n से';
  }

  @override
  String get cropTitle => 'प्रीव्यू हिस्सा चुनें';

  @override
  String get cropHint =>
      'बॉक्स खींचकर चुनें कि RealScale-Pro क्या प्रीव्यू करे।';

  @override
  String get cropApply => 'यही हिस्सा उपयोग करें';

  @override
  String get cropReset => 'रीसेट करें';

  @override
  String get settingsLanguageSystem => 'सिस्टम डिफ़ॉल्ट';

  @override
  String get settingsLanguageHindi => 'हिन्दी';

  @override
  String get settingsLanguagePick => 'भाषा चुनें';
}
