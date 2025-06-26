FROM openjdk:17-slim

RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

	COPY target/videoprocessor-0.0.1-SNAPSHOT.jar /app/app.jar
	WORKDIR /app


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
