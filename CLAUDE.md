# Sendik — Instrucciones para el agente

Marketplace colombiano de moda nueva y de segunda, y de tecnología nueva. Lo
usado se vende solo en moda (RN-064). La plataforma cobra 5% al vendedor sobre el
valor del producto y actúa como respaldo de la transacción.

Este archivo es la única fuente de reglas que aplica a todo el repositorio. Es
corto a propósito. Los detalles viven en `docs/` y se leen bajo demanda.

## Idioma

- Conversación, commits, ADR y documentación: **español**.
- Código, nombres de clases, variables, ramas y rutas de API: **inglés**.
- Textos visibles al usuario: nunca en el código. Siempre clave de Transloco.
- El dominio se nombra con el glosario: `docs/producto/glosario.md`. Si un
  concepto no está ahí, no lo inventes: pregunta y agrégalo al glosario.

## Estructura

```
backend/    Java 25 + Spring Boot 4.1 + Gradle multi-módulo + PostgreSQL
frontend/   Angular 21 + SSR + Transloco + Vitest
docs/       producto, arquitectura, ui, marca, operacion, ia
.claude/    comandos, subagentes y hooks
```

Convenciones específicas de cada lado: `backend/CLAUDE.md` y `frontend/CLAUDE.md`.
Léelos antes de tocar código de ese lado.

## Arquitectura: la regla que no se negocia

Cuatro capas, dependencias siempre hacia adentro:

```
presentation ──> application ──> domain <── infrastructure
```

- `domain` no importa nada de Spring, Angular, JPA, HTTP ni de ninguna librería
  de infraestructura. Solo lenguaje estándar.
- `application` orquesta casos de uso y define **puertos** (interfaces).
- `infrastructure` implementa esos puertos (base de datos, Wompi, Typesense,
  almacenamiento, correo).
- `presentation` es entrada y salida (controladores REST, componentes Angular).

Detalle y ejemplos: `docs/arquitectura/vision-tecnica.md`.

Si una tarea parece exigir romper esta dirección, no la rompas: detente y
propón la alternativa.

## Versiones

La fuente de verdad es `backend/gradle/libs.versions.toml` y
`frontend/package.json`. Nunca supongas una versión: léela ahí.

Baseline del proyecto (agosto de 2026):

| Componente | Versión |
|---|---|
| Java | 25 (Temurin) |
| Spring Boot | 4.1.1 |
| Gradle | 9.x |
| PostgreSQL | 17 |
| Node | 22 LTS |
| Angular | 21.x con SSR e hidratación |
| Runner de pruebas web | Vitest vía `@angular/build:unit-test` |

Advertencia importante: buena parte del material de entrenamiento sobre Spring
Boot corresponde a 3.x y sobre Angular a las versiones 17 a 19. **Este proyecto
no es eso.** Antes de escribir código con una API que recuerdes, verifica contra
el código existente del repositorio. Las prohibiciones concretas están en
`backend/CLAUDE.md` y `frontend/CLAUDE.md`.

## Diseño

Los tokens visuales ya están definidos y auditados. `frontend/src/styles/tokens.css` es
**generado y de solo lectura**: nunca lo edites. Los componentes propios y los
ajustes de modo oscuro van en `frontend/src/styles/marca.css`.

- Ningún HEX, ningún píxel suelto, ningún `font-size` propio. Los colores y las
  medidas por variable; el texto por clase de rol de
  `frontend/src/styles/tipografia.css`, que es la única fuente de verdad del tipo.
- Si falta un color o una medida, el sistema está incompleto: se agrega con
  nombre en `marca.css` y se documenta.
- El acento bronce `--color-acento` aparece **una vez por pantalla** y siempre
  en lo mismo: la insignia de vendedor verificado, como línea de 2px y un icono.
  Nunca como relleno grande, nunca como color de texto. **El botón principal va
  en tinta, no en bronce.**
- El bronce tiene **dos tonos y no se cruzan**: `#8A6428` solo sobre fondo claro
  y `#B4884A` solo sobre fondo oscuro. Cruzarlos da 2.97:1. `tokens.css` alterna
  el correcto por modo; dentro de `.franja-tinta`, que es oscura en los dos
  modos, lo hace `marca.css`.
- La firma gráfica es el corte del isotipo, repetido fuera del logo como
  `.regla-corte`. Una sola vez por pieza.

## Seguridad

- Ningún secreto en el repositorio. Todo por variable de entorno.
- Ninguna URL, clave, correo, NIT o valor de negocio quemado en el código: todo
  parametrizable. Ver `docs/operacion/configuracion.md`.
- Toda entrada del usuario se valida en el borde y se vuelve a validar en el
  dominio.
- Datos personales: aplica la Ley 1581 de 2012. Lee
  `docs/operacion/datos-personales.md` antes de crear cualquier campo que guarde
  cédula, selfie, cuenta bancaria o dirección.

## Cómo trabajas en este repositorio

1. **Antes de escribir código, planea.** Para cualquier tarea que toque más de
   un archivo: propón el plan, espera aprobación, luego implementa.
2. **Una tarea, un alcance.** No refactorices de paso. No renombres lo que no te
   pidieron. No agregues dependencias sin proponerlas primero.
3. **Prueba lo que escribes.** Toda regla de negocio en `domain` o `application`
   llega con su prueba. Ver `docs/arquitectura/pruebas.md`.
4. **Si el contexto no cuadra con lo que ves en el código, gana el código.**
   Reporta la discrepancia en vez de asumir.
5. **No inventes reglas de negocio.** Si `docs/producto/reglas-negocio.md` no lo
   dice, pregunta.
6. **No toques** `frontend/src/styles/tokens.css`, `docs/marca/**`, archivos `.env` ni
   migraciones de Flyway ya aplicadas. Para cambiar el esquema se crea una
   migración nueva.

## Lo que nunca se hace

- Marcar una prueba como omitida o ignorada para que el build pase.
- Suprimir advertencias del compilador o del linter en lugar de corregirlas.
- Declarar que algo funciona sin haberlo ejecutado.
- Inventar endpoints, campos o nombres de dominio que no estén en `docs/`.
- Escribir texto visible al usuario directamente en una plantilla.
- Subir dependencias de versión mayor sin una ADR.

## Commits

Conventional Commits con descripción en español:

```
feat(auth): registrar vendedor con verificación de correo
fix(catalog): corregir cálculo de comisión sobre productos con descuento
docs(adr): ADR-0009 sobre estrategia de caché
```

Un commit por unidad lógica. No mezcles refactor con funcionalidad.

## Estado del proyecto

**La Fase 2 quedó cerrada el 5 de septiembre de 2026**: publicación, moderación,
catálogo, favoritos y panel del vendedor. Sus tres banderas están encendidas en
`dev` y apagadas en `prod`. La Fase 1 se cerró el 21 de agosto.

**La Fase 3 está en curso desde el 8 de septiembre de 2026** y arrancó por la
búsqueda: HU-014, contra PostgreSQL y no contra Typesense, que queda aplazado
detrás del mismo puerto (ADR-0035). Abrirla fue una decisión y no una
consecuencia de cerrar la anterior. **HU-014 quedó integrada el 10 de septiembre
de 2026**, con `FEATURE_SEARCH` sumada a las tres de la Fase 2 en `dev` y apagada
en `prod`: allí son cuatro banderas, no tres. Dentro de la fase el orden lo mandan las
dependencias: **el pago espera a Wompi y el envío espera las cuatro respuestas de
Skydropx, así que nada de pagos ni de envíos se implementa todavía**.

**HU-015, el carrito, quedó integrada el 10 de septiembre de 2026**, y con ella
`FEATURE_CHECKOUT` —que estaba declarada y apagada desde la Fase 1— pasa a tener
algo detrás. Se implementó **partido**: reunir productos y sumar su precio base no
depende de Wompi ni de Skydropx; el proceso de compra sí, y espera. El carrito
termina donde empieza el pedido, **no reserva nada** (RN-089) y **no muestra
total**, porque el total son las tres cifras de RN-076 y el envío todavía no
existe (RN-096). Trajo nueve reglas nuevas, RN-089 a RN-097, y dos decisiones:
ADR-0036 y ADR-0037.

**HU-016, la dirección de entrega, quedó integrada el 11 de septiembre de 2026**,
detrás de la misma bandera. Es la mitad de «el proceso de compra» que no espera a
nadie, partida con el criterio que ya había partido el carrito: guardar a dónde
quiere alguien que le llegue lo que compre no necesita a Wompi ni a Skydropx, y
además era la única pieza que **produce** el dato que las demás consumen. El
municipio se elige de la división político-administrativa del DANE y no se
escribe (RN-100); guardar una dirección **no promete que se pueda entregar allí**
(RN-103). Trajo siete reglas, RN-098 a RN-104, y dos decisiones: ADR-0039 y
ADR-0040. Lo que sigue esperando es crear el pedido y el paso a pagar.

La Fase 4 sigue sin abrir. El alcance por fase está en
`docs/producto/alcance.md`.

Lo que bloqueaba el lanzamiento dejó de hacerlo: los **textos legales** están
publicados y vigentes en `dev` desde el 8 de septiembre —versión `2026-09-08b`—
y el **correo transaccional** sale y se comprobó de punta a punta ese mismo día.
Lo que sigue abierto es otra cosa: `prod` no se ha desplegado nunca, sus
versiones legales siguen en `borrador-local`, la entrega del correo allí está sin
comprobar y las tres banderas de la Fase 2 siguen apagadas
—`docs/operacion/entornos.md`—.
