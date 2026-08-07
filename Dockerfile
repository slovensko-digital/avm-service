FROM eclipse-temurin:25.0.3_9-jdk-noble AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY .mvn .mvn
COPY pom.xml pom.xml
COPY core/pom.xml core/pom.xml
COPY service/pom.xml service/pom.xml

COPY core/src core/src
COPY service/src service/src

RUN mvn package

FROM eclipse-temurin:25.0.3_9-jre-noble AS prod

WORKDIR /app
COPY --from=build /app/service/target/service-1.2.1-jar-with-dependencies.jar ./

CMD ["java", "-jar", "service-1.2.1-jar-with-dependencies.jar"]
