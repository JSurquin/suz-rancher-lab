FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY src/Main.java .
RUN javac Main.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/Main.class .
EXPOSE 8080
CMD ["java", "Main"]
