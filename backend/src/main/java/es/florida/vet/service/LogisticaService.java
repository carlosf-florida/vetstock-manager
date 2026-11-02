package es.florida.vet.service;

import es.florida.vet.dto.BultoRequest;
import es.florida.vet.dto.BultoResponse;
import es.florida.vet.dto.DestinoRequest;
import es.florida.vet.dto.DestinoResponse;
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

    public LogisticaService(BultoRepository bultoRepository, PaletRepository paletRepository,
                            DestinoRepository destinoRepository, PedidoRepository pedidoRepository) {
        this.bultoRepository = bultoRepository;
        this.paletRepository = paletRepository;
        this.destinoRepository = destinoRepository;
        this.pedidoRepository = pedidoRepository;
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
        // Solo pedidos que ya superaron el pago pueden tener bultos
        boolean preparacionValida = pedido.getEstado() == EstadoPedido.LISTO_PREPARAR
                || pedido.getEstado() == EstadoPedido.EN_PREPARACION
                || pedido.getEstado() == EstadoPedido.PREPARADO
                || pedido.getEstado() == EstadoPedido.EXPEDIDO;
        if (!preparacionValida) {
            throw new ReglaNegocioException("El pedido todavía no está listo para preparar");
        }
        if (bultoRepository.findAll().stream().anyMatch(b -> b.getIdentificador().equals(datos.identificador()))) {
            throw new ReglaNegocioException("Ya existe un bulto con ese identificador");
        }
        return BultoResponse.desde(bultoRepository.save(new Bulto(datos.identificador(), pedido)));
    }

    @Transactional
    public BultoResponse asignarPalet(Long bultoId, Long paletId) {
        Bulto bulto = bultoRepository.findById(bultoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El bulto no existe"));
        Palet palet = paletRepository.findById(paletId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El palet no existe"));
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

    @Transactional
    public DestinoResponse crearDestino(DestinoRequest datos) {
        if (destinoRepository.findAll().stream()
                .anyMatch(d -> d.getNombre().equalsIgnoreCase(datos.nombre()))) {
            throw new ReglaNegocioException("Ya existe ese destino");
        }
        return DestinoResponse.desde(destinoRepository.save(new Destino(datos.nombre())));
    }
}
