#!/usr/bin/env python3
"""Verifica el contraste del sistema en modo claro Y en modo oscuro.

    cd docs/ui/generador
    python3 verificar.py

Por que existe si kit_ui.py ya escribe contraste.md:

kit_ui.py comprueba los pares del MODO CLARO y solo los que el generador
conoce. El sistema real de Sendik tiene ademas:

  - la franja de tinta (`--color-tinta`), que no cambia con el modo y por eso
    necesita sus propios pares en los dos;
  - el bronce, que tiene DOS tonos —uno para fondo claro y otro para fondo
    oscuro— y falla si se cruzan;
  - los componentes propios y las correcciones de modo oscuro que viven en
    `frontend/src/styles/marca.css`, que el kit no ve.

Este script lee los colores reales de las tres hojas **tal como las sirve el
sitio**: `docs/ui/tokens.css` (recien generado) mas las dos hojas del proyecto
en `frontend/src/styles/`. Asi lo que se mide es la pantalla, no el kit.

No necesita nada instalado: solo Python 3.
Devuelve 0 si todo cumple y 1 si algo falla, para poder encadenarlo.
"""
import re
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
KIT = AQUI.parent                   # docs/ui
RAIZ = KIT.parent.parent            # raiz del repositorio
ESTILOS = RAIZ / "frontend" / "src" / "styles"

# El orden importa: es el mismo de src/styles.css, y la ultima gana.
HOJAS = [KIT / "tokens.css",
         ESTILOS / "tipografia.css",
         ESTILOS / "marca.css"]

AA_TEXTO = 4.5   # texto normal
AA_GRANDE = 3.0  # texto grande (>=24px, o >=18.66px en negrita) e iconos


# ---------------------------------------------------------------- color
def a_rgb(h):
    h = h.strip().lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def luminancia(rgb):
    def canal(v):
        v /= 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (canal(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contraste(a, b):
    la, lb = luminancia(a_rgb(a)), luminancia(a_rgb(b))
    alto, bajo = max(la, lb), min(la, lb)
    return round((alto + 0.05) / (bajo + 0.05), 2)


# ------------------------------------------------------- lectura del CSS
BLOQUE = re.compile(r"([^{}]+)\{([^{}]*)\}", re.S)
VARIABLE = re.compile(r"(--[\w-]+)\s*:\s*(#[0-9A-Fa-f]{3,8})\s*(?:;|$)")


def leer_variables(ruta):
    """Devuelve (claro, oscuro): las variables de color de :root y las que
    sobrescribe [data-tema="oscuro"]."""
    if not ruta.exists():
        sys.exit("No existe " + str(ruta) + ". Corre primero construir.py.")
    texto = ruta.read_text(encoding="utf-8")
    texto = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)   # fuera comentarios
    claro, oscuro = {}, {}
    for selector, cuerpo in BLOQUE.findall(texto):
        sel = selector.strip()
        destino = None
        if sel == ":root":
            destino = claro
        elif "data-tema" in sel and "oscuro" in sel:
            destino = oscuro
        if destino is None:
            continue
        for nombre, valor in VARIABLE.findall(cuerpo):
            destino[nombre] = valor
    return claro, oscuro


def paletas():
    """Arma la paleta final de cada modo respetando el orden de carga:
    tokens.css -> tipografia.css -> marca.css."""
    claro, oscuro = {}, {}
    for archivo in HOJAS:
        c, o = leer_variables(archivo)
        claro.update(c)
        oscuro.update(o)
    # el modo oscuro parte del claro y solo sobrescribe lo que redefine
    final_oscuro = dict(claro)
    final_oscuro.update(oscuro)
    return claro, final_oscuro


# ----------------------------------------------------------------- pares
# (descripcion, frente, fondo, umbral)
# Cada par corresponde a algo que de verdad se ve en pantalla. Si se anade un
# componente con una combinacion nueva, se anade aqui.
PARES = [
    ("Texto principal sobre el fondo",        "--color-texto",          "--color-fondo",          AA_TEXTO),
    ("Texto principal sobre tarjeta",         "--color-texto",          "--color-superficie",     AA_TEXTO),
    ("Texto secundario sobre el fondo",       "--color-texto-suave",    "--color-fondo",          AA_TEXTO),
    ("Texto secundario sobre tarjeta",        "--color-texto-suave",    "--color-superficie",     AA_TEXTO),
    ("Enlace y estructura sobre el fondo",    "--color-primario",       "--color-fondo",          AA_TEXTO),
    ("Texto del boton primario",              "--color-sobre-primario", "--color-primario",       AA_TEXTO),
    ("Etiqueta sobre fondo de etiqueta",      "--color-texto",          "--color-primario-suave", AA_TEXTO),
    ("Mensaje de exito sobre tarjeta",        "--color-exito",          "--color-superficie",     AA_TEXTO),
    ("Mensaje de aviso sobre tarjeta",        "--color-aviso",          "--color-superficie",     AA_TEXTO),
    ("Mensaje de error sobre tarjeta",        "--color-error",          "--color-superficie",     AA_TEXTO),
    # Y los mismos sobre el fondo de pagina, que es donde caen en las pantallas de
    # publicacion: alli el mensaje no vive dentro de una tarjeta.
    ("Mensaje de aviso sobre el fondo",       "--color-aviso",          "--color-fondo",          AA_TEXTO),
    # HU-003: el nivel del asistente de captura dice "Nivelado" en verde sobre el fondo de
    # pagina. El exito estaba solo como BORDE a 3:1; como texto no se comprobaba.
    ("Mensaje de exito sobre el fondo",       "--color-exito",          "--color-fondo",          AA_TEXTO),
    ("Mensaje de error sobre el fondo",       "--color-error",          "--color-fondo",          AA_TEXTO),
    ("Anillo de foco sobre el fondo",         "--color-foco",           "--color-fondo",          AA_GRANDE),
    ("Anillo de foco sobre tarjeta",          "--color-foco",           "--color-superficie",     AA_GRANDE),
    ("Borde de campo de formulario",          "--color-borde-control",  "--color-superficie",     AA_GRANDE),
    # HU-008. La confirmacion y las marcas de la cola son cajas con borde de control que se
    # apoyan sobre el fondo de PAGINA, no dentro de una tarjeta.
    ("Borde de control sobre el fondo",       "--color-borde-control",  "--color-fondo",          AA_GRANDE),
    # HU-014. La ficha de filtro puesto cambia de relleno al pasar el raton, y su borde deja
    # de estar sobre el fondo de pagina. Con el borde de control encima daba 2.96:1, del
    # mismo orden que el bronce cruzado que el manual prohibe, asi que la ficha cambia
    # tambien de borde y el par que hay que vigilar es este.
    ("Borde de ficha de filtro en hover",     "--color-primario",       "--color-primario-suave", AA_GRANDE),
    # Y el aviso de "esta publicacion es tuya", que lleva el borde del boton secundario.
    ("Borde de aviso propio sobre tarjeta",   "--color-primario",       "--color-superficie",     AA_GRANDE),
    # Las cajas de aviso y de error llevan fondo de tarjeta pero se apoyan sobre
    # el fondo de pagina: su borde linda con los dos y es informacion no textual.
    ("Borde de aviso sobre el fondo",         "--color-aviso",          "--color-fondo",          AA_GRANDE),
    ("Borde de error sobre el fondo",         "--color-error",          "--color-fondo",          AA_GRANDE),
    ("Borde de exito sobre el fondo",         "--color-exito",          "--color-fondo",          AA_GRANDE),
    # El boton secundario solo se identifica como control por su borde: eso es
    # informacion no textual y va contra el fondo de PAGINA, que es donde se apoya.
    ("Borde de boton secundario sobre el fondo", "--color-primario",    "--color-fondo",          AA_GRANDE),

    # ---- El bronce. Dos tonos, cada uno con su fondo, y nunca al reves ----
    # Sobre fondo claro va --color-acento (que en modo claro ES el bronce
    # oscuro #8A6428). Cruzar los tonos es el error que el manual senala como
    # prohibido: el bronce claro sobre fondo claro da 2.97:1.
    #
    # Umbral de 3:1 y no 4.5:1, y no es una rebaja para que pase: el bronce
    # NUNCA es texto. En la insignia de vendedor verificado es una linea de 2px
    # y un icono de 16px —objetos graficos, WCAG 1.4.11—, y el texto de la
    # insignia va en --color-texto-suave, que si se comprueba como texto mas
    # arriba. El manual lo prohibe ademas explicitamente como color de texto
    # sobre fondo claro y como relleno grande.
    #
    # Que quede claro cual es el margen real: sobre la tarjeta oscura el bronce
    # da 4.22:1. Cumple de sobra como objeto grafico, pero si alguna vez se usa
    # como texto ahi, incumple. Si eso llega a hacer falta, no se sube el
    # umbral: se pide a diseno un tercer tono, porque el bronce de marca no da.
    ("Insignia verificada sobre el fondo",    "--color-acento",         "--color-fondo",          AA_GRANDE),
    ("Insignia verificada sobre tarjeta",     "--color-acento",         "--color-superficie",     AA_GRANDE),
    # El relleno de acento si lleva texto encima (--color-sobre-acento), y ahi
    # el umbral de texto aplica entero.
    ("Texto sobre relleno de acento",         "--color-sobre-acento",   "--color-acento",         AA_TEXTO),

    # ---- La franja de tinta: hero y pie. NO cambia con el modo ----
    # Por eso sus pares se comprueban en los dos: en oscuro la tinta queda un
    # paso POR ENCIMA del fondo de pagina y se sigue leyendo como franja, pero
    # el texto y el anillo de foco de dentro tienen que cumplir igual.
    ("Texto sobre la franja de tinta",        "--color-sobre-tinta",    "--color-tinta",          AA_TEXTO),
    ("Insignia verificada sobre la tinta",    "--color-acento-tinta",   "--color-tinta",          AA_GRANDE),
    # El anillo se dibuja con outline-offset:2px, o sea SEPARADO del control por
    # un hueco que deja ver el fondo. Por eso se compara contra el fondo de
    # alrededor y no contra el relleno del control.
    ("Anillo de foco sobre la franja de tinta", "--color-foco-tinta",   "--color-tinta",          AA_GRANDE),
    # Dentro de la franja el boton principal se invierte: relleno claro con
    # tinta encima. Un boton en tinta sobre fondo de tinta no se ve, y el manual
    # prohibe resolverlo con bronce ("el boton principal de compra va en tinta,
    # no en bronce"), asi que la salida es invertirlo, no cambiarle el color.
    ("Texto del boton dentro de la franja",   "--color-tinta",          "--color-primario-tinta", AA_TEXTO),
    ("Relleno del boton contra la franja",    "--color-primario-tinta", "--color-tinta",          AA_GRANDE),
]


def revisar(modo, paleta):
    print("\n" + "=" * 74)
    print("MODO " + modo.upper())
    print("=" * 74)
    fallas, ausentes = [], []
    for desc, fg, bg, umbral in PARES:
        if fg not in paleta or bg not in paleta:
            ausentes.append((desc, fg if fg not in paleta else bg))
            continue
        r = contraste(paleta[fg], paleta[bg])
        cumple = r >= umbral
        marca = "ok   " if cumple else "FALLA"
        print("  {} {:>6}:1  (min {:.1f})  {:<38} {} sobre {}".format(
            marca, r, umbral, desc[:38], paleta[fg], paleta[bg]))
        if not cumple:
            fallas.append((desc, r, umbral, paleta[fg], paleta[bg]))
    for desc, falta in ausentes:
        print("  ?????            variable no encontrada: {}  ({})".format(falta, desc))
    return fallas, ausentes


def main():
    claro, oscuro = paletas()
    f1, a1 = revisar("claro", claro)
    f2, a2 = revisar("oscuro", oscuro)
    fallas, ausentes = f1 + f2, a1 + a2

    print("\n" + "=" * 74)
    if not fallas and not ausentes:
        print("Todo cumple: {} pares en modo claro y {} en oscuro.".format(
            len(PARES), len(PARES)))
        print("\nRecuerda que esto comprueba los COLORES, no la maqueta. Abre\n"
              "index.html, reduce la ventana a 360px, prueba el modo oscuro y\n"
              "amplia el texto al 200%: eso se ve, no se deduce.")
        return 0

    for desc, r, umbral, fg, bg in fallas:
        print("FALLA  {}: {}:1, necesita {}:1  ({} sobre {})".format(desc, r, umbral, fg, bg))
        print("       Se corrige en tokens.json si es un color de rol, o en\n"
              "       frontend/src/styles/marca.css si es un token del proyecto.")
    for desc, falta in ausentes:
        print("FALTA  la variable {} ({})".format(falta, desc))
    return 1


if __name__ == "__main__":
    sys.exit(main())
