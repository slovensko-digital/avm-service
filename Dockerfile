FROM bellsoft/liberica-runtime-container:jdk-17.0.10-glibc as build

RUN apk add bash

COPY mvnw mvnw
COPY .mvn .mvn

COPY pom.xml pom.xml
RUN ./mvnw initialize

COPY src src
RUN apk add --no-cache binutils
RUN ./mvnw package

CMD ["java", "-jar", "--add-exports", "java.base/sun.security.x509=ALL-UNNAMED", "target/avm-1.0.0.jar"]

# FROM bellsoft/liberica-runtime-container:jdk-17.0.10-glibc as prod
#
# COPY --from=build target/avm-1.0.0.jar avm-1.0.0.jar
#
# CMD ["java", "-jar", "avm-1.0.0.jar"]
