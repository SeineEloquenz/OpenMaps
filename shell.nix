{
  system,
  nixpkgs,
}:

let
  # Android SDK versions are kept in sync by the `update-android-sdk` workflow from
  # github:SeineEloquenz/ci, which scans this file.
  buildToolsVersion = "37.0.0";
  buildToolsVersions = [ buildToolsVersion ];
  platformVersions = [ "37" ];

  pkgs = import nixpkgs {
    inherit system;

    config.allowUnfree = true;
    config.android_sdk.accept_license = true;
  };

  jdk = pkgs.jdk21;

  androidSdk = pkgs.androidenv.composeAndroidPackages {
    inherit buildToolsVersions platformVersions;
    includeNDK = false;
    includeEmulator = false;
  };
in
{
  default = pkgs.mkShell {
    packages = [
      jdk
      pkgs.gradle
      androidSdk.androidsdk
    ];

    env = {
      ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
      JAVA_HOME = "${jdk}";

      # The aapt2 in the AGP Maven artifact is a generic Linux binary that NixOS cannot run.
      GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk.androidsdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2";

      LOCAL_COMPOSE_KIT = "../compose-kit";
    };

    shellHook = ''
      cat > "$PWD/local.properties" <<EOF
      sdk.dir=${androidSdk.androidsdk}/libexec/android-sdk
      EOF
    '';
  };
}
