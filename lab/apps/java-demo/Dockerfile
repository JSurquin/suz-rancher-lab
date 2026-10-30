FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY src/Main.java .
COPY src/web ./web
RUN javac Main.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/Main.class .
COPY --from=builder /app/web ./web
EXPOSE 8080
CMD ["java", "Main"]
