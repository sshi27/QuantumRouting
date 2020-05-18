FROM ubuntu:focal
ENV DEBIAN_FRONTEND=noninteractive

RUN     apt update && apt install maven git python3 python3-pip texlive fonts-linuxlibertine ttf-mscorefonts-installer -y && cd /root && git clone https://github.com/sshi27/QuantumRouting \
        && cd QuantumRouting && mvn compile && cd .. && git clone https://github.com/sshi27/plot && cd plot && \
        pip3 install -r requirements.txt && apt clean

WORKDIR  /root/