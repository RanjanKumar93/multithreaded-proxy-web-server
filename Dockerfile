FROM amazoncorretto:25

WORKDIR /app

COPY . .

RUN javac src/v8_http_https/*.java

CMD ["java", "-cp", "src", "v8_http_https.ProxyServer"]