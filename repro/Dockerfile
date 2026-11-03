# A clean Debian build machine, roughly like F-Droid's build server, for checking that a
# release APK built on Windows can be reproduced from the committed source.
FROM debian:trixie

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      openjdk-21-jdk-headless git unzip wget ca-certificates apksigcopier apksigner \
 && rm -rf /var/lib/apt/lists/*

# Same Android command-line tools release and SDK packages as the Windows machine.
ENV ANDROID_HOME=/opt/android-sdk
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -O /tmp/ct.zip \
 && mkdir -p $ANDROID_HOME/cmdline-tools \
 && unzip -q /tmp/ct.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/ct.zip
RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null \
 && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;35.0.0"

COPY check.sh /check.sh
CMD ["sh", "/check.sh"]
