# Notes · Dotrino

**Un bloc de notas que te sigue entre aparatos.** Escribes en el teléfono y sigues en la
computadora. [`notes.dotrino.com`](https://notes.dotrino.com/)

> **Parte del ecosistema [Dotrino](https://dotrino.com).** Dotrino es un ecosistema de
> aplicaciones centradas en la privacidad de los datos: tu información es tuya, y las
> decisiones sobre ella también — qué compartes, con quién, cuándo y por qué. Sin
> anuncios, sin cookies, sin rastreo de datos, sin vender tu identidad a nadie.

**Cómo se usa:** [wiki.dotrino.com/apps/notas](https://wiki.dotrino.com/apps/notas/).
Este README es para quien desarrolla.

## Cómo funciona

| Qué | Dónde |
|---|---|
| Qué se guarda y con qué forma (`notes.items`, una nota por entrada) | `src/lib/repo.js` (`@dotrino/store`) |
| Título, orden, búsqueda, color del indicador, qué hacer con lo que llega | `src/lib/notes.js` (puro, con pruebas) |
| Autoguardado, «Sincronizar», recarga al llegar cambios de otro aparato | `src/state.js` |
| El panel lateral (crear, buscar, abrir, fijar, borrar; plegable, cajón en el móvil) | `src/components/Sidebar.vue` |
| El editor (solo escribe) | `src/components/NoteEditor.vue` |

**La sincronización es la del almacén.** Las notas viven en `@dotrino/store`, abierto con la
identidad del perfil. Si el perfil está enlazado a una bóveda, el almacén respalda el hilo en
ella (cifrado con la llave de la cuenta) y los otros aparatos de la cuenta lo bajan: sube solo
1,5 s después de cada escritura y baja al volver a la pestaña y cada 5 minutos. «Sincronizar»
fuerza `store.vaultSync()` ahora. Entre dos versiones de la misma nota gana la de `ts` más alto;
si la nota abierta tiene cambios sin guardar, se conservan. Borrar deja lápida.

El indicador: **verde** = todo está en la bóveda y no queda nada por subir; **amarillo** = algo
queda solo en este aparato (pendiente, sin bóveda enlazada o con error).

Límite: una nota de más de 100 000 caracteres no se guarda (una entrada de ~550 KB no cabe en el
respaldo).

Pilares: `@dotrino/identity`, `@dotrino/store`, `@dotrino/topbar`, `@dotrino/theme`,
`@dotrino/nav`, `@dotrino/install`, `@dotrino/reputation`, `@dotrino/compat`, `@dotrino/prerender`.

## Desarrollo

```sh
npm install
npm run dev          # https://localhost:3160 (HTTPS autofirmado: los iframes del ecosistema lo piden)
npm test             # funciones puras
npm run type-check   # vue-tsc estricto
npm run build        # → dist/
```

## Deploy

Push a `main` → GitHub Actions construye y publica `dist/` en Pages (`notes.dotrino.com`).

Licencia MIT.

## Versiones nativas (Android e iOS)

La PWA va delante (CONVENCIONES §16). `android/` (Kotlin, vistas nativas) e `ios/` (SwiftUI) usan
[`dotrino-native`](https://github.com/imdotrino/dotrino-native) como submódulo en `native/`, el mismo
hilo `notes.items` que la web y el respaldo de la bóveda, así que las notas van y vienen entre la
web y el teléfono. Los textos salen de `src/i18n.js`: tras cambiarlos, `npm run native:i18n`.

```bash
git submodule update --init
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
cd ios && xcodegen generate && xcodebuild -project Notes.xcodeproj -scheme Notes \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' test
```

Android habla con la app «Identidad Dotrino», que solo atiende a apps firmadas con la misma llave:
para probar contra ella hay que compilar el release con la llave de subida
(`dotrino-env run --ns claude -- ./gradlew --no-daemon :app:assembleRelease`).
