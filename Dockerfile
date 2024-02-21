FROM bellsoft/liberica-runtime-container:jdk-all-17.0.7-glibc as build

COPY mvnw mvnw
COPY .mvn .mvn

COPY pom.xml pom.xml

COPY src src

RUN ./mvnw package

FROM bellsoft/liberica-runtime-container:jre-17.0.7-glibc as prod

COPY --from=build target/avm-1.0.0-jar-with-dependencies.jar avm-1.0.0.jar

CMD ["java", "-jar", "avm-1.0.0.jar"]
