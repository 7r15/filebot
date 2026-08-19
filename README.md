# FileBot

This is a fork of the official FileBot source code on **23 Mar 2018** for version **4.8.0**.
The repo has unfortunately since been taken offline. It appears like the developer [rednoah](https://github.com/rednoah) has decided to try and make money off this software which originally rose in popularity due to its open source nature.

rednoah has:
* Added nagware to the original software to promote sales
* Made it intentionally harder to build the software
* Censored/removed posts on forums they moderate
* Deceived the community who supported the software
* Finally, removed the open source code from Github.

Stop making absurd excuses like "there were no other contributors" which is a complete lie. Just say you want to make money, there is nothing wrong with that but you can't seem to admit it.

# Original Fork Point
If you are interested in the original fork point check out the [fork-point](../../tree/fork-point/) branch.

# Newer Fork
Looks like another newer fork is available here: https://github.com/deleted-repo/filebot

# Building
It is possible to build the source code as a standalone jar or as an self signed UWP app.

## Metadata provider credentials

Provider credentials are supplied at runtime instead of being committed to the repository. Each key can be configured with a Java system property or an environment variable. System properties take precedence.

The Movie Database read access token is the preferred credential:

```text
-Dnet.filebot.apikey.themoviedb.token=your-token
FILEBOT_APIKEY_THEMOVIEDB_TOKEN=your-token
```

Legacy v3 API keys remain supported as `net.filebot.apikey.themoviedb` or `FILEBOT_APIKEY_THEMOVIEDB`.

The same naming scheme applies to `thetvdb`, `opensubtitles`, `omdb`, `acoustid`, `anidb`, and `fanart.tv` (`FILEBOT_APIKEY_FANART_TV`). Credentials can also be saved through **Settings > Providers** in the desktop interface.

TheTVDB v4 uses a project API key. User-supported projects also require the subscriber PIN provided by TheTVDB:

```text
FILEBOT_APIKEY_THETVDB=your-project-key
FILEBOT_APIKEY_THETVDB_PIN=your-subscriber-pin
```

The equivalent Java properties are `net.filebot.apikey.thetvdb` and `net.filebot.apikey.thetvdb.pin`. Metadata provided by [TheTVDB](https://thetvdb.com/).

For OpenSubtitles, create an API consumer key from your [OpenSubtitles.com account](https://www.opensubtitles.com/consumers) and provide it as `FILEBOT_APIKEY_OPENSUBTITLES`.

For AcoustID lookups, register an application and provide its application API key as `FILEBOT_APIKEY_ACOUSTID`. The user key shown after signing in is intended for fingerprint submissions and cannot be used as the lookup client key. The integration uses compressed HTTPS requests and observes the service limit of three requests per second. AcoustID's public service is limited to non-commercial use unless separate permission is arranged.

OMDb requests use HTTPS and require `FILEBOT_APIKEY_OMDB`. Free keys are limited to 1,000 requests per day, and OMDb content is licensed for non-commercial use.

OpenSubtitles downloads also require an OpenSubtitles.com account. Enter it in the subtitle panel for the current session, or configure it at launch:

```text
FILEBOT_OPENSUBTITLES_USERNAME=your-username
FILEBOT_OPENSUBTITLES_PASSWORD=your-password
```

The equivalent Java system properties are `net.filebot.opensubtitles.username` and `net.filebot.opensubtitles.password`. Passwords entered in the interface are not persisted.

# Binaries/Releases
Check out the releases for some releases.

Also check out this repo more up to date sources/releases: https://github.com/barry-allen07/FB-Mod

# Licence
The FileBot source code is available for your convenience.

I will keep this repo under the same licence (which was modified for more greed) [MODIFIED DON'T BE A DICK PUBLIC LICENSE](../master/LICENSE.md).
