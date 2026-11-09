FROM odoo:18.0

USER root
COPY entrypoint.sh /usr/local/bin/vetstock-odoo-entrypoint.sh
# No en /mnt/extra-addons: la imagen oficial lo declara volumen y, al recrear el contenedor, Docker
# conservaría la copia antigua del módulo. El entrypoint añade esta ruta a la de módulos.
COPY addons/ /opt/vetstock-addons/

RUN sed -i -e 's/\r$//' -e 's/ --admin-passwd adminodoo//g' /usr/local/bin/vetstock-odoo-entrypoint.sh \
    && bash -n /usr/local/bin/vetstock-odoo-entrypoint.sh \
    && chmod 755 /usr/local/bin/vetstock-odoo-entrypoint.sh

USER odoo
ENTRYPOINT ["/bin/bash", "/usr/local/bin/vetstock-odoo-entrypoint.sh"]
