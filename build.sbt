ThisBuild / scalaVersion := "3.6.4"
ThisBuild / scalafmtOnCompile := true

ThisBuild / organization := "lt.dvim.lemonbusy"
ThisBuild / organizationName := "github.com/2m/lemonbusy/contributors"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / dynverSeparator := "-"

ThisBuild / libraryDependencies += compilerPlugin("com.github.ghik" % "zerowaste" % "1.0.0" cross CrossVersion.full)

// invisible because used from dyn task
Global / excludeLintKeys ++= Set(nativeImageJvm, nativeImageVersion)

val MUnitFramework = new TestFramework("munit.Framework")
val Integration = config("integration").extend(Test)

lazy val backend = project
  .in(file("modules/backend"))
  .configs(Integration)
  .settings(
    inConfig(Integration)(Defaults.testTasks),
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client"                       % "1.11.23",
      "org.http4s"                  %% "http4s-ember-client"                       % "0.23.30",
      "org.http4s"                  %% "http4s-otel4s-middleware-trace-client"     % "0.10.0",
      "ch.qos.logback"               % "logback-classic"                           % "1.5.18",
      "com.monovore"                %% "decline-effect"                            % "2.5.0",
      "com.themillhousegroup"       %% "scoup"                                     % "1.0.0",
      "io.bullet"                   %% "borer-derivation"                          % "1.16.0",
      "org.typelevel"               %% "log4cats-core"                             % "2.7.0",
      "org.typelevel"               %% "log4cats-slf4j"                            % "2.7.0",
      "org.typelevel"               %% "otel4s-oteljava"                           % "0.12.0",
      "org.typelevel"               %% "otel4s-instrumentation-metrics"            % "0.12.0",
      "io.opentelemetry"             % "opentelemetry-exporter-otlp"               % "1.48.0",
      "io.opentelemetry"             % "opentelemetry-sdk-extension-autoconfigure" % "1.48.0",
      "org.scalameta"               %% "munit"                                     % "1.1.0" % Test,
      "org.typelevel"               %% "munit-cats-effect"                         % "2.0.0" % Test,
      "com.github.jatcwang"         %% "difflicious-munit"                         % "0.4.3" % Test
    ),

    // while http4s-otel4s-middleware depends on older otel4s version
    libraryDependencySchemes ++= Seq(
      "org.typelevel" %% "otel4s-core-trace"  % VersionScheme.Always,
      "org.typelevel" %% "otel4s-core-common" % VersionScheme.Always
    ),

    // for correct IOApp resource cleanup
    Compile / run / fork := true,

    // disable varnings for given search alternatives
    scalacOptions ++= Seq("-source", "3.7"),

    // exclude integration tests
    Test / testOptions += Tests.Argument(MUnitFramework, "--exclude-tags=integration"),
    Integration / testOptions := Seq(Tests.Argument(MUnitFramework, "--include-tags=integration")),

    // include some build settings into module code
    buildInfoKeys := Seq[BuildInfoKey](
      isSnapshot, // for determining which backend URL to use
      version, // for user agent
      Test / resourceDirectory // for integration test snapshots
    ),
    buildInfoPackage := "lemonbusy",

    // native image
    nativeImageJvm := "graalvm-java21",
    nativeImageVersion := "21.0.2",
    nativeImageAgentOutputDir := (Compile / resourceDirectory).value / "META-INF" / "native-image" / organization.value / name.value,
    nativeImageOptions ++= List(
      "--verbose",
      "--no-fallback", // show the underlying problem due to unsupported features instead of building a fallback image
      "-H:IncludeResources=db/V.*sql$",
      "-march=compatibility", // Use most compatible instructions, 'native' fails to start on flyio
      "--enable-url-protocols=https" // for OpenTelemetry export to honeycomb
    ),

    // docker image build
    docker / dockerfile := {
      val artifactDir = nativeImage.value.getParentFile

      new Dockerfile {
        from("alpine:3.19")

        run("apk", "add", "gcompat") // GNU C Library compatibility layer for native image
        copy(artifactDir ** "*" filter { !_.isDirectory } get, "/app/")
      }
    },
    docker / imageNames := Seq(
      ImageName("registry.fly.io/lemonbusy:latest"),
      ImageName(s"registry.fly.io/lemonbusy:${version.value}")
    )
  )
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(NativeImagePlugin)
  .enablePlugins(DockerPlugin)
