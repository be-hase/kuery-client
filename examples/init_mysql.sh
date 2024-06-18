#!/bin/bash
set -euo pipefail

mysql_name="kuery-client-example-mysql"

# Create user
echo "[$mysql_name] Try to create users: admin"
docker compose exec "$mysql_name" mysql -e "
CREATE USER 'admin'@'%' IDENTIFIED BY 'admin';
GRANT ALL ON *.* TO admin@'%';
"
echo "[$mysql_name] Success to created users"

# Create database
echo "[$mysql_name] Try to create database"
docker compose exec "$mysql_name" mysql -e "
CREATE DATABASE testdb;
"
echo "[$mysql_name] Success to create database"

# Create user and tweet table
echo "[$mysql_name] Try to create tables"
docker compose exec "$mysql_name" mysql testdb -e "
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL
);

CREATE TABLE orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    order_date DATE,
    amount DECIMAL(10, 2),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(100),
    price DECIMAL(10, 2)
);

CREATE TABLE order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

INSERT INTO users (username, email) VALUES
('user1', 'user1@example.com'),
('user2', 'user2@example.com');

INSERT INTO orders (user_id, order_date, amount) VALUES
(1, '2023-06-01', 100.00),
(2, '2023-06-02', 150.00);

INSERT INTO products (product_name, price) VALUES
('Product A', 25.00),
('Product B', 50.00);

INSERT INTO order_items (order_id, product_id, quantity) VALUES
(1, 1, 2),
(1, 2, 1),
(2, 1, 1);
"
echo "[$mysql_name] Success to create tables"
