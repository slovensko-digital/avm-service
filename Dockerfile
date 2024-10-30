FROM bellsoft/liberica-runtime-container:jdk-17.0.13-glibc as build

RUN apk add bash

COPY .mvn .mvn
COPY pom.xml pom.xml

COPY src src
RUN apk add --no-cache binutils
RUN mvn package


FROM eclipse-temurin:17.0.12_7-jre-noble as prod
WORKDIR /app
COPY --from=build target/service-1.0.0-jar-with-dependencies.jar ./

CMD ["java", "-jar", "--add-exports", "java.base/sun.security.x509=ALL-UNNAMED", "service-1.0.0-jar-with-dependencies.jar"]
