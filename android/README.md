# VetStock para Android

App nativa en Java con vistas XML, para clínicas (auxiliar y veterinario). No es una WebView: usa la misma API REST que la web, y todas las reglas siguen en el backend.

## Qué hace

| Perfil | Funciones |
|---|---|
| Auxiliar y veterinario | Iniciar y cerrar sesión, catálogo con búsqueda, detalle, carrito, crear pedido (borrador o enviado a firma), sus pedidos y su estado |
| Veterinario | Pedidos que esperan su firma, revisar el contenido y firmar con el dedo |

Una cuenta de administración no entra: la app avisa de que se usa el portal web.

## Pago: alcance

La app **muestra** el estado del pago que confirma el servidor, pero **no permite pagar**. La única pasarela del proyecto es de demostración, y un pago real necesita un proveedor y un flujo externo que aún no están decididos. Por eso el pago se hace en el portal web. Integrarlo en Android es una fase posterior, que depende de elegir ese proveedor.

Tampoco incluye: rechazo de pedidos por el veterinario, modo sin conexión, notificaciones, geolocalización, escáner ni publicación en Google Play.

## Requisitos (versiones con las que se ha compilado)

- Android Studio (probado con la compilación AI-253) o solo el SDK de Android.
- SDK: plataforma `android-36.1` y build-tools 36 o superior.
- JDK 21 para ejecutar Gradle (el de Android Studio sirve). El código se compila con nivel Java 17.
- Gradle 9.4.1, con el wrapper incluido, y plugin de Android 9.2.0.
- `minSdk` 26 (Android 8), `targetSdk` 36.

La carpeta del proyecto contiene el carácter `º`, y el plugin de Android lo rechaza en Windows. Por eso `gradle.properties` activa `android.overridePathCheck=true`, una opción que Gradle marca como experimental. Con ella compila; si diera problemas, la solución es mover el proyecto a una ruta sin ese carácter.

## Abrirla y ejecutarla

1. Arranca el backend: `docker compose up -d` en la raíz del proyecto.
2. En Android Studio: **File → Open** y elige esta carpeta, `android/`. No abras la raíz del repositorio.
3. Espera a que termine la sincronización de Gradle. Crea `local.properties` con la ruta del SDK; ese archivo no se sube a Git.
4. Elige un emulador y pulsa **Run**.

Desde la terminal, dentro de `android/`:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Dirección del backend

La app no usa `localhost`: dentro de un móvil o un emulador, esa dirección es el propio dispositivo.

| Dónde se ejecuta | URL | Cómo se configura |
|---|---|---|
| Emulador | `http://10.0.2.2:8080` | Valor por defecto en debug |
| Móvil físico en la misma red Wi-Fi | `http://IP_DEL_ORDENADOR:8080` | `./gradlew assembleDebug -PvetstockApiUrl=http://IP_DEL_ORDENADOR:8080` |
| Producción | `https://…` | `-PvetstockApiUrlProduccion=https://…` |

HTTP solo se permite en la variante debug (`app/src/debug/res/xml/network_security_config.xml`). La variante release solo admite HTTPS y, sin URL configurada, avisa al iniciar sesión. Para usar un móvil físico, el cortafuegos de Windows debe permitir el puerto 8080.

## Cómo está hecha

```
es.florida.vetstock
├── api     ApiCliente (HttpURLConnection y org.json) y ApiException
├── modelo  Sesion, Carrito y Textos (estados y reglas de presentación)
└── ui      BaseActivity y una Activity por pantalla, FirmaView (lienzo)
```

Las únicas dependencias son `appcompat` y `recyclerview`.

- **Sesión:** la misma de la web, con la cookie `JSESSIONID`. Se guarda solo en memoria: la contraseña y la cookie no se escriben en el dispositivo y no hay JWT. Si Android cierra la app, hay que volver a entrar y el carrito se vacía.
- **Sesión caducada:** cualquier respuesta 401 lleva al inicio de sesión con un aviso.
- **Hilos:** la red va en segundo plano y la pantalla se actualiza en el hilo principal.
- **Peticiones repetidas:** los botones se desactivan mientras se envía, para no crear dos pedidos con una doble pulsación.
- **Fallo parcial:** si el pedido se crea pero falla el envío a firma, se abre como borrador con el aviso y el botón para reintentar.
- **Permisos:** el botón de firmar es solo comodidad. Quien decide es el backend, que comprueba que firma el veterinario asignado.

## Comprobado

Compilada y probada en el emulador Pixel 6 (Android 37). Resultados en `docs/pruebas.md`, apartado 2i. **No se ha probado** en un móvil físico, ni se ha generado una versión release, ni tiene pruebas automáticas propias.
