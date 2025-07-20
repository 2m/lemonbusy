build-backend:
  sbt --client backend/dockerBuildAndPush

deploy-backend:
  cd modules/backend; flyctl deploy

scrape:
  ./mill modules.backend.run scraper

smoke:
  ./mill modules.backend.run smoke-run

smoke-grafana-local:
  ./mill modules.backend.run smoke-run \
    --exporter-endpoint="http://localhost:4318" \
    --exporter-protocol="http/protobuf"

smoke-grafana-cloud:
  ./mill modules.backend.run smoke-run \
    --exporter-endpoint="https://otlp-gateway-prod-eu-west-0.grafana.net/otlp" \
    --exporter-protocol="http/protobuf" \
    --exporter-headers="Authorization=Basic $(op item get lemonbusy-otlp-auth --fields=notesPlain)"

native-image:
  ./mill modules.backend.nativeImage

run-native-image:
  out/modules/backend/nativeImage.dest/native-executable scraper

docker-login:
  cd modules/backend; fly auth token | docker login registry.fly.io --username=x --password-stdin

telemetry:
  cd telemetry; docker-compose up

logs:
  cd modules/backend; flyctl logs
