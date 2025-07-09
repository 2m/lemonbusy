FROM docker.io/library/alpine:3.19

COPY out/modules/backend/nativeImage.dest/native-executable /lemonbusy

ENTRYPOINT ["/lemonbusy"]
