# The jar is built on the host first: mvn -q -pl sut -am package -DskipTests
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/sut.jar sut.jar
EXPOSE 7070
ENTRYPOINT ["java", "-jar", "sut.jar"]
