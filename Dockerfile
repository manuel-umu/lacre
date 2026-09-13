FROM eclipse-temurin:25-jdk-alpine AS construccion

WORKDIR /origen

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests \
    && java -Djarmode=tools -jar target/lacre-*.jar \
       extract --layers --launcher --destination extraido


FROM eclipse-temurin:25-jre-alpine AS ejecucion

RUN addgroup --system lacre && adduser --system --ingroup lacre lacre

WORKDIR /app

COPY --from=construccion --chown=lacre:lacre /origen/extraido/dependencies/ ./
COPY --from=construccion --chown=lacre:lacre /origen/extraido/spring-boot-loader/ ./
COPY --from=construccion --chown=lacre:lacre /origen/extraido/snapshot-dependencies/ ./
COPY --from=construccion --chown=lacre:lacre /origen/extraido/application/ ./

USER lacre
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
