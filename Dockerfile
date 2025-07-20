FROM docker.io/library/alpine:3.19

# GNU C Library compatibility layer for native image
RUN apk add gcompat \
  && apk cache clean

COPY out/modules/backend/nativeImage.dest/native-executable /lemonbusy
