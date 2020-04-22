FROM navikt/java:12
# prøver java12 på tross av cxf #YOLO

COPY build/libs/*.jar ./

