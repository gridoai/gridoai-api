## sbt project compiled with Scala 3

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

## Development
Run `sbt` to get into the sbt console
To restart the server every time a change is detected, run `~reStart`

## Deploying
- Set up google cloud credentials
- Run `sbt deploy`

# Testing
- Set ENV=TEST
- Run `sbt test`

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).
