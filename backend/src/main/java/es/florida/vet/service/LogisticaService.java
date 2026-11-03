package es.florida.vet.service;

import es.florida.vet.dto.BultoRequest;
import es.florida.vet.dto.BultoResponse;
import es.florida.vet.dto.DestinoRequest;
import es.florida.vet.dto.DestinoResponse;
import es.florida.vet.dto.PedidoResponse;
import es.florida.vet.dto.PaletRequest;
import es.florida.vet.dto.PaletResponse;
import es.florida.vet.entity.Bulto;
import es.florida.vet.entity.Destino;
import es.florida.vet.entity.Palet;
import es.florida.vet.entity.Pedido;
import es.florida.vet.enums.EstadoPedido;
import es.florida.vet.exception.RecursoNoEncontradoException;
import es.florida.vet.exception.ReglaNegocioException;
import es.florida.vet.repository.BultoRepository;
import es.florida.vet.repository.DestinoRepository;
import es.florida.vet.repository.PaletRepository;
import es.florida.vet.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Logística interna: bultos, palets y destinos. Solo la usa el ADMIN. */
@Service
public class LogisticaService {

    private final BultoRepository bultoRepository;
    private final PaletRepository paletRepository;
    private final DestinoRepository destinoRepository;
    private final PedidoRepository pedidoRepository;
    private final SesionService sesionService;
    private final org.springframework.context.ApplicationEventPublisher eventos;

    public LogisticaService(BultoRepository bultoRepository, PaletRepository paletRepository,
                            DestinoRepository destinoRepository, PedidoRepository pedidoRepository,
                            SesionService sesionService, org.springframework.context.ApplicationEventPublisher eventos) {
        this.bultoRepository = bultoRepository;
        this.paletRepository = paletRepository;
        this.destinoRepository = destinoRepository;
        this.pedidoRepository = pedidoRepository;
        this.sesionService = sesionService;
        this.eventos = eventos;
    }

    /**
     * Pide actualizar el pedido en Odoo después del commit (mismo mecanismo que pago, firma y
     * estados). Solo se exportan pedidos pagados. Si Odoo falla, el pedido queda pendiente.
     */
    private void sincronizarConOdoo(Pedido pedido) {
        if (pedido.getFechaPago() == null) return;
        pedido.setOdooSincronizacionPendiente(true);
        pedido.setOdooUltimoError(null);
        pedidoRepository.save(pedido);
        eventos.publishEvent(new PedidoActualizadoEvent(pedido.getId()));
    }

    // ----- Salida de expedición del pedido -----

    /**
     * Administración asigna o cambia la salida de un pedido. Queda como elección manual
     * (preparar ya no la sustituye por la habitual). No se permite en pedidos expedidos o
     * cancelados, ni si algún bulto ya está en un palet de otra salida.
     */
    @Transactional
    public PedidoResponse asignarSalida(Long pedidoId, Long destinoId, String username) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));
        if (pedido.getEstado() == EstadoPedido.EXPEDIDO || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new ReglaNegocioException("No se puede cambiar la salida de un pedido expedido o cancelado");
        }
        if (destinoId == null) {
            throw new ReglaNegocioException("Elige una salida");
        }
        Destino destino = destinoRepository.findById(destinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La salida no existe"));
        boolean bultoEnOtraSalida = bultoRepository.findByPedidoId(pedidoId).stream()
                .anyMatch(b -> b.getPalet() != null && !b.getPalet().getDestino().getId().equals(destinoId));
        if (bultoEnOtraSalida) {
            throw new ReglaNegocioException("Hay bultos de este pedido en un palet de otra salida; no se puede cambiar");
        }
        pedido.asignarDestino(destino, sesionService.usuarioActual(username), true);
        pedidoRepository.save(pedido);
        sincronizarConOdoo(pedido);
        return PedidoResponse.desde(pedido, null, bultoRepository.countByPedidoId(pedidoId));
    }

    // ----- Bultos -----

    @Transactional(readOnly = true)
    public List<BultoResponse> listarBultos(Long pedidoId, Long paletId) {
        List<Bulto> bultos;
        if (pedidoId != null) {
            bultos = bultoRepository.findByPedidoId(pedidoId);
        } else if (paletId != null) {
            bultos = bultoRepository.findByPaletId(paletId);
        } else {
            bultos = bultoRepository.findAllByOrderByIdDesc();
        }
        return bultos.stream().map(BultoResponse::desde).toList();
    }

    @Transactional
    public BultoResponse crearBulto(BultoRequest datos) {
        Pedido pedido = pedidoRepository.findById(datos.pedidoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido no existe"));
        // Solo pedidos que ya superaron el pago y aún no han salido pueden tener bultos nuevos
        boolean preparacionValida = pedido.getEstado() == EstadoPedido.LISTO_PREPARAR
                || pedido.getEstado() == EstadoPedido.EN_PREPARACION
                || pedido.getEstado() == EstadoPedido.PREPARADO;
        if (!preparacionValida) {
            throw new ReglaNegocioException(pedido.getEstado() == EstadoPedido.EXPEDIDO
                    ? "El pedido ya está expedido: no admite bultos nuevos"
                    : "El pedido todavía no está listo para preparar");
        }
        // El registro manual no puede pasar de los bultos que exige el peso del pedido
        PesoPedido peso = PesoPedido.de(pedido);
        if (!peso.completo()) {
            throw new ReglaNegocioException("No se pueden registrar bultos: falta el peso de "
                    + String.join(", ", peso.productosSinPeso()));
        }
        long registrados = bultoRepository.countByPedidoId(pedido.getId());
        if (registrados >= peso.bultosCalculados()) {
            throw new ReglaNegocioException("El pedido ya tiene los " + peso.bultosCalculados()
                    + " bultos que le corresponden por su peso (" + peso.pesoTotalKg() + " kg)");
        }
        String identificador = datos.identificador().trim();
        if (bultoRepository.existsByIdentificador(identificador)) {
            throw new ReglaNegocioException("Ya existe un bulto con ese identificador");
        }
        Bulto bulto = bultoRepository.save(new Bulto(identificador, pedido));
        sincronizarConOdoo(pedido); // cambia el número de bultos que muestra Odoo
        return BultoResponse.desde(bulto);
    }

    /**
     * Elimina un bulto SOBRANTE (hay más registros que los calculados por peso). Nunca uno que
     * ya esté en un palet ni de un pedido expedido o cancelado: esos conflictos los resuelve
     * administración a mano (sacarlo del palet no existe a propósito).
     */
    @Transactional
    public void eliminarBulto(Long bultoId) {
        Bulto bulto = bultoRepository.findById(bultoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El bulto no existe"));
        Pedido pedido = bulto.getPedido();
        if (pedido.getEstado() == EstadoPedido.EXPEDIDO || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new ReglaNegocioException("No se puede eliminar un bulto de un pedido expedido o cancelado");
        }
        if (bulto.getPalet() != null) {
            throw new ReglaNegocioException("El bulto está en el palet " + bulto.getPalet().getIdentificador()
                    + ": no se puede eliminar");
        }
        PesoPedido peso = PesoPedido.de(pedido);
        long registrados = bultoRepository.countByPedidoId(pedido.getId());
        if (!peso.completo() || registrados <= peso.bultosCalculados()) {
            throw new ReglaNegocioException("Solo se pueden eliminar bultos que sobren respecto a los calculados por peso");
        }
        bultoRepository.delete(bulto);
        sincronizarConOdoo(pedido);
    }

    @Transactional
    public BultoResponse asignarPalet(Long bultoId, Long paletId) {
        Bulto bulto = bultoRepository.findById(bultoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El bulto no existe"));
        Palet palet = paletRepository.findById(paletId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El palet no existe"));
        if (palet.getFechaExpedicion() != null) {
            throw new ReglaNegocioException("El palet ya está expedido; no admite más bultos");
        }
        if (bulto.getPedido().getEstado() == EstadoPedido.EXPEDIDO) {
            throw new ReglaNegocioException("El pedido de este bulto ya está expedido");
        }
        // Coherencia: el palet debe ir a la misma salida que el pedido del bulto
        Destino salidaPedido = bulto.getPedido().getDestino();
        if (salidaPedido == null) {
            throw new ReglaNegocioException("El pedido #" + bulto.getPedido().getId()
                    + " aún no tiene salida asignada; asígnala o márcalo como preparado antes de paletizar");
        }
        if (!salidaPedido.getId().equals(palet.getDestino().getId())) {
            throw new ReglaNegocioException("El palet " + palet.getIdentificador() + " va a «" + palet.getDestino().getEtiqueta()
                    + "» y el pedido #" + bulto.getPedido().getId() + " sale por «" + salidaPedido.getEtiqueta() + "»");
        }
        if (bulto.getPalet() != null && bulto.getPalet().getId().equals(paletId)) {
            return BultoResponse.desde(bulto); // ya estaba en ese palet: no se duplica nada
        }
        bulto.asignarPalet(palet);
        return BultoResponse.desde(bultoRepository.save(bulto));
    }

    // ----- Palets -----

    @Transactional(readOnly = true)
    public List<PaletResponse> listarPalets(Long destinoId) {
        List<Palet> palets = (destinoId == null)
                ? paletRepository.findAllByOrderByIdDesc()
                : paletRepository.findByDestinoId(destinoId);
        return palets.stream().map(PaletResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public PaletResponse obtenerPalet(Long id) {
        return PaletResponse.desde(paletRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El palet no existe")));
    }

    @Transactional
    public PaletResponse crearPalet(PaletRequest datos) {
        if (paletRepository.findAll().stream().anyMatch(p -> p.getIdentificador().equals(datos.identificador()))) {
            throw new ReglaNegocioException("Ya existe un palet con ese identificador");
        }
        Destino destino = destinoRepository.findById(datos.destinoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El destino no existe"));
        return PaletResponse.desde(paletRepository.save(new Palet(datos.identificador(), destino)));
    }

    /** Marca un palet como expedido (sus bultos ya están en ruta). */
    @Transactional
    public PaletResponse expedirPalet(Long id) {
        Palet palet = paletRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El palet no existe"));
        if (palet.getFechaExpedicion() != null) {
            throw new ReglaNegocioException("El palet ya está expedido");
        }
        palet.marcarExpedido();
        return PaletResponse.desde(paletRepository.save(palet));
    }

    // ----- Destinos -----

    @Transactional(readOnly = true)
    public List<DestinoResponse> listarDestinos() {
        return destinoRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Destino::getNombre))
                .map(DestinoResponse::desde).toList();
    }

    /** Cambia nombre y código de una salida. El código es del negocio y no puede repetirse. */
    @Transactional
    public DestinoResponse actualizarDestino(Long id, DestinoRequest datos) {
        Destino destino = destinoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La salida no existe"));
        String codigo = normalizarCodigo(datos.codigo());
        if (codigo != null) {
            destinoRepository.findByCodigo(codigo).filter(otro -> !otro.getId().equals(id)).ifPresent(otro -> {
                throw new ReglaNegocioException("El código " + codigo + " ya lo usa la salida " + otro.getNombre());
            });
        }
        destino.setCodigo(codigo);
        destino.setNombre(datos.nombre().trim());
        return DestinoResponse.desde(destinoRepository.save(destino));
    }

    private String normalizarCodigo(String codigo) {
        return (codigo == null || codigo.isBlank()) ? null : codigo.trim();
    }

    @Transactional
    public DestinoResponse crearDestino(DestinoRequest datos) {
        if (destinoRepository.findAll().stream()
                .anyMatch(d -> d.getNombre().equalsIgnoreCase(datos.nombre()))) {
            throw new ReglaNegocioException("Ya existe ese destino");
        }
        String codigo = normalizarCodigo(datos.codigo());
        if (codigo != null && destinoRepository.findByCodigo(codigo).isPresent()) {
            throw new ReglaNegocioException("Ya existe una salida con el código " + codigo);
        }
        Destino nuevo = new Destino(datos.nombre().trim());
        nuevo.setCodigo(codigo);
        return DestinoResponse.desde(destinoRepository.save(nuevo));
    }
}
