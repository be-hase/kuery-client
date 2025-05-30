# Examples

## How to run

### Run MySQL container

```shell
cd examples
./run_containers.sh
```

### Init MySQL databases/tables

```shell
cd examples
./init_mysql.sh
```

### Run application

```shell
cd examples
./gradlew :spring-data-r2dbc:bootRun

# or

./gradlew :spring-data-jdbc:bootRun
```
