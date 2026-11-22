# OdisseyStaff

Plugin de staff para Paper 1.21 (Java 21). El plugin funciona dentro del
servidor y puede publicar el registro de baneos en la web usando una clave
privada que se configura solo en el servidor.

## Baneos con diálogos y página web

En Paper 1.21.7 o posterior, `/ban` abre un diálogo con los jugadores en línea.
Al elegir uno, aparece el formulario de duración (minutos, horas o días) y
motivo. El diálogo también ofrece plantillas rápidas; si eliges una y dejas
vacío el tiempo o el motivo, usa los valores de esa plantilla. Puedes editar
las opciones en `ban-dialog.presets` de `config.yml`. `/ban <jugador> <tiempo> <motivo>`
y `/staffban` siguen disponibles.
La página pública está en `/baneos`.

### Permisos por acción

Puedes asignar permisos separados con LuckPerms: `odissey.staff.ban`,
`odissey.staff.unban`, `odissey.staff.mute`, `odissey.staff.inventory` y
`odissey.staff.data`. Cubren banear, desbanear, silenciar/expulsar, ver
inventarios y consultar fichas, historiales y evidencias. Por ejemplo:

```text
/lp group moderador permission set odissey.staff.ban true
/lp group moderador permission set odissey.staff.mute true
```

El permiso existente `odissey.staff` conserva acceso general y hereda los cinco
permisos, así que los rangos actuales no pierden acceso. Un rango con permisos
individuales puede usar esas acciones sin recibir el panel completo. Los
comandos `/ban`, `/unban`, `/mute`, `/staffinvsee` y las consultas de datos
respetan sus permisos correspondientes.

Para sincronizar el registro, ejecuta `supabase/08-baneos.sql` en el editor SQL
del proyecto Supabase. Luego, en `plugins/OdisseyStaff/config.yml`, configura:

```yaml
web-ban-sync:
  enabled: true
  supabase-url: https://TU-PROYECTO.supabase.co
  service-key: "CLAVE_PRIVADA_SOLO_DEL_SERVIDOR"
  server-id: odissey-main
```

Usa la clave privada/secret del servidor en ese archivo; no la pongas en el
sitio, en el repositorio ni en el JAR. La página tiene lectura pública y no
permite escritura desde el navegador. El registro se sincroniza al banear y al
usar `/unban`.

## Avisos de baneos en Discord

El plugin publica los baneos en un canal mediante un webhook. En `config.yml`, activa `integraciones.discord-activo` y pega la URL del webhook en `integraciones.discord-webhook`. El texto se cambia en `integraciones.discord-ban.mensaje`; admite `%jugador%`, `%staff%`, `%motivo%`, `%duracion%`, `%tiempo%`, `%servidor%` y `%url%`. Puedes desactivar solo los avisos de baneos con `integraciones.discord-ban.activo: false`.

## Herramientas de staff

`/staff` abre el panel de staff, la puerta a todas las herramientas (mas abajo).
`/staffitems` da o quita solo las cinco herramientas de la barra rapida (slots 1,
3, 5, 7 y 9), sin pasar por el menu: es lo que hacia `/staff` antes.

Cada boton lleva su estado a la vista, para no tener que probar:

```
                    ╔══ PANEL DE STAFF ══╗

     🎒 Objetos de staff  👻 Vanish   ⚙ Rango y color
      (puestos / no)      (on / off)

     👤 Ficha de jugador 👁 Espiar    🔎 Cuentas alternativas

     ⚖ Sanciones  🔇 Silenciar  ❄ Congelar  ⛓ Celdas

     💬 Chat de un jugador 📞 Llamar al staff 👁 Lista de vigilancia

                        ✖ Cerrar
```

Los tres botones que piden un dato cierran el menu y esperan a que escribas en
el chat. Ese mensaje **no sale** por el global: se queda como respuesta y el
menu se vuelve a abrir.

Los botones que ya son un comando entero (silenciar, celdas) te enseñan el
comando en vez de montarte un formulario a medias: un formulario que solo sabe
hacer el caso fácil esconde el caso difícil, y el caso difícil es el que importa
cuando hay un jugador blocked en medio de una disputa.

## Cómo están hechos todos los menus

**Todos son de 54 huecos (seis filas) con el borde negro.** Sin excepciones. Eso
se decide en un unico sitio (`MenuBase.marco()`) y todos pasan por ahi. Antes
cada uno pintaba su propio borde y eran cuatro versiones distintas de lo mismo:
es exactamente como un menu se queda con el borde a medias o con el interior sin
rellenar y parece que le falta algo.

```
 0..8    █  borde negro
 9 y 17  █  columnas laterales
10..16   ·  relleno gris claro, contenido
   ...      (y asi las cuatro filas del interior)
44 y 36  █
45..53   █  borde negro
```

El relleno gris del interior no es adorno: al pulsar un hueco vacio se ve que el
menu esta tapado, no que le falte un item.

**Reglas que siguen todos:**

- El borde va **antes** de escribir nada. Si se pusiera despues, al rellenar los
  54 huecos borraria todo lo que se acabara de escribir encima.
- Los botones van en los 28 huecos del interior, en rejillas centradas. Con siete
  huecos por fila, cuatro centrados van en 11, 12, 14 y 15.
- **La cabecera va en el 13**, centro de la primera fila: de quien es el menu o
  a quien va dirigido.
- **Los controles van en la fila de abajo**: cerrar en el 49, paginacion en
  48/50 y contador de pagina en el 53. Siempre en esos sitios, para que el
  pulgar aprenda donde esta.
- Cada boton lleva **su estado a la vista** (llevas las herramientas, estas en
  vanish, cuantos hay sancionados). Un menu que obliga a probar para saber como
  estas es un menu que se usa mal.
- El boton se reconoce por una **etiqueta dentro del item**, nunca por su
  nombre: un jugador o un mundo llamados "cancelar" no pueden dispararlo.
- Click y arrastre se cancelan **siempre**. Sin eso, shift-click vacia el menu
  y el staff se lleva los items dentro.

`MenuBase` cancela ademas los clicks que llegan de otro plugin (compara por
identidad del inventario, no por titulo), asi que un cofre de otro plugin con el
mismo nombre no se toca.

## El modo espiar

Se entra desde `/staff` → **Espiar**. Con varios jugadores conectados sale un
selector con sus caras; con uno solo se entra directo.

Espectral es mirar a alguien en cuatro cosas a la vez:

| | |
|---|---|
| **Vista** | El staff entra en modo espectador con el objetivo encima. El jugador no ve un cuerpo extra, porque en espectador no hay cuerpo. Si cambia de dimensión, se le lleva detrás. |
| **Panel en vivo** | Ubicación (mundo, X/Y/Z, orientación, bioma), vida, comida, aire, experiencia, efectos de poción, mano principal, mano secundaria, armadura, lo último que ha dicho y lo último que ha hecho, y su historial. Se refresca solo cada medio segundo. |
| **Chat** | Lo que dice le llega al staff al chat con `[espía]` delante, aunque tenga el menu cerrado. Se ve **antes** de mirar silencios y antid-spam, para que el staff vea también lo que se va a cortar. |
| **Limpieza** | Si el objetivo se desconecta, se le suelta a todos los que lo estaban mirando y al panel se cierra. |

**Lo que NO espía**: mensajes privados ni ventas. El chat privado lo lleva el
plugin que lo implemente (essentials, towny...) y las ventas el de la economy.
Ningún listener de este plugin los ve nunca, asi que prometerlos seria
prometer algo que no llega.

**Cerrar el panel no es soltar el espiado.** Cerrar un cofre para ir al
inventario es normal, y reabrirselo encima cada medio segundo haria el espiado
inservible. El panel vuelve con `/staffespiar` o con el boton del panel de
staff; para soltar de verdad, el boton rojo o `/staffespiar off`.

`/staffspectate` es ahora un atajo de esto. Antes tenia su propio modo
espectador, sin registro: el staff veia a alguien pero no le llegaba su chat.
Dos sistemas para lo mismo, y cada uno con sus fallos.

El panel se actualiza solo pero no cada tick: compara item a item y solo manda al
cliente los que han cambiado de verdad (la vida, el sitio, el item de la mano).
Sin eso, veinte veces por segundo llegaria el menu entero y los iconos
brillarian sin parar.

**Herramientas de staff**

`/staffitems` no te activa nada: te reparte cinco items en los slots 1, 3, 5, 7 y
9 de la barra rapida, y cada uno hace una cosa. El staff decide cuando usarlos en
vez de llevarlos puestos.

| Item | Al hacer click | Que hace |
|---|---|---|
| **Perla de ender** | En el aire | Te teletransporta a un jugador conectado al azar. Agachado + click derecho abre el menu de jugadores conectados. Si no hay ninguno, avisa y no hace nada. No se puede lanzar. |
| **Cofre** | Derecho a un jugador | Abre su inventario. |
| **Bloque de hielo** | Derecho a un jugador | Lo congela (oscuridad infinita, sin movimiento, aviso en el chat). Vuelve a clicar para soltarlo. Si tiene una sancion activa, no se puede soltar con el bloque. |
| **Pocion** | En el aire | Alterna el vanish: invisible / visible. |
| **Vara de blaze** | Derecho a un jugador | Lo manda a su cama, o al spawn del mundo si no tiene ninguna. Le quita el congelado si lo tenia. |

Los items no se pueden mover de su ranura, tirar al suelo ni pasar a la otra
mano. Con `items.persistentes: true` se guardan y vuelven solas al morir y al
reconectar (solo a quien es staff de verdad). Con `false` se pierden.

Para quitarle el congelado a alguien sin item: `/freeze <jugador>` (alterna).

**Comandos**

| Comando | Que hace |
|---|---|
| `/staff` | Abre el panel de staff |
| `/staffitems` | Da o quita solo las cinco herramientas |
| `/staffespiar [jug\|off]` | Entra, vuelve al panel o suelta el espiado |
| `/vanish [on\|off]` | Invisible para el resto de jugadores |
| `/staffchat <msg>` (alias `sc`) | Habla solo con el staff (queda guardado para `/staffchatlog`) |
| `/freeze <jugador>` | Congela y descongela |
| `/staffwarn <jug> <motivo>` (alias `warn`) | Aviso con sonido |
| `/staffmute <jug> [7d\|perm] <motivo>` (alias `mute`) | Silencio con caducidad o permanente |
| `/staffban <jug> [7d\|perm] <motivo> PERM` (alias `ban`) | Ban temporal o permanente. El permanente hay que confirmarlo escribiendo `PERM` al final |
| `/ipban <jugador|ip> [tiempo|perm] <motivo>` | Banea la IP actual o la última conocida; el permanente pide `PERM` |
| `/ipmute <jugador|ip> [tiempo|perm] <motivo>` | Silencia la IP actual o la última conocida |
| `/staffkick <jug> <motivo>` (alias `kick`) | Expulsion |
| `/stafftools <color> [nombre]` | Tu rango delante del nombre en el chat |
| `/staffspectate <jug>\|off` | Atajo de `/staffespiar` |
| `/staffrespawn <jug>` | Mata y reaparece a un jugador atascado |
| `/staffcall` | Pide ayuda al resto del staff conectado |
| `/staffnota <jug> [borrar <id>\|<texto>]` | Notas internas (el jugador no se entera) |
| `/staffvigilar <jug> <motivo>\|off` | Lista de vigilancia: avisa al staff cuando entra |
| `/staffjaula <jug> <celda> <tiempo> [motivo]` | Celda con reloj, en un punto del config |
| `/staffbloquear <jug> <cmd>\|off\|all` | Le prohibe un comando a un jugador |
| `/staffsay [segundos] <msg>` | Mensaje grande en el centro de la pantalla de todos |
| `/staffactionbar [segundos] <msg>` | Mensaje en la barra superior (action bar) de todos |

**Consultas**

| Comando | Que hace |
|---|---|
| `/staffinfo <jug>` | Ficha completa: cuenta, staff, IP, silencio, congelado, notas, sanciones |
| `/staffhistory [jug]` | Historial de sanciones del jugador o del servidor |
| `/history <jug>` | Lo mismo, en un menu: quién hizo qué y cuándo |
| `/banear <jug>` | Menu de baneo: tiempo con botones y motivo escrito |
| `/staffunmute <jug>` | Levanta un silencio |
| `/unban <jug>` | Levanta un ban activo |
| `/unwarn <jug> [id]` | Retira el aviso activo más reciente o uno concreto |
| `/checkban <jug>` | Consulta ban activo, motivo, staff y vencimiento |
| `/checkmute <jug>` | Consulta silencio activo, motivo y vencimiento |
| `/warnings <jug>` | Lista los avisos activos y cómo retirar uno |
| `/banlist` | Lista los bans activos |
| `/mutelist` | Lista los silencios activos |
| `/stafffrozen` | Quien esta congelado ahora mismo |
| `/staffalt <jug>` | Cuentas alternativas: misma IP, nombres parecidos, horas jugadas |
| `/staffonline [horas]` | Quien ha estado conectado en las ultimas horas |
| `/staffinvsee <jug>` | Inventario de un jugador, conectado o no |
| `/staffsnapshot <jug> [min]` | Bloques, comandos y chat de los ultimos minutos |
| `/staffchatlog [jug] [min] [palabra]` | Historial del chat de jugadores o del canal de staff |
| `/evidence add <jug> <url> [nota]` | Adjunta una prueba enlazada a una cuenta |
| `/evidence view <jug>` | Consulta pruebas adjuntas |
| `/evidence capture <jug>` | Guarda una captura del inventario y estado actual (conectado) |

**Menus (GUI)**

| Comando | Que hace |
|---|---|
| `/staffsancion` | Sancion en cuatro pasos: motivo, jugador, tiempo, destino, congelado y modo de reloj |
| `/staffsancion <jug> off` | Levanta la sancion |
| `/staffsancion lista` | Sanciones en curso |
| `/staffrestaurar [copia]` | Vuelve atras en los archivos del plugin desde una copia de seguridad (solo `odissey.admin`) |

No hay comando de teletransporte: para moverse esta la perla de `/staffitems`
(a un jugador al azar, o el menu con agachado) y la vara de blaze, que manda a
OTRO jugador a su cama.

No hay `/staffmenu`: la ficha de cada jugador es un boton del panel.

## Copiar skin

`/staff` → **Copiar** abre un menu con la cabeza de todos los conectados. Al
pulsar una, el staff recibe una cabeza con esa piel: puede enseñarsela a otro
por el chat, dejarla como prueba en un reporte o guardarsela. Cada copia se
anota en la consola (`[skins] quien copio la de quien`).

**No cambia tu propia piel, y no se puede.** La skin de un jugador es suya y
esta atada a su cuenta de Mojang; un servidor no puede poner a uno la piel de
otro sin parcheos internos que se rompen en cada version. Lo que sale aqui es
una cabeza, que es lo que se puede hacer bien.

**En modo offline no va a funcionar de verdad.** Si el servidor esta en
`online-mode=false`, el servidor no recibe las texturas de nadie (no se las
manda la aplicacion), asi que la cabeza sale con el modelo por defecto aunque
esa persona tenga una skin propia. El menu lo avisa en la cabecera en vez de
dejar que el staff piense que el plugin esta roto.

Para saber en que modo estas, abre el menu de copiar: si la cabecera avisa de "Modo offline", es eso.

---

## Ghost mute

`/ghostmute <jugador> [tiempo|perm] <motivo>` bloquea el chat del jugador sin
mostrarle el aviso normal de silencio. Sus mensajes quedan guardados en el
registro y llegan al staff con el prefijo configurable `ghost-mute.staff-prefix`.
`ghost-mute.echo-to-player` controla si se le devuelve un eco de su propio
mensaje para que parezca publicado. Se puede levantar con `/staffunmute`.

`/mutechat [on|off]` pausa o abre el chat público; el estado se guarda en
`chat-control.muted` y el staff puede seguir hablando. `/clearchat` limpia las
ventanas de chat visibles de todos los conectados.

## Reportes y tickets

`/report <jugador> <motivo>` crea un reporte visible al staff, con enfriamiento
de cinco minutos por usuario. `/ticket create <mensaje>` abre una solicitud de
soporte (una abierta por jugador); el staff la administra con `/ticket list`,
`/ticket view <id>` y `/ticket close <id>`. Todo queda guardado en
`reportes.yml` o en el almacén configurado. Ajusta el enfriamiento con
`reportes.enfriamiento` y el límite de entradas con `reportes.max-registros`.

## Evidencias

`/evidence add` acepta enlaces HTTP/HTTPS de capturas, videos o replays con una
nota opcional. `/evidence capture` registra inventario, armadura, mano secundaria,
ubicación, vida, comida y nivel del jugador conectado; no toma una captura de
pantalla del cliente. Las entradas se guardan en `evidencias.yml` o en el almacén
seleccionado y se limitan con `evidencias.max-registros`. Si
`evidencias.capturar-al-banear` está activo, el plugin guarda automáticamente
el inventario de un objetivo conectado antes de expulsarlo por ban.
Los bans por IP usan la lista persistente de Paper. Los silencios por IP se
guardan en el almacén de sanciones y se aplican también a cuentas que entren
después desde esa dirección. `/unban <ip>`, `/unmute <ip>`, `/checkban <ip>` y
`/checkmute <ip>` consultan o levantan esas sanciones.

Los bans manuales están limitados por staff con `limites-sanciones.bans-por-hora`
y `limites-sanciones.permanentes-por-dia`; la confirmación `PERM` sigue siendo
obligatoria para bans permanentes.

## Avisos que se acumulan (warns)

Un aviso suelto no hace nada: el staff avisa, el jugador sigue igual y a la
cuarta vez ya nadie se cree que avisaron. Aqui cada aviso cuenta, y al llegar a
un numero el servidor hace algo solo.

Los escalones se configuran, no se compilan:

```yaml
warns:
  activo: true
  caducan-en-dias: 90
  niveles:
    - avisos: 1
      accion: aviso
      motivo: "Primer aviso."
    - avisos: 3
      accion: mute
      tiempo: "1h"
      motivo: "Tercer aviso."
    - avisos: 5
      accion: ban
      tiempo: "1d"
      motivo: "Quinto aviso."
```

Reglas:

- **Solo se aplica un escalon, el mas alto.** Si lleva 7 avisos y hay escalones
  en 3 y en 5, salta el de 5. Aplicar los dos seria silenciarle y banearle en el
  mismo clic.
- **No se repite.** Si le quitas avisos y luego los recupera, el escalon no
  vuelve a dispararse hasta que pase de numero.
- **Un ban sin tiempo no se aplica solo** (seria permanente sin querer). Avisa
  al staff y se decide a mano.
- **Los avisos caducan** a los 90 dias por defecto. Un jugador que lleva anos
  cumpliendo no debe arrastrar un aviso viejo para siempre; sin caducidad el
  sistema acaba quejandose solo y el staff deja de mirarlo.

Al dar un aviso, el staff ve cuantos lleva y cuantos le faltan para el
siguiente escalon. Y `/history <jugador>` los enseña todos.

## Historial en menu

`/history <jugador>` abre el historial en un cofre en vez de en el chat:
cuantas veces de cada tipo, quien lo hizo y cuando. `/staffhistory` se queda
como comando de texto, porque para pegar una captura en Discord un menu no
sirve.

Las sanciones no se "deshacen" desde ahi: se levantan con su comando
(`/staffunmute`, `/staffsancion <nombre> off`), que deja anotado quien lo
levanto. Un historial que se puede reescribir a un click no vale como registro.

## Baneo limpio

`/banear <jugador>` (o el boton del menu de sanciones) abre un menu donde el
tiempo son botones: 30m, 1h, 6h, 12h, 1d, 7d, 30d y permanente.

El motivo se escribe en el chat y **sin motivo no se banea**. Un ban sin motivo
es un ban que dentro de un mes nadie sabe por que se puso.

El permanente pide escribir `PERM` por escrito, antes de aplicarlo. El motivo
que se guarda va con la marca `[OdisseyStaff]` delante, que es lo unico que
permite saber despues que lo puso el plugin y no un admin a mano, y por tanto lo
unico que permite levantarlo sin tocar los baneos de los demas.

El motivo se guarda en `historial.yml` con su duracion exacta.

## Chequeo de IP y cuentas alternativas

Ya existia y sigue: al entrar un jugador se mira su IP contra los nombres
baneados y se avisa al staff. Lo que he reforzado es que **ahora tambien mira la
lista de baneos por IP**, no solo la de nombres. Antes, un baneo por IP (que es
justo lo que se pone cuando el del nombre no ha servido) no disparaba ni un
aviso. Se avisa solo al staff; al jugador nunca.

## El congelado

**Congelar es ahora exactamente lo mismo que sancionar, solo que con otro
nombre.** Antes no: el congelado solo bloqueaba el movimiento, así que podía
tirar perlas, abrir cofres, romper bloques, abrir puertas y usar `/rtp`,
`/home`, `/warp` y el resto de comandos de otros plugins. Es decir, era un
aviso con pasos. Ahora, congelado significa quieto y sin poder hacer nada:

| | |
|---|---|
| Moverse | No |
| Comandos | No (salvo login/register/changepassword) |
| Perla de ender, snowball, cualquier proyectil | No |
| Fruta de cronus, camas, cualquier teletransporte | No |
| Romper o colocar bloques | No |
| Abrir cofres, chests, baúl, inventario | No |
| Coger cosas del suelo | No |
| Interactuar (puertas, botones, vallas, hitos) | No |
| Hacer daño | Recibe, pero no hace |

El staff nunca se congela a sí mismo: si a un admin le pisan una sanción tiene
que poder usar `/staffsancion off` para sacársela, o se queda encerrado sin
salida.

**El bloque en la cabeza.** Al congelar se le pone un bloque de hielo, y al
soltar se le quita y vuelve lo que llevaba. Es lo que hace visible el congelado
desde lejos sin que el staff tenga que estar al lado.

Tres casos que se resolvieron por el camino:

- **El casco original no se pierde.** Si lo tenian puesto, se guarda y se
  devuelve. Va también al archivo, así que sobrevive a la desconexión.
- **Al morir no cae al suelo.** El hielo es un item normal y se caería con todo
  lo demás, dejando un bloque tirado en el mapa.
- **Si alguien se lo quita a mano, vuelve.** Un reloj lo repone cada segundo.
  Solo toca la cabeza si lo que hay es nuestro hielo: si alguien ha puesto otra
  cosa encima, no se la quita.

**Desconectarse no te libera.** El estado va a `congelados.yml` con la posición,
el modo de juego, la velocidad y el vuelo que tenía. Al volver le devuelves al
mismo sitio, con el hielo puesto, y sigue sin poder hacer nada. Reiniciar el
servidor tampoco.

**El staff que lo congela ve por qué.** `/stafffrozen` lista quién, desde cuándo
y por qué motivo, con el nombre de quien lo hizo.

## Instalacion

1. Copia `build/OdisseyStaff-7.2.0.jar` en `plugins/`.
2. Arranca el servidor una vez para que cree `plugins/OdisseyStaff/`.
3. Ya esta: no hay ningun secreto que sincronizar con la web.

## Como funciona el sistema de sanciones

Es distinto de un ban normal y a proposito:

- El sancionado **se queda dentro del servidor**, congelado, a oscuras y con un
  titulo en pantalla que le dice cuanto le queda, y con un bloque de hielo en
  la cabeza.
- **No puede hacer nada**, igual que un congelado con el bloque de hielo: ni
  comandos, ni cofres, ni bloques, ni proyectiles, ni teletransportes. Se cortan
  en `PlayerCommandPreprocessEvent`, `InventoryOpenEvent`, `InventoryClickEvent`,
  `PlayerAttemptPickupItemEvent`, `BlockBreakEvent`, `BlockPlaceEvent`,
  `EntityDamageByEntityEvent` y `PlayerInteractEvent`. Los de autenticacion
  (`login`, `register`, `changepassword`) se dejan: sin ellos un sancionado con
  plugin de registro se queda encerrado sin poder hacer nada.
- **No se escapa teletransportandose**: una sancion no es un aviso. Se cortan la
  perla de ender, el fruit de cronus, las camas, cualquier proyectil y, en
  general, cualquier `PlayerTeleportEvent` que no venga del propio plugin.
- El reloj tiene dos modos, elegibles en el menu:
  - **Pausa** (por defecto en `/staffsancion <jug> <tiempo> <motivo>`): el
    tiempo se para si el jugador se desconecta y sigue al volver.
  - **Real** (por defecto en el menu): el tiempo corre aunque salga. Ademas se
    le pone un ban temporal con marca `[OdisseyStaff]` para que no pueda
    desconectarse y esquivarla; el ban lo levanta solo el plugin al cumplirse, o
    `/staffsancion <jug> off`.
- El estado se guarda en `sanciones.yml`, asi que reiniciar el servidor no le
  regala la sancion a nadie.

Los silencios de /staffmute van aparte: caducan solos aunque el servidor
reinicie, y avisan al staff cuando se acaban.

Los alias `/tempban <jugador> <tiempo> <motivo>` y
`/tempmute <jugador> <tiempo> <motivo>` requieren tiempo explícito.

`/unban <jugador>` ya está registrado y levanta el ban. Si el plugin corre en
Paper detrás de Velocity o BungeeCord, `proxy-kick.enabled: true` envía el kick
al proxy con motivo, duración y staff, para que desconecte la conexión en vez
de mandarla al lobby. Velocity debe tener habilitado
`advanced.bungee-plugin-message-channel` en `velocity.toml`.

## Redención

Un jugador con una sanción activa puede usar `/redemption start` para iniciar
un reto de cálculo y `/redemption answer <resultado>` para responder. Si acierta,
se descuentan 10 minutos de su sanción. El cooldown predeterminado es 24 horas;
el límite de intentos, el tiempo descontado y el uso único se configuran bajo
`redemption` en `config.yml`.

## Avisos externos

Las integraciones opcionales `integraciones.discord-activo` y
`integraciones.telegram.activo` envían avisos de sanciones de forma asíncrona.
Configura el webhook/token y el identificador de chat en `config.yml`; están
desactivadas por defecto.

## GeoIP y VPN

Activa `geoip.enabled` para permitir `/geoip <jugador|ip>` y
`/vpncheck <jugador|ip>`. La consulta se realiza en segundo plano y se cachea
localmente; al habilitarla, la IP se envía a ipwho.is. El plan gratuito entrega
geolocalización, pero la detección VPN puede no estar disponible en ese plan.
`/network alts <jugador>` y `/network connections <jugador>` consultan las
cuentas vistas desde la misma IP y los datos de conexión guardados localmente.

## Capturas automáticas de chat

Al aplicar un mute o ban, OdisseyStaff puede guardar como evidencia hasta 20
mensajes de los 20 minutos anteriores. Se ajusta con `chat-snapshots` y se
consulta mediante `/evidence view <jugador>`; se puede desactivar allí.

## CoreProtect

Con CoreProtect instalado, `/staffrollback preview <jugador-online> <segundos>
<radio>` consulta los cambios de esa cuenta alrededor de su posición. El mundo
no se modifica hasta que un admin confirme con `/staffrollback confirm` dentro
de 60 segundos. Los máximos se configuran en `coreprotect`; requiere
`odissey.admin`.

## Análisis de moderación con IA

Configura `ai.api-key` y habilita `ai.enabled` para usar
`/ai analyze <jugador>` o `/ai suggest <jugador>`. El comando envía los mensajes
recientes guardados a OpenAI y presenta una recomendación manual: nunca sanciona
por sí solo. Limita el contexto con `ai.window-minutes` y `ai.max-messages`.

La integración usa OpenAI Responses (`POST /v1/responses`); `/ai` solo hace
la llamada cuando el staff la ejecuta y la opción está habilitada.

## Borrado de datos

Con `data-wipe.enabled: true`, `/staffdatawipe preview <jugador>` muestra los
archivos vanilla de datos, estadísticas y avances que existen para esa cuenta.
El jugador debe estar desconectado; un admin confirma con
`/staffdatawipe confirm` dentro de 60 segundos. No se ejecuta como parte de un
ban y no borra datos propios de plugins.

## Copias de seguridad

Antes de pisar cualquier archivo del plugin (sanciones, historial, notas,
silencios, vigilancia, congelados, bloqueos, conexiones, chat) se copia el
anterior a `plugins/OdisseyStaff/copias/`. Se guardan las 10 ultimas de cada
archivo. `/staffrestaurar` las lista y las repone, recargando el store
correspondiente.

Ojo: esto **no es un backup de verdad**. Todo sigue en el mismo disco, asi que
protege de un "lo he borrado sin querer" pero no de que se muera el servidor.
Para eso, copia `plugins/OdisseyStaff/` con tu propia periodicidad.

## Bases de datos

`database.type` admite `yaml`, `sqlite`, `h2`, `mysql`, `mariadb` y
`postgresql`. Los almacenes existentes conservan su formato, así que reportes,
evidencias, silencios por IP e historial usan el mismo motor. Al cambiar desde
YAML a una base vacía, cada archivo se importa y se conserva como copia. H2
guarda su archivo dentro de la carpeta del plugin; MySQL/MariaDB y PostgreSQL
usan los datos de conexión de su sección en `config.yml`.

## Antispam

Corta a quien repita el mismo texto, meta demasiados mensajes seguidos o
escriba a gritos. Cuando se pasa del limite se le silencia un rato y se avisa
al equipo. Todo lo que dice la gente se guarda igual (o acortado) en
`chat.yml`, para poder consultarlo con `/staffchatlog`.

El antid-spam decide en el hilo del chat (que en Bukkit no es el principal) y
aplica el silencio y los avisos desde el hilo principal, escribiendo en disco
y hablando con los jugadores conectados.

## Permisos

| Permiso | Por defecto | Para que |
|---|---|---|
| `odissey.staff` | op | Acceso a las herramientas de staff |
| `odissey.admin` | op | Sancionar a otro miembro del staff, restaurar copias de seguridad |

Con `usar-op-como-staff: false` en el `config.yml`, el acceso es solo por
permiso (util si usas LuckPerms).

Nadie puede applicar una accion a si mismo. Para tocar a otro staff hace falta
`odissey.admin`, y a un admin no le toca ni un admin.

## Compilar

```powershell
$libs = Get-ChildItem 'D:\Servidor 2\libraries' -Recurse -Filter *.jar | % { $_.FullName }
$env:CLASSPATH = ($libs -join ';')
Remove-Item -Recurse -Force build\classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path build\classes | Out-Null
javac -encoding UTF-8 --release 21 -d build\classes (Get-ChildItem src -Recurse -Filter *.java | % { $_.FullName })
Copy-Item plugin.yml,config.yml build\classes
& "C:\Program Files\Java\jdk-21.0.11\bin\jar.exe" --create --file build\OdisseyStaff-7.4.0.jar -C build\classes .
```

Hace falta Java 21.
