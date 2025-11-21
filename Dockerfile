FROM ubuntu:latest

RUN mkdir -p /tmp

RUN apt-get update -y
RUN apt-get upgrade -y
RUN apt-get install -y default-jdk unzip wget


RUN mkdir -p /opt/android-sdk && \
    cd /opt/android-sdk && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip commandlinetools-linux-*.zip -d cmdline-tools && \
    yes | cmdline-tools/bin/sdkmanager --sdk_root=/opt/android-sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"


ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$ANDROID_HOME/cmdline-tools/bin:$ANDROID_HOME/platform-tools:$PATH


COPY . /tmp
WORKDIR /tmp
RUN chmod +x gradlew

CMD [ "./gradlew", "assembleDebug" ]