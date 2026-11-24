-- Ejecutar en la base techshop de semanas anteriores si faltan estas tablas.
USE techshop;
CREATE TABLE IF NOT EXISTS factura (
    id_factura INT PRIMARY KEY AUTO_INCREMENT,
    id_usuario INT NOT NULL,
    fecha DATETIME NOT NULL,
    total DECIMAL(15,2) NOT NULL,
    estado ENUM('Activa','Pagada','Anulada') NOT NULL,
    fecha_creacion DATETIME NOT NULL,
    fecha_modificacion DATETIME NOT NULL,
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);
CREATE TABLE IF NOT EXISTS venta (
    id_venta INT PRIMARY KEY AUTO_INCREMENT,
    id_factura INT NOT NULL,
    id_producto INT NOT NULL,
    precio_historico DECIMAL(15,2) NOT NULL,
    cantidad INT NOT NULL,
    fecha_creacion DATETIME NOT NULL,
    fecha_modificacion DATETIME NOT NULL,
    FOREIGN KEY (id_factura) REFERENCES factura(id_factura),
    FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
);
