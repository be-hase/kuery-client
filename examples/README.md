# Examples

## How to run

For the complete walkthrough and API calls, see the
[Examples documentation](https://kuery-client.hsbrysk.dev/examples).

Run the steps below in order. Start from the repository root; after `cd examples`, remain in that
directory for the application and shutdown commands.

### Start and initialize MySQL

```shell
cd examples
docker compose up -d
./init_mysql.sh
```

### Run application

```shell
../gradlew :spring-data-r2dbc:bootRun

# or

../gradlew :spring-data-jdbc:bootRun
```

Both applications use port `8080`, so run only one at a time.

### Stop MySQL

```shell
docker compose down
```
