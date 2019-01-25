FROM openjdk:8

ADD . /source

WORKDIR /source

RUN git clone https://github.com/media-io/JavaPhoenixChannels.git && \
    cd JavaPhoenixChannels && \
    ./gradlew build \
     mvn install:install-file \
      -Dfile=build/libs/JavaPhoenixChannels-1.0.0.jar \
      -DgroupId=com.github.eoinsha \
      -DartifactId=JavaPhoenixChannels \
      -Dversion=1.0.0 \
      -Dpackaging=jar  && \
    cd ..  && \
    ./gradlew build && \
    ./gradlew getDependencies

ENV IMP_PATH=/media
CMD cd /source/build/libs && java -cp /source/build/libs/*: com.netflix.imflibrary.app.IMPAnalyzer $IMP_PATH
