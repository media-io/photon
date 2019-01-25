FROM openjdk:8

ADD . /source

WORKDIR /source

RUN apt-get update && apt-get install -y git

RUN git clone https://github.com/media-io/JavaPhoenixChannels.git && \
    cd JavaPhoenixChannels && \
    ./gradlew build && \
    ./gradlew publishToMavenLocal && \
    cd ..  && \
    ./gradlew build && \
    ./gradlew getDependencies

ENV IMP_PATH=/media
CMD cd /source/build/libs && java -cp /source/build/libs/*: com.netflix.imflibrary.app.IMPAnalyzer $IMP_PATH
