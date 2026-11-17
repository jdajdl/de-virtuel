"use strict";

const PIPS = [[4], [0, 8], [0, 4, 8], [0, 2, 6, 8], [0, 2, 4, 6, 8], [0, 2, 3, 5, 6, 8]];
const MAX_HISTORY = 10;
const ROLL_MS = 900;

const diceEl = document.getElementById("dice");
const totalEl = document.getElementById("total");
const historyEl = document.getElementById("history");
const rollBtn = document.getElementById("roll");
const countBtns = [...document.querySelectorAll("#counts button")];
const installBtn = document.getElementById("install");

const HINT = "Touchez le dé ou secouez le téléphone";
const SHAKE_PROMPT = "Touchez l'écran une fois pour activer la secousse";
let count = 1;
let rolling = false;
const history = [];

// Tirage uniforme 1..6 (rejet pour éviter le biais du modulo).
function rand6() {
  const a = new Uint8Array(1);
  do { crypto.getRandomValues(a); } while (a[0] >= 252);
  return (a[0] % 6) + 1;
}

function dieEl() {
  const d = document.createElement("div");
  d.className = "die";
  for (let i = 0; i < 9; i++) d.appendChild(document.createElement("i"));
  return d;
}

function setFace(d, v) {
  const on = PIPS[v - 1];
  [...d.children].forEach((p, i) => p.classList.toggle("on", on.includes(i)));
  d.dataset.v = v;
}

function build() {
  diceEl.replaceChildren();
  diceEl.style.setProperty("--k", count);
  for (let i = 0; i < count; i++) {
    const d = dieEl();
    d.style.setProperty("--k", count);
    setFace(d, rand6());
    diceEl.appendChild(d);
  }
  diceEl.setAttribute("aria-label", count === 1 ? "Un dé" : count + " dés");
}

function vibrate() {
  if (navigator.vibrate) navigator.vibrate(40);
}

function roll() {
  if (rolling) return;
  rolling = true;
  vibrate();
  totalEl.textContent = "…";

  const dice = [...diceEl.children];
  const finals = dice.map(rand6);
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

  const ticker = setInterval(() => dice.forEach(d => setFace(d, rand6())), 75);

  dice.forEach(d => {
    const turns = (2 + Math.floor(Math.random() * 2)) * (Math.random() < 0.5 ? -1 : 1) * 360;
    d.animate(
      [
        { transform: "rotate(0deg) scale(1)" },
        { transform: `rotate(${turns / 2}deg) scale(1.18)`, offset: 0.5 },
        { transform: `rotate(${turns}deg) scale(1)` },
      ],
      { duration: reduce ? 200 : ROLL_MS, easing: "cubic-bezier(.2,.7,.3,1)" }
    );
  });

  setTimeout(() => {
    clearInterval(ticker);
    dice.forEach((d, i) => setFace(d, finals[i]));
    const total = finals.reduce((a, b) => a + b, 0);
    totalEl.textContent = finals.length === 1
      ? `Résultat : ${total}`
      : `Total : ${total}  (${finals.join(" + ")})`;
    history.unshift(total);
    history.length = Math.min(history.length, MAX_HISTORY);
    historyEl.textContent = "Derniers lancers : " + history.join(" · ");
    rolling = false;
  }, reduce ? 200 : ROLL_MS);
}

diceEl.addEventListener("click", roll);
rollBtn.addEventListener("click", roll);

countBtns.forEach(b => b.addEventListener("click", () => {
  if (rolling) return;
  count = Number(b.dataset.n);
  countBtns.forEach(x => x.classList.toggle("sel", x === b));
  totalEl.textContent = HINT;
  build();
}));

// --- Secouer le téléphone ---
let shakeOn = false;
let lastShake = 0;

const needsPermission =
  "DeviceMotionEvent" in window && typeof DeviceMotionEvent.requestPermission === "function";
let lastMag = 0;

function onMotion(e) {
  // Avec gravité si disponible, sinon accélération seule (capteurs sans gravité).
  const a = e.accelerationIncludingGravity || e.acceleration;
  if (!a) return;
  const mag = Math.hypot(a.x || 0, a.y || 0, a.z || 0);
  // Variation brusque de l'accélération (m/s²) : plus fiable qu'un seuil absolu.
  const jerk = Math.abs(mag - lastMag);
  lastMag = mag;
  const now = Date.now();
  if ((jerk > 12 || mag > 20) && now - lastShake > 1000) {
    lastShake = now;
    roll();
  }
}

async function enableShake() {
  if (shakeOn || !("DeviceMotionEvent" in window)) return;
  try {
    // iOS exige une autorisation explicite, déclenchée par un geste de l'utilisateur.
    if (needsPermission) {
      const r = await DeviceMotionEvent.requestPermission();
      if (r !== "granted") return;
    }
    window.addEventListener("devicemotion", onMotion);
    shakeOn = true;
    if (totalEl.textContent === SHAKE_PROMPT) totalEl.textContent = HINT;
  } catch (_) { /* capteur indisponible : le toucher reste possible */ }
}

if ("DeviceMotionEvent" in window) {
  if (needsPermission) {
    // iPhone : la secousse ne marche qu'après un premier toucher (autorisation).
    totalEl.textContent = SHAKE_PROMPT;
    document.addEventListener("click", enableShake);
  } else {
    // Android : aucune autorisation nécessaire.
    enableShake();
  }
}

// --- Installation (PWA) ---
let deferredPrompt = null;
window.addEventListener("beforeinstallprompt", e => {
  e.preventDefault();
  deferredPrompt = e;
  installBtn.hidden = false;
});
installBtn.addEventListener("click", async () => {
  if (!deferredPrompt) return;
  deferredPrompt.prompt();
  await deferredPrompt.userChoice;
  deferredPrompt = null;
  installBtn.hidden = true;
});
window.addEventListener("appinstalled", () => { installBtn.hidden = true; });

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}

build();
