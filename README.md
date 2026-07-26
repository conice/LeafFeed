<div align="center">
  <a href="https://github.com/conice/LeafFeed">
    <img src="fastlane/metadata/android/en-US/images/icon.png" alt="LeafFeed Logo" width="80" height="80">
  </a>

  <h3>LeafFeed</h3>
  <p>A quiet, modern, and user-controlled RSS reader for Android</p>

  <p>
    <a href="https://github.com/conice/LeafFeed/stargazers"><img src="https://img.shields.io/github/stars/conice/LeafFeed?style=flat-square&logo=github&label=Stars&labelColor=black&color=ffcb47" alt="GitHub Stars"></a>
    <a href="https://github.com/conice/LeafFeed/graphs/contributors"><img src="https://img.shields.io/github/contributors/conice/LeafFeed?style=flat-square&logo=github&label=Contributors&labelColor=black" alt="Contributors"></a>
    <a href="https://github.com/conice/LeafFeed/releases"><img src="https://img.shields.io/github/downloads/conice/LeafFeed/total?style=flat-square&logo=github&label=Downloads&labelColor=black&color=369eff" alt="Downloads"></a>
    <a href="https://github.com/conice/LeafFeed/releases/latest"><img src="https://img.shields.io/github/v/release/conice/LeafFeed?style=flat-square&logo=android&label=Release&labelColor=black&color=3ddc84" alt="Latest Release"></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/conice/LeafFeed?style=flat-square&labelColor=black" alt="License"></a>
    <br />
    <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white&labelColor=black" alt="Android 8.0+">
    <img src="https://img.shields.io/badge/Material-You-6750a4?style=flat-square&logo=materialdesign&logoColor=white&labelColor=black" alt="Material You">
    <img src="https://img.shields.io/badge/Ads-None-2ea44f?style=flat-square&labelColor=black" alt="No Ads">
    <br />
    <br />
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/feeds.png" width="30%" alt="Feeds">
    &nbsp;&nbsp;
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/flow.png" width="30%" alt="Article list">
    &nbsp;&nbsp;
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/read.png" width="30%" alt="Reader">
  </p>
</div>

Your information feed should not force you to chase an algorithm. LeafFeed keeps subscriptions, articles, podcasts, and reading data on your own device and services, presenting them in a clear timeline focused on what you actually want to read.

## Getting Started

LeafFeed is under active development. Feedback and bug reports are welcome in [GitHub Issues](https://github.com/conice/LeafFeed/issues).

| Platform | Requirement | Source |
| :--- | :--- | :--- |
| Android | Android 8.0 / API 26 or later | [![GitHub Release](https://img.shields.io/badge/GitHub-Release-181717?style=for-the-badge&logo=github)](https://github.com/conice/LeafFeed/releases/latest) |
| Android | Latest CI test build | [![GitHub Actions](https://img.shields.io/badge/GitHub-Actions-2088ff?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/conice/LeafFeed/actions) |
| Source | Kotlin / Jetpack Compose | [![Source Code](https://img.shields.io/badge/Source-Code-ffcb47?style=for-the-badge&logo=git&logoColor=black)](https://github.com/conice/LeafFeed) |

> [!IMPORTANT]
>
> Android only permits an APK to update an installed app when both use the same package name and signing certificate. To keep receiving in-place updates, always install builds from the same release channel. If a version signed with a different certificate is already installed, back up your data before uninstalling it.

Star the repository to keep track of new versions and release notes.

## Features

### Your Own Information Hub

Subscribe to RSS and Atom feeds, then organize content with groups, unread status, stars, Read Later, and highlight rules. LeafFeed supports OPML migration, local accounts, and services compatible with the Fever or Google Reader APIs.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/feeds.png" width="32%" alt="Subscription management">
  &nbsp;&nbsp;
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/flow.png" width="32%" alt="Article timeline">
</p>

### Focused, Complete Reading

Parse full articles and customize fonts, text size, line height, alignment, images, and reading themes. Reading history, full-text search, saved searches, tags, excerpts, notes, and text-to-speech make information easy to revisit and organize.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/read.png" width="36%" alt="Article reader">
</p>

### AI When You Need It

Use an OpenAI-compatible service of your choice to summarize groups of headlines, individual articles, and subscription reports. Models, prompts, request timeouts, content scope, and whether article links are included remain under your control.

### More Than Text

LeafFeed supports background podcast playback, queues, playback speed, skip controls, progress tracking, automatic downloads, cache management, and transcripts, together with lock-screen, headset, and Android Auto media controls.

### Data You Can Take With You

Export and import preferences, tags, notes, saved searches, and article reading state. Subscriptions use OPML, while article rules have a separate backup format. Reading-data backups include integrity verification and remain compatible with older formats.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/settings.png" width="36%" alt="Settings and data management">
</p>

## Contributing

Issues and pull requests are welcome. Changes should account for local accounts, third-party accounts, background synchronization, data migration, and the Android lifecycle.

<details>
  <summary><strong>Development and builds</strong></summary>

The development environment requires JDK 17, Android SDK with Compile SDK 36, and the Gradle Wrapper included in this repository.

```bash
# GitHub-channel debug APK
./gradlew assembleGithubDebug

# Unit tests and Android Lint
./gradlew testGithubDebugUnitTest lintGithubDebug
```

Release builds require a persistent signing certificate configured through these environment variables:

```text
LEAFFEED_SIGNING_STORE_FILE
LEAFFEED_SIGNING_STORE_PASSWORD
LEAFFEED_SIGNING_KEY_ALIAS
LEAFFEED_SIGNING_KEY_PASSWORD
```

```bash
./gradlew assembleGithubRelease
```

Never commit keystores, passwords, API keys, or backups containing sensitive information.

</details>

## Code Signing Policy

GitHub Release APKs are signed with a persistent certificate held by the project maintainer. The keystore and passwords are supplied to GitHub Actions through Repository Secrets and are never stored in the repository. The workflow validates the keystore, alias, and credentials before packaging.

Anyone producing an independent build is responsible for signing it and safeguarding the certificate. APKs signed with different certificates cannot update one another, even when their package names and versions are identical.

## Privacy

LeafFeed contains no advertising or built-in analytics service. Accounts, subscriptions, articles, and preferences are stored locally by default.

- When synchronization is enabled, subscription and reading-state data is sent to the service configured by the user.
- When an AI feature is used, selected headlines or article content is sent directly to the configured OpenAI-compatible service.
- Background synchronization, notifications, and podcast playback may use the network, wake locks, and foreground services.
- Users should review the privacy policies of their content sources, synchronization services, and AI providers.

## License

LeafFeed is distributed under the [GNU General Public License v3.0](LICENSE). Distributions of modified versions must comply with the source-availability and license-preservation requirements of GPL-3.0. Third-party components and assets remain subject to their respective licenses.

## Upstream Acknowledgement

LeafFeed is a derivative project based on [Read You](https://github.com/Ashinch/ReadYou). We thank Read You author Ashinch and every upstream contributor for the design, code, and open-source foundation on which this project builds.

LeafFeed is not an official Read You release and is not affiliated with or endorsed by the upstream project or its maintainers. Issues caused by LeafFeed-specific features or modifications should be reported to [LeafFeed Issues](https://github.com/conice/LeafFeed/issues); upstream maintainers should not be expected to support this fork. Copyright in the original code and subsequent modifications remains with the respective contributors, and the project continues to be distributed under GPL-3.0.
