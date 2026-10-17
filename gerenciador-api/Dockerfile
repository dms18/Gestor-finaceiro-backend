# Build multi-stage do backend Quarkus (Java 25). Contexto: backend/gerenciador-api
# O estagio de build roda na arquitetura do host (o jar e portavel) para ser mais rapido
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src src
# As chaves JWT vem de volume em runtime; nao embarcar as do repositorio no jar
RUN rm -f src/main/resources/privateKey.pem src/main/resources/publicKey.pem \
 && mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd -u 185 -r -s /usr/sbin/nologin quarkus \
 && mkdir -p /deployments /var/log/gerenciador \
 && chown quarkus /var/log/gerenciador
WORKDIR /deployments
COPY --from=build --chown=185 /build/target/quarkus-app/ ./
USER 185
EXPOSE 8080
ENV JAVA_OPTS_APPEND=""
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS_APPEND -Djava.util.logging.manager=org.jboss.logmanager.LogManager -jar quarkus-run.jar"]
