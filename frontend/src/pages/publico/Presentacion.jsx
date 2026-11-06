import { Link } from 'react-router-dom'
import { useAuth } from '../../auth.jsx'
import { DESARROLLADOR, TECNOLOGIAS, emailValido, urlValida } from './datosDesarrollador.js'
import FormularioSolicitud from './FormularioSolicitud.jsx'
import '../../publico.css'

// Solo funciones que existen en la aplicación
const FUNCIONALIDADES = [
  ['Catálogo', 'Productos veterinarios con búsqueda, categoría, precio y stock disponible.'],
  ['Pedidos', 'Cada clínica prepara su carrito y crea sus pedidos; el servidor calcula precios e IVA.'],
  ['Autorización del veterinario', 'El veterinario asignado revisa el contenido y lo firma desde su cuenta.'],
  ['Firma, pago y estado', 'La clínica consulta por separado la firma, el pago y en qué punto está cada pedido.'],
  ['Preparación de almacén', 'Solo se prepara un pedido con firma válida y pago completo confirmado.'],
  ['Bultos y salidas', 'Los bultos se calculan por peso y cada pedido se asigna a una salida y a sus palets.'],
  ['Integración con Odoo', 'Los pedidos pagados se envían a Odoo Ventas sin duplicarse y con reintentos si falla.'],
]

const PASOS = [
  ['Solicitud de alta', 'La clínica envía sus datos y los de su veterinario responsable.'],
  ['Revisión', 'Una persona comprueba la solicitud.'],
  ['Aprobación', 'Se registran la clínica y su veterinario responsable.'],
  ['Activación del acceso', 'El veterinario recibe un enlace de un solo uso para elegir su contraseña.'],
  ['Uso del portal', 'La clínica ya puede pedir, firmar y consultar sus pedidos.'],
]

function EnlacesDesarrollador() {
  const { email, github, linkedin } = DESARROLLADOR
  const enlaces = [
    emailValido(email) && { texto: 'Correo', href: `mailto:${email}` },
    urlValida(github) && { texto: 'GitHub', href: github },
    urlValida(linkedin) && { texto: 'LinkedIn', href: linkedin },
  ].filter(Boolean)
  if (enlaces.length === 0) return null
  return (
    <p className="pub-enlaces">
      {enlaces.map((e) => (
        <a key={e.texto} className="btn btn-secundario" href={e.href} target="_blank" rel="noreferrer">{e.texto}</a>
      ))}
    </p>
  )
}

export default function Presentacion() {
  const { usuario } = useAuth()
  const portal = usuario ? { texto: 'Ir al portal', a: '/portal' } : { texto: 'Acceder', a: '/login' }

  return (
    <div className="publico">
      <a className="pub-saltar" href="#contenido">Saltar al contenido</a>
      <header className="pub-cabecera">
        <div className="pub-ancho pub-cabecera-interior">
          <Link to="/" className="pub-marca"><span aria-hidden="true">🐾</span> VetStock Manager</Link>
          <nav aria-label="Secciones de la página" className="pub-nav">
            <a href="#funcionalidades">Funcionalidades</a>
            <a href="#como-funciona">Cómo funciona</a>
            <a href="#desarrollador">Desarrollador</a>
            <a href="#solicitud">Solicitar alta</a>
          </nav>
          <Link className="btn btn-primario" to={portal.a}>{portal.texto}</Link>
        </div>
      </header>

      <main id="contenido">
        <section className="pub-hero">
          <div className="pub-ancho">
            <h1>Pedidos de productos veterinarios, del catálogo a la expedición</h1>
            <p className="pub-entrada">
              VetStock Manager reúne en un solo sitio lo que suele repartirse entre llamadas, correos y hojas de cálculo:
              qué ha pedido cada clínica, si el veterinario lo ha autorizado, si está pagado y en qué punto de la
              preparación o el envío se encuentra.
            </p>
            <p className="pub-acciones">
              <Link className="btn btn-primario btn-grande pub-boton-claro" to={portal.a}>{portal.texto}</Link>
              <a className="btn btn-secundario btn-grande" href="#solicitud">Solicitar alta de clínica</a>
            </p>
          </div>
        </section>

        <section id="funcionalidades" className="pub-seccion">
          <div className="pub-ancho">
            <h2>Funcionalidades</h2>
            <ul className="pub-rejilla">
              {FUNCIONALIDADES.map(([titulo, texto]) => (
                <li key={titulo} className="pub-tarjeta">
                  <h3>{titulo}</h3>
                  <p>{texto}</p>
                </li>
              ))}
            </ul>
            <p className="texto-suave">La pasarela de pago es de demostración y no realiza cobros reales. La aplicación Android está en desarrollo.</p>
          </div>
        </section>

        <section id="como-funciona" className="pub-seccion pub-seccion-alterna">
          <div className="pub-ancho">
            <h2>Cómo funciona</h2>
            <ol className="pub-pasos">
              {PASOS.map(([titulo, texto]) => (
                <li key={titulo}>
                  <h3>{titulo}</h3>
                  <p>{texto}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="desarrollador" className="pub-seccion">
          <div className="pub-ancho pub-desarrollador">
            {DESARROLLADOR.foto ? (
              <img className="pub-foto" src={DESARROLLADOR.foto} alt={`Fotografía de ${DESARROLLADOR.nombre}`} width="160" height="160" />
            ) : (
              <div className="pub-foto pub-foto-vacia" role="img" aria-label="Espacio reservado para la fotografía" />
            )}
            <div>
              <h2>Sobre el desarrollador</h2>
              <p className="pub-nombre">{DESARROLLADOR.nombre}</p>
              <p>{DESARROLLADOR.descripcion}</p>
              <ul className="pub-tecnologias" aria-label="Tecnologías del proyecto">
                {TECNOLOGIAS.map((t) => <li key={t}>{t}</li>)}
              </ul>
              <EnlacesDesarrollador />
            </div>
          </div>
        </section>

        <section id="solicitud" className="pub-seccion pub-seccion-alterna">
          <div className="pub-ancho pub-estrecho">
            <h2>Solicitud de alta de clínica</h2>
            <p>Envía los datos de la clínica y de su veterinario responsable. Una persona los revisará antes de dar de alta nada.</p>
            <FormularioSolicitud />
          </div>
        </section>
      </main>

      <footer className="pub-pie">
        <div className="pub-ancho">
          <p><strong>VetStock Manager</strong> · Proyecto académico de 2.º DAM · <Link to={portal.a}>Acceso al portal</Link></p>
          <h2 id="privacidad" className="pub-pie-titulo">Privacidad del formulario</h2>
          <p>Los datos del formulario se usan para revisar la solicitud y, si se aprueba, dar de alta la clínica y su veterinario
            responsable. Los consulta el personal autorizado de la distribuidora en VetStock Manager y en Odoo. No se usan con fines publicitarios.</p>
          <h2 className="pub-pie-titulo">Pendiente de completar antes de un despliegue público</h2>
          <p className="texto-suave">Esta es una versión académica, con datos ficticios y pasarela de pago de demostración. Antes de publicarla
            deben añadirse la identidad y el contacto del responsable del tratamiento, la base legal, el plazo de conservación,
            los derechos de las personas y el aviso legal.</p>
        </div>
      </footer>
    </div>
  )
}
