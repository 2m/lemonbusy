build-backend:
  sbt --client backend/dockerBuildAndPush

deploy-backend:
  cd modules/backend; flyctl deploy

scrape:
  sbt --client backend/run scraper

smoke:
  sbt --client backend/run smoke-run

smoke-with-agent:
  sbt --client backend/nativeImageRunAgent \" smoke-run\"

native-image:
  sbt --client backend/nativeImage

run-native-image:
  modules/backend/target/native-image/backend scraper

docker-login:
  cd modules/backend; fly auth token | docker login registry.fly.io --username=x --password-stdin

telemetry:
  cd telemetry; docker-compose up

logs:
  cd modules/backend; flyctl logs
