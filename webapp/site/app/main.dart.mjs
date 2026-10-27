// Compiles a dart2wasm-generated main module from `source` which can then
// be instantiated via the `instantiate` method.
//
// `source` needs to be a `Response` object (or promise thereof) e.g. created
// via the `fetch()` JS API.
export async function compileStreaming(source) {
  const builtins = {builtins: ['js-string']};
  return new CompiledApp(
      await WebAssembly.compileStreaming(source, builtins), builtins);
}

// Compiles a dart2wasm-generated wasm module from `bytes` which is then
// instantiable via the `instantiate` method.
export async function compile(bytes) {
  const builtins = {builtins: ['js-string']};
  return new CompiledApp(await WebAssembly.compile(bytes, builtins), builtins);
}

class CompiledApp {
  constructor(module, builtins) {
    this.module = module;
    this.builtins = builtins;
  }

  // The second argument is an options object containing:
  // `loadDeferredModules` is a JS function that takes an array of module names
  //   matching wasm files produced by the dart2wasm compiler. It also takes a
  //   callback that should be invoked for each loaded module with 2 arguments:
  //   (1) the module name, (2) the loaded module in a format supported by
  //   `WebAssembly.compile` or `WebAssembly.compileStreaming`. The callback
  //   returns a Promise that resolves when the module is instantiated.
  //   loadDeferredModules should return a Promise that resolves when all the
  //   modules have been loaded and the callback promises have resolved.
  // `loadDeferredId` is a JS function that takes load ID produced by the
  //   compiler when the `use-load-ids` option is passed. Each load ID maps to
  //   one or more wasm files as specified in the emitted JSON file. It also
  //   takes a callback that should be invoked for each loaded module with 2
  //   arguments: (1) the module name, (2) the loaded module in a format
  //   supported by `WebAssembly.compile` or `WebAssembly.compileStreaming`.
  //   The callback returns a Promise that resolves when the module is
  //   instantiated.
  //   loadDeferredId should return a Promise that resolves when all the
  //   modules have been loaded and the callback promises have resolved.
  async instantiate(additionalImports, {loadDeferredModules, loadDeferredId} = {}) {
    let dartInstance;

    // Prints to the console
    function printToConsole(value) {
      if (typeof dartPrint == "function") {
        dartPrint(value);
        return;
      }
      if (typeof console == "object" && typeof console.log != "undefined") {
        console.log(value);
        return;
      }
      if (typeof print == "function") {
        print(value);
        return;
      }

      throw "Unable to print message: " + value;
    }

    // A special symbol attached to functions that wrap Dart functions.
    const jsWrappedDartFunctionSymbol = Symbol("JSWrappedDartFunction");

    function finalizeWrapper(dartFunction, wrapped) {
      wrapped.dartFunction = dartFunction;
      wrapped[jsWrappedDartFunctionSymbol] = true;
      return wrapped;
    }

    // Imports
    const dart2wasm = {
            AB: (x0,x1,x2,x3) => x0.addEventListener(x1,x2,x3),
      AC: Function.prototype.call.bind(DataView.prototype.setInt16),
      AD: x0 => x0.height,
      AE: (x0,x1) => x0.observe(x1),
      AF: x0 => x0.wheelDeltaY,
      AG: x0 => x0.next(),
      AH: x0 => x0.minWidth,
      AI: x0 => x0.document,
      AJ: x0 => x0.url,
      AK: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      AL: (x0,x1) => { x0.height = x1 },
      AM: (x0,x1) => { x0.id = x1 },
      AN: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      B: s => printToConsole(s),
      BB: b => !!b,
      BC: Function.prototype.call.bind(DataView.prototype.setUint16),
      BD: x0 => x0.width,
      BE: (wasmFunction,f) => finalizeWrapper(f, function(x0,x1) { return wasmFunction(f,arguments.length,x0,x1) }),
      BF: x0 => x0.wheelDeltaX,
      BG: x0 => x0.current(),
      BH: (x0,x1) => x0.removeProperty(x1),
      BI: x0 => new WeakRef(x0),
      BJ: x0 => x0.status,
      BK: x0 => x0.send(),
      BL: (x0,x1) => { x0.width = x1 },
      BM: (x0,x1,x2,x3) => x0.decrypt(x1,x2,x3),
      BN: (x0,x1) => ({createScript: x0,createScriptURL: x1}),
      C: Function.prototype.call.bind(Number.prototype.toString),
      CB: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      CC: Function.prototype.call.bind(DataView.prototype.setUint8),
      CD: x0 => x0.screen,
      CE: x0 => new ResizeObserver(x0),
      CF: x0 => x0.key,
      CG: (x0,x1) => new Intl.v8BreakIterator(x0,x1),
      CH: (x0,x1) => x0.add(x1),
      CI: x0 => x0.deref(),
      CJ: x0 => x0.getReader(),
      CK: x0 => x0.status,
      CL: x0 => x0.height,
      CM: x0 => x0.subtle,
      CN: (x0,x1,x2) => x0.createPolicy(x1,x2),
      D: Function.prototype.call.bind(BigInt.prototype.toString),
      DB: (x0,x1) => x0.focus(x1),
      DC: Function.prototype.call.bind(DataView.prototype.setInt8),
      DD: o => {
        if (o === null || o === undefined) return 0;
        if (typeof(o) === 'string') return 1;
        return 2;
      },
      DE: (x0,x1) => x0.getPropertyValue(x1),
      DF: x0 => x0.identifier,
      DG: x0 => x0.v8BreakIterator,
      DH: x0 => x0.data,
      DI: () => globalThis.WeakRef,
      DJ: x0 => x0.read(),
      DK: x0 => x0.response,
      DL: x0 => x0.width,
      DM: x0 => x0.crypto,
      DN: (x0,x1) => x0.createScriptURL(x1),
      E: (exn) => {
        let stackString = exn.toString();
        let frames = stackString.split('\n');
        let drop = 4;
        if (frames[0].startsWith('Error')) {
            drop += 1;
        }
        return frames.slice(drop).join('\n');
      },
      EB: () => ({}),
      EC: Function.prototype.call.bind(DataView.prototype.getInt8),
      ED: x0 => x0.tabIndex,
      EE: x0 => globalThis.parseFloat(x0),
      EF: x0 => x0.touches,
      EG: () => globalThis.Intl,
      EH: (x0,x1) => { x0.scrollTop = x1 },
      EI: (o, offsetInBytes, lengthInBytes) => {
        var dst = new ArrayBuffer(lengthInBytes);
        new Uint8Array(dst).set(new Uint8Array(o, offsetInBytes, lengthInBytes));
        return new DataView(dst);
      },
      EJ: x0 => x0.value,
      EK: (x0,x1,x2) => x0.setRequestHeader(x1,x2),
      EL: x0 => x0.rasterEndMilliseconds,
      EM: x0 => x0.isSecureContext,
      EN: (x0,x1,x2) => x0.createScript(x1,x2),
      F: () => new Error().stack,
      FB: (o, p, v) => o[p] = v,
      FC: o => {
        if (o === null || o === undefined) return 0;
        if (o instanceof Int8Array) return 1;
        return 2;
      },
      FD: (x0,x1) => x0.contains(x1),
      FE: (x0,x1) => x0.getComputedStyle(x1),
      FF: x0 => x0.pressure,
      FG: (x0,x1) => x0.segment(x1),
      FH: (x0,x1,x2) => x0.setSelectionRange(x1,x2),
      FI: (a, s, e) => a.slice(s, e),
      FJ: x0 => x0.done,
      FK: (x0,x1) => { x0.responseType = x1 },
      FL: x0 => x0.rasterStartMilliseconds,
      FM: (x0,x1,x2,x3,x4,x5,x6,x7) => x0.unwrapKey(x1,x2,x3,x4,x5,x6,x7),
      FN: (x0,x1) => x0.appendChild(x1),
      G: s => JSON.stringify(s),
      GB: () => [],
      GC: (o, start, length) => new Float64Array(o.buffer, o.byteOffset + start, length),
      GD: x0 => x0.activeElement,
      GE: x0 => x0.documentElement,
      GF: x0 => x0.tiltY,
      GG: x0 => x0.index,
      GH: (x0,x1) => { x0.value = x1 },
      GI: () => {
        return typeof process != "undefined" &&
               Object.prototype.toString.call(process) == "[object process]" &&
               process.platform == "win32"
      },
      GJ: x0 => x0.cancel(),
      GK: () => new XMLHttpRequest(),
      GL: x0 => x0.imageBitmaps,
      GM: (x0,x1,x2,x3,x4,x5) => x0.importKey(x1,x2,x3,x4,x5),
      GN: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      H: Function.prototype.call.bind(Number.prototype.toString),
      HB: (a, i) => a.push(i),
      HC: (o, start, length) => new Float32Array(o.buffer, o.byteOffset + start, length),
      HD: x0 => x0.parentNode,
      HE: x0 => x0.computedStyleMap(),
      HF: x0 => x0.tiltX,
      HG: x0 => x0.next(),
      HH: (x0,x1,x2) => x0.setSelectionRange(x1,x2),
      HI: () => {
        // On browsers return `globalThis.location.href`
        if (globalThis.location != null) {
          return globalThis.location.href;
        }
        return null;
      },
      HJ: x0 => x0.body,
      HK: (a, l) => a.length = l,
      HL: x0 => x0.canvasKitMaximumSurfaces,
      HM: (x0,x1,x2,x3) => x0.generateKey(x1,x2,x3),
      HN: (o, p) => delete o[p],
      I: Function.prototype.call.bind(String.prototype.indexOf),
      IB: x0 => new Int8Array(x0),
      IC: (o, start, length) => new Uint32Array(o.buffer, o.byteOffset + start, length),
      ID: x0 => x0.tagName,
      IE: (x0,x1) => x0.get(x1),
      IF: x0 => x0.pointerType,
      IG: x0 => x0.value,
      IH: (x0,x1) => { x0.value = x1 },
      II: (o, p) => p in o,
      IJ: x0 => x0.headers,
      IK: x0 => x0.protocol,
      IL: x0 => x0.nextSibling,
      IM: (x0,x1,x2,x3,x4) => x0.wrapKey(x1,x2,x3,x4),
      IN: (x0,x1) => { x0.text = x1 },
      J: (s, p, i) => s.lastIndexOf(p, i),
      JB: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const getValue = dartInstance.exports.$wasmI8ArrayGet;
        for (let i = 0; i < length; i++) {
          jsArray[jsArrayOffset + i] = getValue(wasmArray, wasmArrayOffset + i);
        }
      },
      JC: (o, start, length) => new Int32Array(o.buffer, o.byteOffset + start, length),
      JD: x0 => x0.target,
      JE: (o, p) => p in o,
      JF: x0 => x0.pointerId,
      JG: x0 => x0.done,
      JH: s => {
        if (/[[\]{}()*+?.\\^$|]/.test(s)) {
            s = s.replace(/[[\]{}()*+?.\\^$|]/g, '\\$&');
        }
        return s;
      },
      JI: x0 => x0.groups,
      JJ: x0 => x0.signal,
      JK: (x0,x1,x2) => x0.close(x1,x2),
      JL: (x0,x1) => x0.debug(x1),
      JM: (x0,x1,x2) => x0.exportKey(x1,x2),
      JN: x0 => x0.head,
      K: o => o,
      KB: x0 => new Uint8Array(x0),
      KC: (o, start, length) => new Uint16Array(o.buffer, o.byteOffset + start, length),
      KD: x0 => x0.clientY,
      KE: (x0,x1) => { x0.textContent = x1 },
      KF: x0 => x0.getCoalescedEvents(),
      KG: (o, m, a) => o[m].apply(o, a),
      KH: x0 => x0.value,
      KI: () => new XMLHttpRequest(),
      KJ: (x0,x1) => x0.revokeObjectURL(x1),
      KK: x0 => x0.close(),
      KL: x0 => x0.hostElement,
      KM: x0 => x0.sessionStorage,
      KN: (x0,x1) => { x0.text = x1 },
      L: o => {
        if (o === undefined || o === null) return 0;
        if (typeof o === 'number') return 1;
        return 2;
      },
      LB: x0 => new Uint8ClampedArray(x0),
      LC: (o, start, length) => new Int16Array(o.buffer, o.byteOffset + start, length),
      LD: x0 => x0.clientX,
      LE: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      LF: (x0,x1) => x0.getModifierState(x1),
      LG: x0 => x0.iterator,
      LH: x0 => x0.selectionDirection,
      LI: (x0,x1,x2) => x0.open(x1,x2),
      LJ: (x0,x1) => { x0.src = x1 },
      LK: (x0,x1) => x0.send(x1),
      LL: x0 => x0.location,
      LM: (x0,x1) => x0.getRandomValues(x1),
      LN: x0 => x0.trustedTypes,
      M: x0 => x0.index,
      MB: x0 => new Int16Array(x0),
      MC: (o, start, length) => new Uint8ClampedArray(o.buffer, o.byteOffset + start, length),
      MD: (x0,x1,x2) => x0.setAttribute(x1,x2),
      ME: x0 => x0.matches,
      MF: s => s.trimLeft(),
      MG: () => globalThis.Symbol,
      MH: x0 => x0.selectionStart,
      MI: (x0,x1) => x0.send(x1),
      MJ: (x0,x1,x2,x3,x4) => globalThis.createImageBitmap(x0,x1,x2,x3,x4),
      MK: () => new Array(),
      ML: (x0,x1) => x0.getModifierState(x1),
      MM: (x0,x1,x2,x3) => x0.encrypt(x1,x2,x3),
      MN: () => globalThis.console,
      N: o => String(o),
      NB: x0 => new Uint16Array(x0),
      NC: (o, start, length) => new Uint8Array(o.buffer, o.byteOffset + start, length),
      ND: x0 => x0.getBoundingClientRect(),
      NE: (x0,x1) => x0.matchMedia(x1),
      NF: s => s.toUpperCase(),
      NG: (x0,x1) => new Intl.Segmenter(x0,x1),
      NH: x0 => x0.selectionEnd,
      NI: x0 => x0.send(),
      NJ: x0 => x0.naturalHeight,
      NK: (x0,x1) => new WebSocket(x0,x1),
      NL: x0 => x0.metaKey,
      NM: () => globalThis.removeSplashFromWeb(),
      NN: x0 => x0.trustedTypes,
      O: o => o === undefined,
      OB: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const getValue = dartInstance.exports.$wasmI16ArrayGet;
        for (let i = 0; i < length; i++) {
          jsArray[jsArrayOffset + i] = getValue(wasmArray, wasmArrayOffset + i);
        }
      },
      OC: (o, start, length) => new Int8Array(o.buffer, o.byteOffset + start, length),
      OD: (ms, c) =>
      setTimeout(() => dartInstance.exports.$invokeCallback(c),ms),
      OE: x0 => x0.matches,
      OF: x0 => x0.pop(),
      OG: x0 => x0.Segmenter,
      OH: x0 => x0.value,
      OI: x0 => x0.abort(),
      OJ: x0 => x0.naturalWidth,
      OK: x0 => x0.reason,
      OL: x0 => x0.altKey,
      OM: x0 => x0.message,
      ON: (x0,x1) => { x0.crossOrigin = x1 },
      P: (x0,x1) => x0.exec(x1),
      PB: x0 => new Int32Array(x0),
      PC: (x0,x1) => x0.querySelector(x1),
      PD: s => new Date(s * 1000).getTimezoneOffset() * 60,
      PE: o => typeof o === 'function' && o[jsWrappedDartFunctionSymbol] === true,
      PF: x0 => x0.flags,
      PG: x0 => x0.buffer,
      PH: x0 => x0.selectionDirection,
      PI: x0 => x0.upload,
      PJ: x0 => x0.decode(),
      PK: x0 => x0.code,
      PL: x0 => x0.ctrlKey,
      PM: x0 => x0.code,
      PN: (x0,x1) => { x0.type = x1 },
      Q: (x0,x1) => { x0.lastIndex = x1 },
      QB: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const getValue = dartInstance.exports.$wasmI32ArrayGet;
        for (let i = 0; i < length; i++) {
          jsArray[jsArrayOffset + i] = getValue(wasmArray, wasmArrayOffset + i);
        }
      },
      QC: (x0,x1) => x0.item(x1),
      QD: Date.now,
      QE: f => f.dartFunction,
      QF: (a, s) => a.join(s),
      QG: x0 => x0.wasmMemory,
      QH: x0 => x0.selectionStart,
      QI: x0 => x0.readyState,
      QJ: (x0,x1) => { x0.decoding = x1 },
      QK: (o, t) => typeof o === t,
      QL: x0 => x0.isComposing,
      QM: () => globalThis.Notification.requestPermission(),
      QN: x0 => x0.href,
      R: o => o,
      RB: x0 => new Uint32Array(x0),
      RC: x0 => x0.length,
      RD: (handle) => clearTimeout(handle),
      RE: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      RF: (x0,x1) => x0.error(x1),
      RG: () => globalThis.window._flutter_skwasmInstance,
      RH: x0 => x0.selectionEnd,
      RI: x0 => x0.responseURL,
      RJ: (x0,x1) => { x0.crossOrigin = x1 },
      RK: x0 => x0.data,
      RL: x0 => x0.code,
      RM: (x0,x1) => globalThis.firebase_messaging.getToken(x0,x1),
      RN: x0 => x0.location,
      S: (s, m) => {
        try {
          return new RegExp(s, m);
        } catch (e) {
          return String(e);
        }
      },
      SB: x0 => new Float32Array(x0),
      SC: (x0,x1) => x0.querySelectorAll(x1),
      SD: (x0,x1) => x0.closest(x1),
      SE: (wasmFunction,f) => finalizeWrapper(f, function(x0,x1) { return wasmFunction(f,arguments.length,x0,x1) }),
      SF: () => globalThis.console,
      SG: () => new TextDecoder(),
      SH: x0 => x0.keyCode,
      SI: x0 => x0.statusText,
      SJ: (x0,x1) => x0.createObjectURL(x1),
      SK: x0 => x0.readyState,
      SL: x0 => x0.repeat,
      SM: x0 => x0.link,
      SN: x0 => x0.length,
      T: o => o instanceof RegExp,
      TB: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const getValue = dartInstance.exports.$wasmF32ArrayGet;
        for (let i = 0; i < length; i++) {
          jsArray[jsArrayOffset + i] = getValue(wasmArray, wasmArrayOffset + i);
        }
      },
      TC: (x0,x1) => x0.getAttribute(x1),
      TD: x0 => x0.bottom,
      TE: (p, s, f) => p.then(s, (e) => f(e, e === undefined)),
      TF: s => s.trimRight(),
      TG: (a, i) => a.splice(i, 1),
      TH: (x0,x1) => x0.scrollIntoView(x1),
      TI: x0 => x0.getAllResponseHeaders(),
      TJ: x0 => x0.URL,
      TK: (x0,x1) => { x0.binaryType = x1 },
      TL: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      TM: x0 => x0.analyticsLabel,
      TN: x0 => x0.getReader(),
      U: (string, times) => string.repeat(times),
      UB: x0 => new Float64Array(x0),
      UC: x0 => x0.remove(),
      UD: x0 => x0.top,
      UE: (o, i) => o[i],
      UF: x0 => x0.blur(),
      UG: a => a.pop(),
      UH: x0 => x0.multiViewEnabled,
      UI: x0 => x0.status,
      UJ: x0 => new Blob(x0),
      UK: (x0,x1,x2) => x0.showNotification(x1,x2),
      UL: x0 => x0.userAgent,
      UM: x0 => x0.image,
      UN: x0 => x0.value,
      V: o => o,
      VB: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const getValue = dartInstance.exports.$wasmF64ArrayGet;
        for (let i = 0; i < length; i++) {
          jsArray[jsArrayOffset + i] = getValue(wasmArray, wasmArrayOffset + i);
        }
      },
      VC: (x0,x1) => x0.appendChild(x1),
      VD: x0 => x0.right,
      VE: o => o.length,
      VF: x0 => x0.button,
      VG: (map, o, v) => map.set(o, v),
      VH: (x0,x1) => x0.replaceWith(x1),
      VI: x0 => x0.response,
      VJ: (x0,x1,x2,x3,x4) => ({type: x0,data: x1,premultiplyAlpha: x2,colorSpaceConversion: x3,preferAnimation: x4}),
      VK: (x0,x1,x2,x3,x4,x5,x6,x7,x8,x9,x10,x11,x12) => ({dir: x0,lang: x1,body: x2,tag: x3,image: x4,icon: x5,badge: x6,timestamp: x7,renotify: x8,silent: x9,requireInteraction: x10,data: x11,actions: x12}),
      VL: (x0,x1,x2,x3) => x0.open(x1,x2,x3),
      VM: x0 => x0.body,
      VN: x0 => x0.done,
      W: o => {
        if (o === undefined || o === null) return 0;
        if (typeof o === 'boolean') return 1;
        return 2;
      },
      WB: x0 => new ArrayBuffer(x0),
      WC: (x0,x1) => x0.append(x1),
      WD: x0 => x0.left,
      WE: o => {
        if (o === undefined) return 1;
        var type = typeof o;
        if (type === 'boolean') return 2;
        if (type === 'number') return 3;
        if (type === 'string') return 4;
        if (o instanceof Array) return 5;
        if (ArrayBuffer.isView(o)) {
          if (o instanceof Int8Array) return 6;
          if (o instanceof Uint8Array) return 7;
          if (o instanceof Uint8ClampedArray) return 8;
          if (o instanceof Int16Array) return 9;
          if (o instanceof Uint16Array) return 10;
          if (o instanceof Int32Array) return 11;
          if (o instanceof Uint32Array) return 12;
          if (o instanceof Float32Array) return 13;
          if (o instanceof Float64Array) return 14;
          if (o instanceof DataView) return 15;
        }
        if (o instanceof ArrayBuffer) return 16;
        // Feature check for `SharedArrayBuffer` before doing a type-check.
        if (globalThis.SharedArrayBuffer !== undefined &&
            o instanceof SharedArrayBuffer) {
            return 17;
        }
        if (o instanceof Promise) return 18;
        return 19;
      },
      WF: x0 => x0.innerHeight,
      WG: (map, o) => map.get(o),
      WH: (x0,x1) => { x0.type = x1 },
      WI: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      WJ: x0 => new window.ImageDecoder(x0),
      WK: x0 => x0.active,
      WL: (x0,x1,x2) => x0.setAttribute(x1,x2),
      WM: x0 => x0.title,
      WN: x0 => x0.read(),
      X: x0 => x0.dotAll,
      XB: (x0,x1,x2) => new Uint8Array(x0,x1,x2),
      XC: (x0,x1,x2,x3) => x0.setProperty(x1,x2,x3),
      XD: x0 => x0.clientY,
      XE: x0 => x0.language,
      XF: x0 => x0.innerWidth,
      XG: () => new WeakMap(),
      XH: (x0,x1) => { x0.className = x1 },
      XI: (x0,x1,x2,x3) => x0.addEventListener(x1,x2,x3),
      XJ: x0 => x0.name,
      XK: () => globalThis.Notification.permission,
      XL: (x0,x1) => x0.createElement(x1),
      XM: x0 => x0.fcmOptions,
      XN: x0 => x0.body,
      Y: x0 => x0.unicode,
      YB: (x0,x1,x2) => new DataView(x0,x1,x2),
      YC: x0 => x0.style,
      YD: x0 => x0.clientX,
      YE: (x0,x1,x2,x3) => x0.register(x1,x2,x3),
      YF: x0 => x0.height,
      YG: x0 => x0.debugSkipFontRetryDelay,
      YH: (x0,x1) => { x0.tabIndex = x1 },
      YI: (x0,x1,x2,x3) => x0.removeEventListener(x1,x2,x3),
      YJ: x0 => x0.repetitionCount,
      YK: (x0,x1,x2,x3) => ({crv: x0,d: x1,kty: x2,x: x3}),
      YL: (x0,x1) => { x0.cursor = x1 },
      YM: x0 => x0.notification,
      YN: (x0,x1) => new OffscreenCanvas(x0,x1),
      Z: x0 => x0.ignoreCase,
      ZB: (o, p) => o[p],
      ZC: x0 => x0.debugShowSemanticsNodes,
      ZD: x0 => x0.changedTouches,
      ZE: () => globalThis.window.FinalizationRegistry,
      ZF: x0 => x0.width,
      ZG: x0 => x0.status,
      ZH: (x0,x1) => { x0.name = x1 },
      ZI: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      ZJ: x0 => x0.frameCount,
      ZK: (x0,x1,x2) => globalThis.crypto.subtle.sign(x0,x1,x2),
      ZL: (x0,x1) => { x0.height = x1 },
      ZM: x0 => x0.messageId,
      ZN: x0 => x0.assetBase,
      a: x0 => x0.multiline,
      aB: (o) => new DataView(o.buffer, o.byteOffset, o.byteLength),
      aC: (x0,x1) => x0.warn(x1),
      aD: x0 => x0.offsetY,
      aE: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      aF: x0 => x0.clientHeight,
      aG: (x0,x1,x2) => x0.set(x1,x2),
      aH: (x0,x1) => { x0.placeholder = x1 },
      aI: x0 => x0.withCredentials,
      aJ: x0 => x0.selectedTrack,
      aK: (x0,x1,x2,x3,x4) => globalThis.crypto.subtle.importKey(x0,x1,x2,x3,x4),
      aL: (x0,x1) => { x0.width = x1 },
      aM: x0 => x0.from,
      aN: x0 => x0.loader,
      b: (exn) => {
        if (exn instanceof Error) {
          return exn.stack;
        } else {
          return null;
        }
      },
      bB: Function.prototype.call.bind(Object.getOwnPropertyDescriptor(DataView.prototype, 'byteLength').get),
      bC: x0 => x0.console,
      bD: x0 => x0.offsetX,
      bE: x0 => new window.FinalizationRegistry(x0),
      bF: x0 => x0.clientWidth,
      bG: x0 => x0.arrayBuffer(),
      bH: (x0,x1) => { x0.autocomplete = x1 },
      bI: (x0,x1) => { x0.timeout = x1 },
      bJ: x0 => x0.completed,
      bK: (x0,x1,x2) => x0.insertBefore(x1,x2),
      bL: (x0,x1) => { x0.display = x1 },
      bM: x0 => x0.collapseKey,
      bN: () => globalThis._flutter,
      c: (c) =>
      queueMicrotask(() => dartInstance.exports.$invokeCallback(c)),
      cB: o => o.byteOffset,
      cC: () => globalThis.window,
      cD: x0 => x0.type,
      cE: (x0,x1) => x0.unregister(x1),
      cF: (x0,x1) => { x0.content = x1 },
      cG: o => {
        if (o === null || o === undefined) return 0;
        if (o instanceof ArrayBuffer) return 1;
        if (globalThis.SharedArrayBuffer !== undefined &&
            o instanceof SharedArrayBuffer) {
          return 2;
        }
        return 3;
      },
      cH: (x0,x1) => { x0.name = x1 },
      cI: (x0,x1,x2) => x0.setRequestHeader(x1,x2),
      cJ: x0 => x0.ready,
      cK: x0 => x0.id,
      cL: (x0,x1) => { x0.opacity = x1 },
      cM: x0 => x0.data,
      d: (x0,x1) => x0.didCreateEngineInitializer(x1),
      dB: o => o.buffer,
      dC: (o, c) => o instanceof c,
      dD: x0 => x0.maxTouchPoints,
      dE: (x0,x1) => x0.contains(x1),
      dF: (x0,x1) => { x0.name = x1 },
      dG: (x0,x1) => x0.fetch(x1),
      dH: (x0,x1) => { x0.placeholder = x1 },
      dI: (x0,x1) => { x0.withCredentials = x1 },
      dJ: x0 => x0.tracks,
      dK: x0 => x0.offsetHeight,
      dL: x0 => x0.style,
      dM: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      e: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      eB: Function.prototype.call.bind(DataView.prototype.getUint8),
      eC: (x0,x1) => x0[x1],
      eD: x0 => x0.platform,
      eE: (s) => +s,
      eF: x0 => x0.head,
      eG: x0 => x0.fontFallbackBaseUrl,
      eH: (x0,x1) => { x0.action = x1 },
      eI: (x0,x1) => { x0.responseType = x1 },
      eJ: x0 => x0.close(),
      eK: x0 => x0.offsetWidth,
      eL: (o, p, v) => o[p] = v,
      eM: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      f: (wasmFunction,f) => finalizeWrapper(f, function() { return wasmFunction(f,arguments.length) }),
      fB: (b, o) => new DataView(b, o),
      fC: x0 => x0.length,
      fD: x0 => x0.body,
      fE: s => {
        if (!/^\s*[+-]?(?:Infinity|NaN|(?:\.\d+|\d+(?:\.\d*)?)(?:[eE][+-]?\d+)?)\s*$/.test(s)) {
          return NaN;
        }
        return parseFloat(s);
      },
      fF: (x0,x1) => x0.removeChild(x1),
      fG: (handle) => clearInterval(handle),
      fH: (x0,x1) => { x0.method = x1 },
      fI: (x0,x1) => globalThis.crypto.subtle.digest(x0,x1),
      fJ: (x0,x1) => ({frameIndex: x0,completeFramesOnly: x1}),
      fK: x0 => x0.stopPropagation(),
      fL: () => globalThis.document,
      fM: (x0,x1) => ({next: x0,error: x1}),
      g: (x0,x1) => ({initializeEngine: x0,autoStart: x1}),
      gB: (b, o, l) => new DataView(b, o, l),
      gC: (string, token) => string.split(token),
      gD: () => globalThis.document,
      gE: s => s.trim(),
      gF: x0 => x0.firstChild,
      gG: (ms, c) =>
      setInterval(() => dartInstance.exports.$invokeCallback(c), ms),
      gH: (x0,x1) => { x0.noValidate = x1 },
      gI: () => globalThis.window.isSecureContext,
      gJ: (x0,x1) => x0.decode(x1),
      gK: x0 => x0.disabled,
      gL: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      gM: (x0,x1) => globalThis.firebase_messaging.onMessage(x0,x1),
      h: (wasmFunction,f) => finalizeWrapper(f, function(x0,x1) { return wasmFunction(f,arguments.length,x0,x1) }),
      hB: Function.prototype.call.bind(DataView.prototype.getFloat64),
      hC: o => o instanceof Array,
      hD: (x0,x1,x2) => x0.addEventListener(x1,x2),
      hE: x0 => x0.classList,
      hF: x0 => x0.viewConstraints,
      hG: () => Date.now(),
      hH: (x0,x1) => x0.removeAttribute(x1),
      hI: () => globalThis.crypto.subtle,
      hJ: x0 => x0.displayHeight,
      hK: (x0,x1) => { x0.min = x1 },
      hL: x0 => x0.preventDefault(),
      hM: x0 => globalThis.firebase_messaging.getMessaging(x0),
      i: x0 => new Promise(x0),
      iB: o => {
        if (o === null || o === undefined) return 0;
        if (o instanceof Float64Array) return 1;
        return 2;
      },
      iC: (a, i) => a[i],
      iD: x0 => x0.hasFocus(),
      iE: x0 => x0.preventDefault(),
      iF: x0 => x0.hostElement,
      iG: (x0,x1,x2,x3) => x0.pushState(x1,x2,x3),
      iH: x0 => x0.isConnected,
      iI: x0 => globalThis.window.crypto.getRandomValues(x0),
      iJ: x0 => x0.displayWidth,
      iK: (x0,x1) => { x0.max = x1 },
      iL: x0 => x0.shiftKey,
      iM: x0 => globalThis.firebase_core.getApp(x0),
      j: (x0,x1,x2) => x0.call(x1,x2),
      jB: Function.prototype.call.bind(DataView.prototype.setFloat64),
      jC: a => a.length,
      jD: x0 => x0.relatedTarget,
      jE: x0 => x0.parent,
      jF: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      jG: x0 => x0.history,
      jH: x0 => x0.click(),
      jI: (x0,x1) => x0.getRandomValues(x1),
      jJ: x0 => x0.duration,
      jK: (x0,x1) => { x0.disabled = x1 },
      jL: x0 => x0.metaKey,
      jM: () => globalThis.firebase_core.getApp(),
      k: (constructor, args) => {
        const factoryFunction = constructor.bind.apply(
            constructor, [null, ...args]);
        return new factoryFunction();
      },
      kB: (t, s) => t.set(s),
      kC: (x0,x1) => x0.test(x1),
      kD: x0 => x0.shiftKey,
      kE: x0 => x0.timeStamp,
      kF: x0 => ({runApp: x0}),
      kG: x0 => x0.search,
      kH: (x0,x1) => x0.getElementsByClassName(x1),
      kI: () => globalThis.crypto,
      kJ: x0 => x0.image,
      kK: (x0,x1) => { x0.scrollLeft = x1 },
      kL: x0 => x0.altKey,
      kM: x0 => x0.measurementId,
      l: x0 => new Array(x0),
      lB: Function.prototype.call.bind(DataView.prototype.setFloat32),
      lC: x0 => x0.userAgent,
      lD: (decoder, codeUnits) => decoder.decode(codeUnits),
      lE: (x0,x1) => x0.hasAttribute(x1),
      lF: Function.prototype.call.bind(DataView.prototype.getBigInt64),
      lG: x0 => x0.location,
      lH: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const setValue = dartInstance.exports.$wasmF32ArraySet;
        for (let i = 0; i < length; i++) {
          setValue(wasmArray, wasmArrayOffset + i, jsArray[jsArrayOffset + i]);
        }
      },
      lI: l => new DataView(new ArrayBuffer(l)),
      lJ: () => globalThis.window.ImageDecoder,
      lK: (x0,x1) => { x0.spellcheck = x1 },
      lL: x0 => x0.ctrlKey,
      lM: x0 => x0.appId,
      m: o => [o],
      mB: Function.prototype.call.bind(DataView.prototype.getFloat32),
      mC: x0 => x0.navigator,
      mD: () => new TextDecoder("utf-8", {fatal: true}),
      mE: x0 => x0.buttons,
      mF: Function.prototype.call.bind(DataView.prototype.setBigInt64),
      mG: x0 => x0.pathname,
      mH: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const setValue = dartInstance.exports.$wasmF64ArraySet;
        for (let i = 0; i < length; i++) {
          setValue(wasmArray, wasmArrayOffset + i, jsArray[jsArrayOffset + i]);
        }
      },
      mI: x0 => x0.x,
      mJ: x0 => x0.naturalHeight,
      mK: (x0,x1) => { x0.disabled = x1 },
      mL: (x0,x1) => x0.getAttribute(x1),
      mM: x0 => x0.messagingSenderId,
      n: (o0, o1) => [o0, o1],
      nB: o => {
        if (o === null || o === undefined) return 0;
        if (o instanceof Float32Array) return 1;
        return 2;
      },
      nC: Function.prototype.call.bind(String.prototype.toLowerCase),
      nD: () => new TextDecoder("utf-8", {fatal: false}),
      nE: x0 => x0.ctrlKey,
      nF: (o, start, length) => new BigInt64Array(o.buffer, o.byteOffset + start, length),
      nG: (x0,x1,x2,x3) => x0.replaceState(x1,x2,x3),
      nH: (x0,x1) => x0.dispatchEvent(x1),
      nI: x0 => x0.d,
      nJ: x0 => x0.naturalWidth,
      nK: x0 => x0.getRegistration(),
      nL: (x0,x1) => x0.closest(x1),
      nM: x0 => x0.storageBucket,
      o: (o0, o1, o2) => [o0, o1, o2],
      oB: Function.prototype.call.bind(DataView.prototype.getUint32),
      oC: Object.is,
      oD: (a, i, v) => a[i] = v,
      oE: x0 => x0.y,
      oF: () => typeof dartUseDateNowForTicks !== "undefined",
      oG: o => {
        const proto = Object.getPrototypeOf(o);
        return proto === Object.prototype || proto === null;
      },
      oH: (x0,x1) => x0.createEvent(x1),
      oI: (x0,x1) => globalThis.crypto.subtle.exportKey(x0,x1),
      oJ: (x0,x1) => x0.createElement(x1),
      oK: (x0,x1) => x0.register(x1),
      oL: x0 => x0.tagName,
      oM: x0 => x0.databaseURL,
      p: (o0, o1, o2, o3) => [o0, o1, o2, o3],
      pB: o => {
        if (o === null || o === undefined) return 0;
        if (o instanceof Uint32Array) return 1;
        return 2;
      },
      pC: x0 => x0.vendor,
      pD: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const setValue = dartInstance.exports.$wasmI8ArraySet;
        for (let i = 0; i < length; i++) {
          setValue(wasmArray, wasmArrayOffset + i, jsArray[jsArrayOffset + i]);
        }
      },
      pE: x0 => x0.x,
      pF: () => Date.now(),
      pG: o => Object.keys(o),
      pH: (x0,x1,x2,x3) => x0.initEvent(x1,x2,x3),
      pI: x0 => x0.privateKey,
      pJ: (x0,x1) => { x0.pointerEvents = x1 },
      pK: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      pL: x0 => x0.target,
      pM: x0 => x0.authDomain,
      q: (x0,x1,x2) => { x0[x1] = x2 },
      qB: Function.prototype.call.bind(DataView.prototype.getInt32),
      qC: (x0,x1) => x0.createTextNode(x1),
      qD: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const setValue = dartInstance.exports.$wasmI16ArraySet;
        for (let i = 0; i < length; i++) {
          setValue(wasmArray, wasmArrayOffset + i, jsArray[jsArrayOffset + i]);
        }
      },
      qE: x0 => x0.scrollTop,
      qF: () => 1000 * performance.now(),
      qG: x0 => x0.state,
      qH: x0 => x0.readText(),
      qI: (x0,x1,x2) => globalThis.crypto.subtle.generateKey(x0,x1,x2),
      qJ: (x0,x1) => { x0.height = x1 },
      qK: (x0,x1) => { x0.onmessage = x1 },
      qL: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      qM: x0 => x0.projectId,
      r: (o, p) => o[p],
      rB: o => {
        if (o === null || o === undefined) return 0;
        if (o instanceof Int32Array) return 1;
        return 2;
      },
      rC: (x0,x1) => { x0.id = x1 },
      rD: (jsArray, jsArrayOffset, wasmArray, wasmArrayOffset, length) => {
        const setValue = dartInstance.exports.$wasmI32ArraySet;
        for (let i = 0; i < length; i++) {
          setValue(wasmArray, wasmArrayOffset + i, jsArray[jsArrayOffset + i]);
        }
      },
      rE: x0 => x0.offsetTop,
      rF: (x0,x1) => x0.requestAnimationFrame(x1),
      rG: x0 => x0.hash,
      rH: x0 => x0.clipboard,
      rI: x0 => ({name: x0}),
      rJ: (x0,x1) => { x0.width = x1 },
      rK: x0 => x0.payload,
      rL: (x0,x1,x2) => x0.setItem(x1,x2),
      rM: x0 => x0.apiKey,
      s: () => globalThis,
      sB: o => o instanceof Uint16Array,
      sC: (x0,x1) => { x0.nonce = x1 },
      sD: x0 => x0.visibilityState,
      sE: x0 => x0.scrollLeft,
      sF: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      sG: x0 => x0.state,
      sH: (x0,x1) => x0.writeText(x1),
      sI: () => new AbortController(),
      sJ: x0 => x0.style,
      sK: x0 => x0.reply,
      sL: x0 => x0.localStorage,
      sM: x0 => x0.options,
      t: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      tB: Function.prototype.call.bind(DataView.prototype.getUint16),
      tC: x0 => x0.nonce,
      tD: (x0,x1,x2) => x0.removeEventListener(x1,x2),
      tE: x0 => x0.offsetLeft,
      tF: x0 => x0.now(),
      tG: (x0,x1) => x0.go(x1),
      tH: x0 => x0.unlock(),
      tI: (x0,x1,x2,x3,x4,x5) => ({method: x0,headers: x1,body: x2,credentials: x3,redirect: x4,signal: x5}),
      tJ: (x0,x1) => { x0.src = x1 },
      tK: x0 => x0.id,
      tL: (x0,x1) => x0.getItem(x1),
      tM: x0 => x0.name,
      u: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      uB: o => o instanceof Int16Array,
      uC: () => globalThis.window.flutterConfiguration,
      uD: x0 => x0.disconnect(),
      uE: x0 => x0.offsetParent,
      uF: x0 => x0.performance,
      uG: x0 => x0.parentElement,
      uH: (x0,x1) => x0.lock(x1),
      uI: (x0,x1) => globalThis.fetch(x0,x1),
      uJ: () => globalThis.document,
      uK: x0 => x0.action,
      uL: (x0,x1) => x0.key(x1),
      uM: () => globalThis.firebase_core.getApps(),
      v: (x0,x1) => ({addView: x0,removeView: x1}),
      vB: Function.prototype.call.bind(DataView.prototype.getInt16),
      vC: (x0,x1) => x0.attachShadow(x1),
      vD: x0 => new Intl.Locale(x0),
      vE: (o, p, r) => o.replace(p, () => r),
      vF: x0 => new Uint8Array(x0),
      vG: (x0,x1) => x0.querySelectorAll(x1),
      vH: x0 => x0.orientation,
      vI: (x0,x1) => x0.get(x1),
      vJ: x0 => x0.src,
      vK: x0 => x0.serviceWorker,
      vL: x0 => x0.length,
      vM: x0 => x0.name,
      w: (l, r) => l === r,
      wB: o => o instanceof Uint8ClampedArray,
      wC: (x0,x1) => x0.createElement(x1),
      wD: x0 => x0.region,
      wE: (o, p, r) => o.replaceAll(p, () => r),
      wF: (x0,x1,x2) => x0.slice(x1,x2),
      wG: (d, digits) => d.toFixed(digits),
      wH: (x0,x1) => x0.querySelector(x1),
      wI: (wasmFunction,f) => finalizeWrapper(f, function(x0,x1,x2) { return wasmFunction(f,arguments.length,x0,x1,x2) }),
      wJ: x0 => x0.decode(),
      wK: x0 => x0.navigator,
      wL: (x0,x1) => x0.removeItem(x1),
      wM: () => globalThis.firebase_core.SDK_VERSION,
      x: x0 => x0.random(),
      xB: o => {
        if (o === null || o === undefined) return 0;
        if (o instanceof Uint8Array) return 1;
        return 2;
      },
      xC: x0 => x0.scale,
      xD: x0 => x0.script,
      xE: x0 => x0.deltaMode,
      xF: (x0,x1) => x0.decode(x1),
      xG: x0 => x0.maxHeight,
      xH: (x0,x1) => { x0.title = x1 },
      xI: (x0,x1) => x0.forEach(x1),
      xJ: (x0,x1,x2,x3) => x0.open(x1,x2,x3),
      xK: () => globalThis.window,
      xL: (x0,x1) => x0.querySelector(x1),
      xM: (x0,x1,x2) => globalThis.firebase_core.registerVersion(x0,x1,x2),
      y: () => globalThis.Math,
      yB: Function.prototype.call.bind(DataView.prototype.setInt32),
      yC: x0 => x0.visualViewport,
      yD: x0 => x0.language,
      yE: x0 => x0.deltaY,
      yF: (x0,x1) => x0.adoptText(x1),
      yG: x0 => x0.maxWidth,
      yH: (x0,x1) => x0.vibrate(x1),
      yI: x0 => x0.name,
      yJ: (wasmFunction,f) => finalizeWrapper(f, function(x0) { return wasmFunction(f,arguments.length,x0) }),
      yK: (x0,x1) => x0.transferFromImageBitmap(x1),
      yL: (x0,x1) => x0.append(x1),
      yM: (x0,x1) => x0.debug(x1),
      z: (x0,x1) => x0.prepend(x1),
      zB: Function.prototype.call.bind(DataView.prototype.setUint32),
      zC: x0 => x0.devicePixelRatio,
      zD: x0 => x0.languages,
      zE: x0 => x0.deltaX,
      zF: x0 => x0.first(),
      zG: x0 => x0.minHeight,
      zH: x0 => x0.content,
      zI: x0 => x0.statusText,
      zJ: (x0,x1,x2) => x0.addEventListener(x1,x2),
      zK: (x0,x1) => x0.getContext(x1),
      zL: x0 => x0.body,
      zM: (wasmFunction,f) => finalizeWrapper(f, function(x0,x1) { return wasmFunction(f,arguments.length,x0,x1) }),

    };

    const baseImports = {
      _: dart2wasm,
      Math: Math,
      Date: Date,
      Object: Object,
      Array: Array,
      Reflect: Reflect,
      WebAssembly: {
        JSTag: WebAssembly.JSTag,
      },
      "": new Proxy({}, { get(_, prop) { return prop; } }),

    };

    const jsStringPolyfill = {
      "charCodeAt": (s, i) => s.charCodeAt(i),
      "compare": (s1, s2) => {
        if (s1 < s2) return -1;
        if (s1 > s2) return 1;
        return 0;
      },
      "concat": (s1, s2) => s1 + s2,
      "equals": (s1, s2) => s1 === s2,
      "fromCharCode": (i) => String.fromCharCode(i),
      "length": (s) => s.length,
      "substring": (s, a, b) => s.substring(a, b),
      "fromCharCodeArray": (a, start, end) => {
        if (end <= start) return '';

        const read = dartInstance.exports.$wasmI16ArrayGet;
        let result = '';
        let index = start;
        const chunkLength = Math.min(end - index, 500);
        let array = new Array(chunkLength);
        while (index < end) {
          const newChunkLength = Math.min(end - index, 500);
          for (let i = 0; i < newChunkLength; i++) {
            array[i] = read(a, index++);
          }
          if (newChunkLength < chunkLength) {
            array = array.slice(0, newChunkLength);
          }
          result += String.fromCharCode(...array);
        }
        return result;
      },
      "intoCharCodeArray": (s, a, start) => {
        if (s === '') return 0;

        const write = dartInstance.exports.$wasmI16ArraySet;
        for (var i = 0; i < s.length; ++i) {
          write(a, start++, s.charCodeAt(i));
        }
        return s.length;
      },
      "test": (s) => typeof s == "string",
    };


    

    dartInstance = await WebAssembly.instantiate(this.module, {
      ...baseImports,
      ...additionalImports,
      
      "wasm:js-string": jsStringPolyfill,
    });

    return new InstantiatedApp(this, dartInstance);
  }
}

class InstantiatedApp {
  constructor(compiledApp, instantiatedModule) {
    this.compiledApp = compiledApp;
    this.instantiatedModule = instantiatedModule;
  }

  // Call the main function with the given arguments.
  invokeMain(...args) {
    this.instantiatedModule.exports.$invokeMain(args);
  }
}
