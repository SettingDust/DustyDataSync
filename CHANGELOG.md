# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2025-03-13
### :sparkles: New Features
- [`6cd3dd2`](https://github.com/SettingDust/DustyDataSync/commit/6cd3dd25f73c0d06d4ad64b7819a5131274bb49d) - usable *(commit by [@SettingDust](https://github.com/SettingDust))*

### :bug: Bug Fixes
- [`81cb973`](https://github.com/SettingDust/DustyDataSync/commit/81cb973f8b1d325c7d8a0702a33d7ec5d1bd7d0e) - ftb team sync correctly *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`8fd9f25`](https://github.com/SettingDust/DustyDataSync/commit/8fd9f25dd7ff44a06b74cc2019e3461b3ad4ad1b) - use the local data if missing in database *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`887a496`](https://github.com/SettingDust/DustyDataSync/commit/887a4960ada14c78cc0112c4f5e4a0ff4cb8404a) - fix errors for ftb *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`a1388c2`](https://github.com/SettingDust/DustyDataSync/commit/a1388c2f5c7e112d24d23cd14a331eea65715e2a) - player don't save *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`d99010b`](https://github.com/SettingDust/DustyDataSync/commit/d99010b3396b6a8296a1e1a429c0e0d4edd8e859) - sync networks when change permission *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`1fb774d`](https://github.com/SettingDust/DustyDataSync/commit/1fb774d83f76bd7a18a0859897cf74646cda74af) - avoid save unloaded data *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`65467d0`](https://github.com/SettingDust/DustyDataSync/commit/65467d0a069d626239cd3f6bf499d9d817a31ef2) - fix crashes syncing flux *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`dca79a3`](https://github.com/SettingDust/DustyDataSync/commit/dca79a3472038126b1dbbeb943f0b654d8d86663) - **fluxnetworks**: concurrent modify networks *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`81c69dd`](https://github.com/SettingDust/DustyDataSync/commit/81c69dd7a1e86d020020bd11df3e8b4e57b44b78) - **gamestages**: save the data on time *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`3948ccd`](https://github.com/SettingDust/DustyDataSync/commit/3948ccd44f7a1ae8a6adaf228a51e2a6b427a825) - memory leak *(commit by [@SettingDust](https://github.com/SettingDust))*

### :recycle: Refactors
- [`6e3970b`](https://github.com/SettingDust/DustyDataSync/commit/6e3970bb0123d00525f44b086ea6867781b2b875) - using mongo *(commit by [@SettingDust](https://github.com/SettingDust))*

### :wrench: Chores
- [`2684331`](https://github.com/SettingDust/DustyDataSync/commit/268433147b209fb37a0d0cf0221630e3fe51ca26) - don't remove empty nbt *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.2.1] - 2024-11-15
### :bug: Bug Fixes
- [`5d42428`](https://github.com/SettingDust/DustyDataSync/commit/5d424280553df2c258b54e2ec6b9524af6dd0538) - **ftbquest**: avoid error when server stopping *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.2.0] - 2024-07-30
### :sparkles: New Features
- [`06bf06f`](https://github.com/SettingDust/DustyDataSync/commit/06bf06fcf48b86bfc54ca074a4aea9eb492b418e) - use mariadb jdbc driver *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.1.6] - 2024-07-30
### :bug: Bug Fixes
- [`c32358a`](https://github.com/SettingDust/DustyDataSync/commit/c32358a6f84ca30d815b91ecba8ec67d030976a8) - create lock file *(commit by [@SettingDust](https://github.com/SettingDust))*
- [`a59e795`](https://github.com/SettingDust/DustyDataSync/commit/a59e795888e26b3a31351b4490cceb03aa08bea0) - shadow slf4j *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.1.4] - 2024-07-30
### :bug: Bug Fixes
- [`467cca3`](https://github.com/SettingDust/DustyDataSync/commit/467cca34889a37fa1bfddd7f6f4eeec26dedc18e) - use hikaricp 4 for java 8 *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.1.3] - 2024-07-30
### :bug: Bug Fixes
- [`5dcd2ae`](https://github.com/SettingDust/DustyDataSync/commit/5dcd2ae3136c6181a2e63038fda58822d8cf5147) - add condition for syncer to avoid crash when mod lacking *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.1.2] - 2024-07-24
### :bug: Bug Fixes
- [`6e9bf0e`](https://github.com/SettingDust/DustyDataSync/commit/6e9bf0eab5abafc132c0dbda5fe5ef2d37485834) - remove minimize from shadow for services *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.1.1] - 2024-07-24
### :bug: Bug Fixes
- [`4869903`](https://github.com/SettingDust/DustyDataSync/commit/4869903acd57b8d50977e9e674894a1e121383ca) - add the annotation processor for mixin *(commit by [@SettingDust](https://github.com/SettingDust))*


## [1.1.0] - 2024-07-23
### :sparkles: New Features
- [`d288da1`](https://github.com/SettingDust/DustyDataSync/commit/d288da12c50940a8e3de26654cb6f2bb5f84bd2a) - change the strings to english *(commit by [@SettingDust](https://github.com/SettingDust))*

### :bug: Bug Fixes
- [`c4e8b9c`](https://github.com/SettingDust/DustyDataSync/commit/c4e8b9cfca69e99737903b47733e58f881558fe6) - needn't mcversion *(commit by [@SettingDust](https://github.com/SettingDust))*

[1.1.0]: https://github.com/SettingDust/DustyDataSync/compare/1.0.0...1.1.0
[1.1.1]: https://github.com/SettingDust/DustyDataSync/compare/1.1.0...1.1.1
[1.1.2]: https://github.com/SettingDust/DustyDataSync/compare/1.1.1...1.1.2
[1.1.3]: https://github.com/SettingDust/DustyDataSync/compare/1.1.2...1.1.3
[1.1.4]: https://github.com/SettingDust/DustyDataSync/compare/1.1.3...1.1.4
[1.1.6]: https://github.com/SettingDust/DustyDataSync/compare/1.1.5...1.1.6
[1.2.0]: https://github.com/SettingDust/DustyDataSync/compare/1.1.6...1.2.0
[1.2.1]: https://github.com/SettingDust/DustyDataSync/compare/1.2.0...1.2.1
[1.3.0]: https://github.com/SettingDust/DustyDataSync/compare/1.2.1...1.3.0
