FROM statful/java-build as build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src/ ./src/
RUN mvn package -B

FROM statful/java-run

ENV APPLICATION_NAME k8s-metrics-pipeline
ENV APPLICATION_JAR k8s-metrics-pipeline-fat.jar

ADD provision/run.sh run.sh
RUN mkdir -p /opt/$APPLICATION_NAME
COPY --from=build /build/target/$APPLICATION_JAR /opt/$APPLICATION_NAME/$APPLICATION_NAME.jar

ENTRYPOINT exec ./run.sh
