// Xcode 26/27 workaround: the Clang dependency scanner requires every
// module map referenced by -fmodule-map-file flags to exist before the
// pod that produces it is built, so device builds fail with
// "EXConstants.modulemap not found" / "could not build module
// '_DarwinFoundation1'". The fix is disabling explicit modules on every
// target — the pods project AND the app project. Settings must live in
// the .xcodeproj files; .xcconfig-only values get overridden by Xcode
// defaults (see facebook/flutter#183210-style reports).
// Lives outside ios/ so `expo prebuild` keeps applying it.

const { withXcodeProject, withPodfile } = require('expo/config-plugins');

const SETTINGS = {
  SWIFT_ENABLE_EXPLICIT_MODULES: 'NO',
  CLANG_ENABLE_EXPLICIT_MODULES: 'NO',
};

const withXcode27ModuleWorkaround = (config) => {
  config = withXcodeProject(config, (cfg) => {
    const section = cfg.modResults.pbxXCBuildConfigurationSection();
    for (const key of Object.keys(section)) {
      const buildConfig = section[key];
      if (buildConfig && typeof buildConfig === 'object' && buildConfig.buildSettings) {
        Object.assign(buildConfig.buildSettings, SETTINGS);
      }
    }
    return cfg;
  });

  return withPodfile(config, (cfg) => {
    if (!cfg.modResults.contents.includes('SWIFT_ENABLE_EXPLICIT_MODULES')) {
      // CocoaPods only allows one post_install hook, so the settings go
      // inside Expo's existing block. Belt and braces for the Pods side;
      // the app project side is patched above.
      cfg.modResults.contents = cfg.modResults.contents.replace(
        /^(  post_install do \|installer\|[\s\S]*?\n)(  end\n)/m,
        [
          '$1  # [with-xcode27-module-workaround] opt pods out of explicit modules;',
          '  # without this, device builds fail with ',
          "  # \"could not build module '_DarwinFoundation1'\".",
          '  installer.pods_project.targets.each do |target|',
          '    target.build_configurations.each do |build_config|',
          "      build_config.build_settings['SWIFT_ENABLE_EXPLICIT_MODULES'] = 'NO'",
          "      build_config.build_settings['CLANG_ENABLE_EXPLICIT_MODULES'] = 'NO'",
          '    end',
          '  end',
          '',
        ].join('\n') + '$2',
      );
    }
    return cfg;
  });
};

module.exports = withXcode27ModuleWorkaround;
