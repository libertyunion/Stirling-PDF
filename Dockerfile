# Build jbig2enc in a separate stage
FROM debian:bullseye-slim as jbig2enc_builder

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        git \
        automake \
        autoconf \
        libtool \
        libleptonica-dev \
        pkg-config \
        ca-certificates \
        zlib1g-dev \
		make \
		g++

RUN git clone https://github.com/agl/jbig2enc && \
    cd jbig2enc && \
    ./autogen.sh && \
    ./configure && \
    make && \
    make install

# Main stage
FROM openjdk:17-jdk-slim

# Install necessary dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libreoffice-core \
        libreoffice-common \
        libreoffice-writer \
        libreoffice-calc \
        libreoffice-impress \
        python3-uno \
		python3-pip \
        unoconv \
		pngquant \
		unpaper \
        ocrmypdf && \
	pip install --user --upgrade ocrmypdf

# Copy the jbig2enc binary from the builder stage
COPY --from=jbig2enc_builder /usr/local/bin/jbig2 /usr/local/bin/jbig2

# Copy the application JAR file
COPY build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Set environment variables
ENV APP_HOME_NAME="Stirling PDF"
#ENV APP_HOME_DESCRIPTION="Personal PDF Website!"
#ENV APP_NAVBAR_NAME="Stirling PDF"

# Run the application
ENTRYPOINT java -jar /app.jar



