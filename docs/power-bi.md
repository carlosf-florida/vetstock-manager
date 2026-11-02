# Preparación para Power BI

## Situación

- No existía ninguna integración previa con Power BI en el proyecto.
- La aplicación tiene su propia pantalla **Admin → Informes**, con gráficos
  hechos en React sobre datos reales de la API (`/api/informes/*`). **No es
  Power BI** y no se presenta como tal.
- Para Power BI se han preparado **vistas SQL de solo lectura**
  (`database/migrations/008_vistas_powerbi.sql`). No se ha creado ningún
  informe `.pbix` ni se ha conectado Power BI: no se ha comprobado que haya
  licencia ni acceso a Power BI Desktop o al servicio.

## Vistas disponibles

| Vista | Una fila por | Sirve para |
|---|---|---|
| `v_pbi_lineas_pedido` | línea de pedido | Productos más pedidos, unidades, importes, evolución mensual (`mes`), por clínica y categoría |
| `v_pbi_pedidos` | pedido | Estados, firmados/pagados, pendientes de firma y de pago, **horas** entre creación → firma → pago → expedición, bultos |
| `v_pbi_stock` | producto | Stock físico, reservado, disponible y aviso de stock bajo |
| `v_pbi_bultos_destino` | bulto | Pedidos y bultos por destino y palet |

Ejemplo de consulta para comprobarlas en MySQL:

```sql
SELECT producto, SUM(unidades) AS unidades, SUM(importe_base) AS importe
  FROM v_pbi_lineas_pedido GROUP BY producto ORDER BY unidades DESC;
```

## Conexión desde Power BI Desktop (Windows)

1. Crea en MySQL un usuario que **solo pueda leer las vistas** (cambia la clave):

   ```sql
   CREATE USER 'powerbi'@'localhost' IDENTIFIED BY 'cambia-esta-clave';
   GRANT SELECT ON vetstock.v_pbi_lineas_pedido  TO 'powerbi'@'localhost';
   GRANT SELECT ON vetstock.v_pbi_pedidos        TO 'powerbi'@'localhost';
   GRANT SELECT ON vetstock.v_pbi_stock          TO 'powerbi'@'localhost';
   GRANT SELECT ON vetstock.v_pbi_bultos_destino TO 'powerbi'@'localhost';
   ```

   Con MySQL en Docker, el usuario debe poder conectarse desde fuera del
   contenedor (`'powerbi'@'%'` o la IP del equipo). El puerto solo se publica
   en `127.0.0.1`.

2. En Power BI Desktop: *Obtener datos → Base de datos MySQL*. Servidor
   `localhost:3306`, base de datos `vetstock`. Puede pedir instalar el conector
   MySQL (Connector/NET) de Oracle.
3. Elige las cuatro vistas y crea las relaciones por `pedido_id`.
4. Visualizaciones sugeridas: barras con las unidades por producto, una línea
   con el importe por `mes`, una tarjeta con los pedidos pendientes de firma y de
   pago, una tabla de stock bajo, la media de `horas_firma_a_pago` y
   `horas_pago_a_expedicion`, y columnas con los bultos por destino.

## Lo que NO se debe hacer

- **No usar «Publicar en la web»**: haría públicos los datos sin control de acceso.
- No prometer el informe incrustado de forma privada en la aplicación sin
  comprobar las licencias (Power BI Pro/Premium o Embedded) y los permisos de la
  organización.
- No dar a Power BI el usuario `root` ni permisos de escritura.

## Alternativa sin licencia

Exportar las vistas a CSV (por ejemplo, desde MySQL Workbench) y abrirlas en
Power BI Desktop, que es gratuito en Windows, para trabajar en local sin
publicar nada.
