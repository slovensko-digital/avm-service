FROM maven:3.9.11-eclipse-temurin-21-noble as build

WORKDIR /app

COPY .mvn .mvn
COPY pom.xml pom.xml
COPY core/pom.xml core/pom.xml
COPY service/pom.xml service/pom.xml

COPY core/src core/src
COPY service/src service/src

RUN mvn package


FROM eclipse-temurin:21.0.9_10-jre-noble as prod
WORKDIR /app
COPY --from=build /app/service/target/service-1.0.0-jar-with-dependencies.jar ./

CMD ["java", "-jar", "service-1.0.0-jar-with-dependencies.jar"]
