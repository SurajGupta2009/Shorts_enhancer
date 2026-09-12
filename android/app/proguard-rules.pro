# Nothing here yet: minification is off for the CI build so the APK stays easy to inspect.
# If you turn it on, keep the model loader and the accessibility service entry points:
-keep class com.shortsense.nlp.TinyModel { *; }
-keep class com.shortsense.service.ShortsWatcherService { *; }
