let remaining = 80;
let paused = false;

function format(seconds) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? m + 'm ' + String(s).padStart(2, '0') + 's' : s + 's';
}

function paint() {
  const countdown = document.querySelector('[data-countdown]');
  if (countdown) countdown.textContent = paused ? 'Paused' : format(remaining);
  const status = document.querySelector('[data-watch-status]');
  if (status) status.textContent = paused ? 'Paused' : 'Watching';
  document.querySelectorAll('[data-watch-btn]').forEach(function (el) {
    el.textContent = paused ? 'Resume' : 'Pause';
  });
  document.querySelectorAll('[data-action="toggle-watch"]').forEach(function (el) {
    el.setAttribute('aria-label', paused ? 'Resume watcher' : 'Pause watcher');
    el.innerHTML = '<span class="i" data-icon="' + (paused ? 'play' : 'pause') + '"></span>';
    if (window.renderIcons) window.renderIcons(el);
  });
}

window.addEventListener('upcroc:watch', function () {
  paused = !paused;
  paint();
  showToast(paused ? 'Watcher paused' : 'Watcher resumed');
});

window.addEventListener('upcroc:check', function () {
  remaining = 80;
  paused = false;
  paint();
});

setInterval(function () {
  if (!paused) {
    remaining = remaining <= 1 ? 80 : remaining - 1;
    paint();
  }
}, 1000);

paint();
