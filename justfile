build-backend:
  sbt --client backend/dockerBuildAndPush

deploy-backend:
  cd modules/backend; flyctl deploy

scrape:
  ./mill modules.backend.run scraper

smoke:
  ./mill modules.backend.run smoke-run

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
