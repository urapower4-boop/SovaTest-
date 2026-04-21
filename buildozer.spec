[app]
title = SovaTest
package.name = sovatest
package.domain = ru.sova

source.dir = .
source.include_exts = py,png,jpg,kv,atlas

requirements = python3,kivy,pillow,requests

orientation = portrait
fullscreen = 0

android.permissions = INTERNET,SYSTEM_ALERT_WINDOW,FOREGROUND_SERVICE,MEDIA_PROJECTION
android.api = 31
android.minapi = 24
android.ndk = 25b
android.accept_sdk_license = True

[buildozer]
log_level = 2
warn_on_root = 1
